package com.underwaterai.enhance.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Process
import com.underwaterai.enhance.utils.AppLogger
import com.underwaterai.enhance.utils.HardwareProfiler
import com.underwaterai.enhance.utils.PerformanceLogger
import com.underwaterai.enhance.utils.PerformanceMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream

class ImageEnhancer(private val context: Context) {

    companion object {
        private const val TAG = "UnderwaterAI.Enhancer"
        private const val DIM_ALIGNMENT = 4 // dims must be divisible by this
    }

    private var loadedModule: Module? = null
    private var loadedModelType: ModelType? = null
    private val perfLogger = PerformanceLogger()
    private var vulkanActive = false

    /**
     * Configure PyTorch to use all CPU cores at maximum performance.
     * The full pytorch_android (non-lite) exposes setNumThreads.
     */
    private fun configureThreads() {
        val numCores = Runtime.getRuntime().availableProcessors()
        try {
            org.pytorch.PyTorchAndroid.setNumThreads(numCores)
            AppLogger.i(TAG, "PyTorch threads set to $numCores")
        } catch (e: Throwable) {
            AppLogger.w(TAG, "setNumThreads unavailable (${e.javaClass.simpleName}), using defaults")
        }
    }

    @Synchronized
    private fun ensureModel(modelType: ModelType): Module {
        if (loadedModelType == modelType && loadedModule != null) {
            AppLogger.d(TAG, "${modelType.displayName} already loaded, reusing")
            return loadedModule!!
        }

        // Release previous model
        try { loadedModule?.destroy() } catch (_: Throwable) {}
        loadedModule = null
        loadedModelType = null
        vulkanActive = false

        // Free memory before loading a new model
        System.gc()

        perfLogger.startPhase("model_load")

        val modelPath = assetFilePath(modelType.fileName)
        AppLogger.i(TAG, "Loading model from: $modelPath")

        configureThreads()

        // Load with Module.load() (full TorchScript).
        // Note: Models are CPU-optimized (Vulkan prepacking requires a Vulkan-capable
        // PyTorch build at conversion time). The full pytorch_android runtime provides
        // setNumThreads + optimized CPU kernels for maximum throughput.
        val module: Module
        try {
            module = Module.load(modelPath)
            vulkanActive = false
            AppLogger.i(TAG, "Model loaded (CPU-optimized, ${Runtime.getRuntime().availableProcessors()} cores active)")
        } catch (e: Throwable) {
            AppLogger.e(TAG, "Module.load failed (${e.javaClass.simpleName}), this should not happen", e)
            throw RuntimeException("Failed to load model ${modelType.fileName}: ${e.message}", e)
        }

        perfLogger.endPhase("model_load")
        AppLogger.i(TAG, "${modelType.displayName} loaded in ${perfLogger.getPhase("model_load")}ms")

        loadedModule = module
        loadedModelType = modelType
        return module
    }

    suspend fun enhance(
        inputBitmap: Bitmap,
        modelType: ModelType
    ): Pair<Bitmap, PerformanceMetrics> = withContext(Dispatchers.Default) {
        // Boost thread priority for inference – use THREAD_PRIORITY_URGENT_DISPLAY
        // which is the highest non-real-time priority on Android.
        val prevPriority = Process.getThreadPriority(Process.myTid())
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)

        try {
            runEnhancement(inputBitmap, modelType)
        } finally {
            // Restore original thread priority
            try { Process.setThreadPriority(prevPriority) } catch (_: Throwable) {}
        }
    }

    private suspend fun runEnhancement(
        inputBitmap: Bitmap,
        modelType: ModelType
    ): Pair<Bitmap, PerformanceMetrics> {
        perfLogger.reset()
        perfLogger.logSystemInfo()
        val numCores = Runtime.getRuntime().availableProcessors()

        // Take hardware snapshot before processing
        val hwBefore = HardwareProfiler.snapshot(context)
        val heapBeforeMb = perfLogger.getMemoryUsageMb()

        AppLogger.i(TAG, "Enhancement started: ${modelType.displayName}")
        AppLogger.i(TAG, "Original bitmap: ${inputBitmap.width}x${inputBitmap.height}")
        AppLogger.i(TAG, "GPU: ${hwBefore.gpuInfo.renderer} (${hwBefore.gpuInfo.vendor})")
        hwBefore.cpuCoreFrequencies.forEach { core ->
            AppLogger.d(TAG, "CPU Core ${core.coreIndex}: ${core.currentFreqMhz}MHz / ${core.maxFreqMhz}MHz")
        }

        // Phase 1: load model
        val module = ensureModel(modelType)

        // Phase 2: preprocess
        perfLogger.startPhase("preprocess")
        val maxSide = modelType.maxInputSide
        AppLogger.i(TAG, "Max input side for ${modelType.displayName}: $maxSide (output up to ${maxSide * modelType.scaleFactor}px)")
        val scaled = prepareInput(inputBitmap, maxSide)
        val inputW = scaled.width
        val inputH = scaled.height
        AppLogger.i(TAG, "Prepared input: ${inputW}x${inputH}")

        val inputTensor = bitmapToTensor(scaled)
        AppLogger.d(TAG, "Input tensor shape: [1, 3, $inputH, $inputW]")
        perfLogger.endPhase("preprocess")

        // Phase 3: inference
        perfLogger.startPhase("inference")
        val backend = if (vulkanActive) "Vulkan GPU + CPU" else "CPU"
        AppLogger.i(TAG, "Running inference on ${modelType.displayName} ($backend, $numCores threads)...")

        val outputTensor: Tensor
        try {
            val result = module.forward(IValue.from(inputTensor))
            outputTensor = when {
                result.isTensor -> result.toTensor()
                result.isTuple -> {
                    val elements = result.toTuple()
                    AppLogger.w(TAG, "Model returned tuple with ${elements.size} elements, using first")
                    elements[0].toTensor()
                }
                else -> throw RuntimeException("Unexpected model output type")
            }
        } catch (e: Throwable) {
            AppLogger.e(TAG, "Inference FAILED", e)
            throw RuntimeException("Model inference failed: ${e.message}", e)
        }
        perfLogger.endPhase("inference")

        val shape = outputTensor.shape()
        AppLogger.i(TAG, "Output tensor shape: [${shape.joinToString(", ")}]")

        // Phase 4: postprocess
        perfLogger.startPhase("postprocess")
        val outputBitmap = tensorToBitmap(outputTensor)
        perfLogger.endPhase("postprocess")

        AppLogger.i(TAG, "Output bitmap: ${outputBitmap.width}x${outputBitmap.height}")

        // Take snapshot after processing
        val hwAfter = HardwareProfiler.snapshot(context)
        val heapAfterMb = perfLogger.getMemoryUsageMb()

        val totalTime = perfLogger.getPhase("model_load") +
                perfLogger.getPhase("preprocess") +
                perfLogger.getPhase("inference") +
                perfLogger.getPhase("postprocess")

        val metrics = PerformanceMetrics(
            modelName = modelType.displayName,
            modelLoadTimeMs = perfLogger.getPhase("model_load"),
            preprocessTimeMs = perfLogger.getPhase("preprocess"),
            inferenceTimeMs = perfLogger.getPhase("inference"),
            postprocessTimeMs = perfLogger.getPhase("postprocess"),
            totalTimeMs = totalTime,
            inputWidth = inputW,
            inputHeight = inputH,
            outputWidth = outputBitmap.width,
            outputHeight = outputBitmap.height,
            threadsUsed = numCores,
            memoryUsedMb = heapAfterMb,
            cpuArch = hwBefore.cpuArch,
            cpuCoreCount = hwBefore.cpuCores,
            cpuCoreFrequencies = hwAfter.cpuCoreFrequencies,
            gpuRenderer = hwBefore.gpuInfo.renderer,
            gpuVendor = hwBefore.gpuInfo.vendor,
            gpuGlVersion = hwBefore.gpuInfo.glVersion,
            gpuAvailable = hwBefore.gpuInfo.available,
            gpuUsedForInference = vulkanActive,
            totalRamMb = hwBefore.totalRamMb,
            availableRamMb = hwAfter.availableRamMb,
            heapMaxMb = hwBefore.heapMaxMb,
            heapUsedBeforeMb = heapBeforeMb,
            heapUsedAfterMb = heapAfterMb,
            deviceModel = hwBefore.deviceModel,
            androidApi = hwBefore.androidApi
        )

        AppLogger.logMetrics(metrics)
        AppLogger.flush()

        if (scaled !== inputBitmap) {
            scaled.recycle()
        }

        return Pair(outputBitmap, metrics)
    }

    /**
     * Scale image so its longest side fits [maxInputSide], then align both
     * dimensions to DIM_ALIGNMENT.  If the image already fits, it is returned
     * as-is (no scaling = no quality loss for smaller / high-quality images).
     */
    private fun prepareInput(bitmap: Bitmap, maxInputSide: Int): Bitmap {
        var w = bitmap.width
        var h = bitmap.height

        // Only downscale if the image exceeds the model's limit
        val longestSide = maxOf(w, h)
        if (longestSide > maxInputSide) {
            val scale = maxInputSide.toFloat() / longestSide
            w = (w * scale).toInt()
            h = (h * scale).toInt()
        }

        // Align dimensions to DIM_ALIGNMENT (round down, minimum DIM_ALIGNMENT)
        w = (w / DIM_ALIGNMENT) * DIM_ALIGNMENT
        h = (h / DIM_ALIGNMENT) * DIM_ALIGNMENT
        w = w.coerceAtLeast(DIM_ALIGNMENT)
        h = h.coerceAtLeast(DIM_ALIGNMENT)

        if (w == bitmap.width && h == bitmap.height) return bitmap

        AppLogger.i(TAG, "Scaling input: ${bitmap.width}x${bitmap.height} -> ${w}x${h}")
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun bitmapToTensor(bitmap: Bitmap): Tensor {
        val width = bitmap.width
        val height = bitmap.height
        val pixelCount = width * height
        val pixels = IntArray(pixelCount)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Build planar [1, 3, H, W] float array directly
        val floats = FloatArray(3 * pixelCount)
        for (i in 0 until pixelCount) {
            val px = pixels[i]
            floats[i] = Color.red(px) / 255.0f                     // R plane
            floats[pixelCount + i] = Color.green(px) / 255.0f      // G plane
            floats[2 * pixelCount + i] = Color.blue(px) / 255.0f   // B plane
        }

        return Tensor.fromBlob(floats, longArrayOf(1, 3, height.toLong(), width.toLong()))
    }

    private fun tensorToBitmap(tensor: Tensor): Bitmap {
        val shape = tensor.shape()
        // Handle both [1, 3, H, W] and [3, H, W]
        val channels: Int
        val height: Int
        val width: Int
        if (shape.size == 4) {
            channels = shape[1].toInt()
            height = shape[2].toInt()
            width = shape[3].toInt()
        } else if (shape.size == 3) {
            channels = shape[0].toInt()
            height = shape[1].toInt()
            width = shape[2].toInt()
        } else {
            throw RuntimeException("Unexpected output tensor rank: ${shape.size}, shape: [${shape.joinToString()}]")
        }

        val data = tensor.dataAsFloatArray
        val planeSize = height * width
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(planeSize)

        for (i in 0 until planeSize) {
            val r = (data[i].coerceIn(0f, 1f) * 255).toInt()
            val g = (data[planeSize + i].coerceIn(0f, 1f) * 255).toInt()
            val b = if (channels >= 3) {
                (data[2 * planeSize + i].coerceIn(0f, 1f) * 255).toInt()
            } else 0
            pixels[i] = Color.argb(255, r, g, b)
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun assetFilePath(assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            AppLogger.d(TAG, "Asset already extracted: $assetName (${file.length()} bytes)")
            return file.absolutePath
        }

        // Clean up stale files from previous model versions
        val staleNames = listOf(
            "test_model_general_4x.ptl", "test_model_purephoto_4x.ptl",
            "model_purephoto_realplksr_4x.ptl", "model_nomos2_hq_mosr_4x.ptl",
            "model_faceupsharp_dat_4x.ptl",
            "test_model_general_4x.pt", "test_model_purephoto_4x.pt",
            "model_purephoto_realplksr_4x.pt", "model_nomos2_hq_mosr_4x.pt",
            "model_faceupsharp_dat_4x.pt"
        )
        for (staleName in staleNames) {
            val staleFile = File(context.filesDir, staleName)
            if (staleFile.exists()) {
                staleFile.delete()
                AppLogger.i(TAG, "Deleted stale asset: $staleName")
            }
        }

        AppLogger.i(TAG, "Extracting asset: $assetName")
        try {
            context.assets.open(assetName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    val buffer = ByteArray(65536) // 64KB buffer for faster extraction
                    var bytesRead: Int
                    var totalBytes = 0L
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                    outputStream.flush()
                    AppLogger.i(TAG, "Extracted $assetName: $totalBytes bytes")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to extract asset: $assetName", e)
            file.delete()
            throw e
        }
        return file.absolutePath
    }

    @Synchronized
    fun release() {
        try { loadedModule?.destroy() } catch (_: Throwable) {}
        loadedModule = null
        loadedModelType = null
        vulkanActive = false
        AppLogger.i(TAG, "Enhancer released")
    }
}
