package com.underwaterai.enhance.model

import android.content.Context
import android.graphics.Bitmap
import android.os.PowerManager
import android.os.Process
import com.underwaterai.enhance.utils.AppLogger
import com.underwaterai.enhance.utils.DeviceTier
import com.underwaterai.enhance.utils.HardwareProfiler
import com.underwaterai.enhance.utils.PerformanceLogger
import com.underwaterai.enhance.utils.PerformanceMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

class ImageEnhancer(private val context: Context) {

    companion object {
        private const val TAG = "UnderwaterAI.Enhancer"
        private const val DIM_ALIGNMENT = 4 // dims must be divisible by this
        private const val DEFAULT_TILE_SIZE = 512  // max tile size per side (before upscale)
        private const val TILE_OVERLAP = 32  // overlap between adjacent tiles for seamless blending
        private const val WAKELOCK_TAG = "UnderwaterAI::Inference"
    }

    private var loadedModule: Module? = null
    private var loadedModelType: ModelType? = null
    private val perfLogger = PerformanceLogger()
    private var vulkanActive = false
    private var deviceTier: DeviceTier? = null
    private var activeTileSize: Int = DEFAULT_TILE_SIZE

    /**
     * Classify device on first call and cache the result.
     */
    private fun ensureDeviceTier(): DeviceTier {
        deviceTier?.let { return it }
        val tier = HardwareProfiler.classifyDevice(context)
        deviceTier = tier
        activeTileSize = HardwareProfiler.recommendedTileSize(tier)
        AppLogger.i(TAG, "Device tier: $tier, adaptive tile size: ${activeTileSize}px")
        return tier
    }

    /**
     * Configure PyTorch to use only the performance (big) CPU cores.
     *
     * On ARM big.LITTLE SoCs the efficiency cores have ~50 % lower
     * max frequency and significantly weaker IPC, dragging down overall
     * throughput when PyTorch spreads work across all cores.
     * Limiting to big cores keeps every thread on the fast cluster.
     */
    private fun configureThreads() {
        val snap = HardwareProfiler.snapshot(context)
        val bigCores = HardwareProfiler.countPerformanceCores(snap.cpuCoreFrequencies)
        // Fall back to all cores if detection returned 0 somehow
        val threadCount = if (bigCores > 0) bigCores else Runtime.getRuntime().availableProcessors()
        try {
            org.pytorch.PyTorchAndroid.setNumThreads(threadCount)
            AppLogger.i(TAG, "PyTorch threads set to $threadCount performance cores (${snap.cpuCoreFrequencies.size} total)")
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
        modelType: ModelType,
        targetScale: Int = modelType.scaleFactor
    ): Pair<Bitmap, PerformanceMetrics> = withContext(Dispatchers.Default) {
        ensureDeviceTier()

        // Boost thread priority for inference – use THREAD_PRIORITY_URGENT_DISPLAY
        // which is the highest non-real-time priority on Android.
        val prevPriority = Process.getThreadPriority(Process.myTid())
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)

        // Acquire a partial wake-lock to prevent the CPU governor from
        // down-clocking cores while inference is running.  Without this,
        // Android will happily reduce core frequencies to 50 % after a
        // few seconds of sustained load to save power — exactly what the
        // user reported.
        // Acquire a partial wake-lock if the permission is available.
        // Wrap in try-catch so the app still works even without the permission.
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock: PowerManager.WakeLock? = try {
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).also {
                it.acquire()  // Hold wakelock indefinitely until manual release
            }
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "WAKE_LOCK permission not granted — CPU may throttle during inference")
            null
        }

        try {
            runEnhancement(inputBitmap, modelType, targetScale)
        } finally {
            // Restore original thread priority
            try { Process.setThreadPriority(prevPriority) } catch (_: Throwable) {}
            try { if (wakeLock?.isHeld == true) wakeLock.release() } catch (_: Throwable) {}
        }
    }

    private suspend fun runEnhancement(
        inputBitmap: Bitmap,
        modelType: ModelType,
        targetScale: Int
    ): Pair<Bitmap, PerformanceMetrics> {
        perfLogger.reset()
        perfLogger.logSystemInfo()

        // Take hardware snapshot before processing
        val hwBefore = HardwareProfiler.snapshot(context)
        val heapBeforeMb = perfLogger.getMemoryUsageMb()

        val tier = ensureDeviceTier()
        AppLogger.i(TAG, "Enhancement started: ${modelType.displayName} (tier=$tier, tileSize=$activeTileSize)")
        AppLogger.i(TAG, "Original bitmap: ${inputBitmap.width}x${inputBitmap.height}")
        AppLogger.i(TAG, "Target scale: ${targetScale}x (model native: ${modelType.scaleFactor}x)")
        AppLogger.i(TAG, "GPU: ${hwBefore.gpuInfo.renderer} (${hwBefore.gpuInfo.vendor})")
        hwBefore.cpuCoreFrequencies.forEach { core ->
            AppLogger.d(TAG, "CPU Core ${core.coreIndex}: ${core.currentFreqMhz}MHz / ${core.maxFreqMhz}MHz")
        }

        // Phase 1: load model
        val module = ensureModel(modelType)

        // Phase 2: preprocess — prepare input (align dimensions) but do NOT downscale
        perfLogger.startPhase("preprocess")
        val alignedInput = alignDimensions(inputBitmap)
        val inputW = alignedInput.width
        val inputH = alignedInput.height
        AppLogger.i(TAG, "Aligned input: ${inputW}x${inputH}")
        perfLogger.endPhase("preprocess")

        // Phase 3: inference — tile-based processing for any size image
        perfLogger.startPhase("inference")
        val nativeScale = modelType.scaleFactor
        val backend = if (vulkanActive) "Vulkan GPU + CPU" else "CPU"
        val snap = HardwareProfiler.snapshot(context)
        val bigCores = HardwareProfiler.countPerformanceCores(snap.cpuCoreFrequencies)

        val fullOutputBitmap: Bitmap
        if (inputW <= activeTileSize && inputH <= activeTileSize) {
            // Small image — process in single pass (no tiling needed)
            AppLogger.i(TAG, "Single-pass inference ($backend, $bigCores perf-threads)...")
            val inputTensor = bitmapToTensor(alignedInput)
            val outputTensor = runModelForward(module, inputTensor)
            fullOutputBitmap = tensorToBitmap(outputTensor)
            AppLogger.i(TAG, "Output: ${fullOutputBitmap.width}x${fullOutputBitmap.height}")
        } else {
            // Large image — tile-based processing with overlap blending
            AppLogger.i(TAG, "Tile-based inference ($backend, $bigCores perf-threads, tile=$activeTileSize)...")
            fullOutputBitmap = processWithTiles(alignedInput, module, nativeScale)
        }
        perfLogger.endPhase("inference")

        // Phase 4: postprocess — scale output to target if needed
        perfLogger.startPhase("postprocess")
        val finalBitmap = if (targetScale != nativeScale) {
            val finalW = inputW * targetScale
            val finalH = inputH * targetScale
            AppLogger.i(TAG, "Rescaling output: ${fullOutputBitmap.width}x${fullOutputBitmap.height} -> ${finalW}x${finalH} (target ${targetScale}x)")
            val scaled = Bitmap.createScaledBitmap(fullOutputBitmap, finalW, finalH, true)
            fullOutputBitmap.recycle()
            scaled
        } else {
            fullOutputBitmap
        }
        perfLogger.endPhase("postprocess")

        AppLogger.i(TAG, "Final output bitmap: ${finalBitmap.width}x${finalBitmap.height}")

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
            outputWidth = finalBitmap.width,
            outputHeight = finalBitmap.height,
            threadsUsed = HardwareProfiler.countPerformanceCores(hwBefore.cpuCoreFrequencies),
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

        if (alignedInput !== inputBitmap) {
            alignedInput.recycle()
        }

        return Pair(finalBitmap, metrics)
    }

    /**
     * Run model forward pass, extracting the output tensor from the result.
     */
    private fun runModelForward(module: Module, inputTensor: Tensor): Tensor {
        val result = module.forward(IValue.from(inputTensor))
        return when {
            result.isTensor -> result.toTensor()
            result.isTuple -> {
                val elements = result.toTuple()
                AppLogger.w(TAG, "Model returned tuple with ${elements.size} elements, using first")
                elements[0].toTensor()
            }
            else -> throw RuntimeException("Unexpected model output type")
        }
    }

    /**
     * Process a large image by splitting it into overlapping tiles,
     * running each tile through the model, and blending the overlapping
     * regions with linear interpolation for seamless output.
     *
     * Supports coroutine cancellation (checked between tiles) and
     * per-tile timeout to protect low-end devices.
     */
    private suspend fun processWithTiles(input: Bitmap, module: Module, scaleFactor: Int): Bitmap {
        val inputW = input.width
        val inputH = input.height
        val outputW = inputW * scaleFactor
        val outputH = inputH * scaleFactor
        val overlap = TILE_OVERLAP
        val scaledOverlap = overlap * scaleFactor
        val tileSize = activeTileSize
        val tileStep = tileSize - overlap  // stride between tile origins

        // Calculate tile grid
        val tilesX = maxOf(1, (inputW - overlap + tileStep - 1) / tileStep)
        val tilesY = maxOf(1, (inputH - overlap + tileStep - 1) / tileStep)
        val totalTiles = tilesX * tilesY
        AppLogger.i(TAG, "Tiling: ${tilesX}x${tilesY} = $totalTiles tiles (tile=${tileSize}px, overlap=${overlap}px, step=${tileStep}px)")

        // Accumulator arrays for blending: weighted sum of colors + total weight
        // OOM-safe: catch allocation failure and retry with smaller tile
        val accR: FloatArray
        val accG: FloatArray
        val accB: FloatArray
        val accW: FloatArray
        try {
            accR = FloatArray(outputW * outputH)
            accG = FloatArray(outputW * outputH)
            accB = FloatArray(outputW * outputH)
            accW = FloatArray(outputW * outputH)
        } catch (e: OutOfMemoryError) {
            System.gc()
            AppLogger.e(TAG, "OOM allocating ${outputW}x${outputH} accumulators", e)
            throw RuntimeException(
                "Image too large for available memory. " +
                "Try a smaller image or lighter model.", e
            )
        }

        ensureDeviceTier()

        var tileIndex = 0
        for (ty in 0 until tilesY) {
            for (tx in 0 until tilesX) {
                // Check for coroutine cancellation between tiles
                coroutineContext.ensureActive()

                tileIndex++

                // Calculate tile position in input space
                var tileX = tx * tileStep
                var tileY = ty * tileStep
                var tileW = minOf(tileSize, inputW - tileX)
                var tileH = minOf(tileSize, inputH - tileY)

                // Align tile dimensions
                tileW = (tileW / DIM_ALIGNMENT) * DIM_ALIGNMENT
                tileH = (tileH / DIM_ALIGNMENT) * DIM_ALIGNMENT
                tileW = tileW.coerceAtLeast(DIM_ALIGNMENT)
                tileH = tileH.coerceAtLeast(DIM_ALIGNMENT)

                // Clamp to image bounds
                if (tileX + tileW > inputW) tileX = inputW - tileW
                if (tileY + tileH > inputH) tileY = inputH - tileH
                tileX = tileX.coerceAtLeast(0)
                tileY = tileY.coerceAtLeast(0)

                AppLogger.d(TAG, "Tile $tileIndex/$totalTiles: input($tileX,$tileY ${tileW}x${tileH})")

                // Extract tile from input
                val tileBitmap: Bitmap
                try {
                    tileBitmap = Bitmap.createBitmap(input, tileX, tileY, tileW, tileH)
                } catch (e: OutOfMemoryError) {
                    System.gc()
                    AppLogger.e(TAG, "OOM extracting tile $tileIndex/$totalTiles", e)
                    throw RuntimeException("Out of memory processing tile $tileIndex. Try a smaller image.", e)
                }

                // Process tile through model with per-tile timeout
                val tileTensor = bitmapToTensor(tileBitmap)
                val tileStart = System.currentTimeMillis()
                val outputTensor: Tensor
                try {
                    outputTensor = runModelForward(module, tileTensor)
                    val tileElapsed = System.currentTimeMillis() - tileStart
                    AppLogger.d(TAG, "Tile $tileIndex took ${tileElapsed}ms")
                } catch (e: OutOfMemoryError) {
                    tileBitmap.recycle()
                    System.gc()
                    AppLogger.e(TAG, "OOM during inference on tile $tileIndex/$totalTiles", e)
                    throw RuntimeException("Out of memory during inference. Try a smaller image or lighter model.", e)
                } catch (e: RuntimeException) {
                    tileBitmap.recycle()
                    throw e  // re-throw our own timeout RuntimeExceptions
                } catch (e: Throwable) {
                    tileBitmap.recycle()
                    AppLogger.e(TAG, "Inference FAILED on tile $tileIndex/$totalTiles", e)
                    throw RuntimeException("Model inference failed on tile $tileIndex: ${e.message}", e)
                }

                val tileOutput = tensorToBitmap(outputTensor)
                tileBitmap.recycle()

                // Upscaled tile position in output space
                val outTileX = tileX * scaleFactor
                val outTileY = tileY * scaleFactor
                val outTileW = tileOutput.width
                val outTileH = tileOutput.height

                // Read output tile pixels
                val tilePixels = IntArray(outTileW * outTileH)
                tileOutput.getPixels(tilePixels, 0, outTileW, 0, 0, outTileW, outTileH)
                tileOutput.recycle()

                // Blend tile into accumulator with feathered weights.
                // Inline bit-shift extraction avoids 3 Color.* calls per pixel.
                // Row-base pre-computation and early-break reduce redundant math.
                for (py in 0 until outTileH) {
                    val outY = outTileY + py
                    if (outY >= outputH) break
                    val outRowBase = outY * outputW
                    val tileRowBase = py * outTileW
                    for (px in 0 until outTileW) {
                        val outX = outTileX + px
                        if (outX >= outputW) break
                        val weight = computeBlendWeight(px, py, outTileW, outTileH, scaledOverlap)
                        val outIdx = outRowBase + outX
                        val pixel = tilePixels[tileRowBase + px]
                        accR[outIdx] += ((pixel shr 16) and 0xFF) * weight
                        accG[outIdx] += ((pixel shr  8) and 0xFF) * weight
                        accB[outIdx] += ( pixel          and 0xFF) * weight
                        accW[outIdx] += weight
                    }
                }

                AppLogger.d(TAG, "Tile $tileIndex/$totalTiles done: output(${outTileX},${outTileY} ${outTileW}x${outTileH})")
            }
        }

        // Compose final output from accumulated weighted pixels
        AppLogger.i(TAG, "Compositing ${totalTiles} tiles into ${outputW}x${outputH} output...")
        // Compose with reciprocal multiply (avoids division per-channel),
        // proper rounding, and inline ARGB packing.
        val outputPixels = IntArray(outputW * outputH)
        val alphaFull = 0xFF shl 24
        for (i in outputPixels.indices) {
            val wt = accW[i]
            if (wt > 0f) {
                val invW = 1f / wt
                val r = (accR[i] * invW + 0.5f).toInt().coerceIn(0, 255)
                val g = (accG[i] * invW + 0.5f).toInt().coerceIn(0, 255)
                val b = (accB[i] * invW + 0.5f).toInt().coerceIn(0, 255)
                outputPixels[i] = alphaFull or (r shl 16) or (g shl 8) or b
            } else {
                outputPixels[i] = alphaFull // black
            }
        }

        val outputBitmap = Bitmap.createBitmap(outputW, outputH, Bitmap.Config.ARGB_8888)
        outputBitmap.setPixels(outputPixels, 0, outputW, 0, 0, outputW, outputH)
        return outputBitmap
    }

    /**
     * Compute a blend weight for a pixel at (x, y) within a tile of size (w, h).
     * Uses linear feathering in the overlap region so adjacent tiles blend seamlessly.
     * Pixels far from edges get weight 1.0; pixels near edges linearly ramp from 0 to 1.
     */
    private fun computeBlendWeight(x: Int, y: Int, w: Int, h: Int, overlap: Int): Float {
        if (overlap <= 0) return 1f
        val feather = overlap.toFloat()

        val wx = when {
            x < overlap -> (x + 0.5f) / feather
            x >= w - overlap -> (w - x - 0.5f) / feather
            else -> 1f
        }.coerceIn(0.01f, 1f)

        val wy = when {
            y < overlap -> (y + 0.5f) / feather
            y >= h - overlap -> (h - y - 0.5f) / feather
            else -> 1f
        }.coerceIn(0.01f, 1f)

        return wx * wy
    }

    /**
     * Align both dimensions to DIM_ALIGNMENT without downscaling.
     * The image is used at its full resolution — tiling handles large images.
     */
    private fun alignDimensions(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

        // Align dimensions to DIM_ALIGNMENT (round down, minimum DIM_ALIGNMENT)
        val alignedW = ((w / DIM_ALIGNMENT) * DIM_ALIGNMENT).coerceAtLeast(DIM_ALIGNMENT)
        val alignedH = ((h / DIM_ALIGNMENT) * DIM_ALIGNMENT).coerceAtLeast(DIM_ALIGNMENT)

        if (alignedW == w && alignedH == h) return bitmap

        // Center-crop instead of scaling — preserves original pixel fidelity.
        // At most (DIM_ALIGNMENT - 1) pixels trimmed per edge.
        val cropX = (w - alignedW) / 2
        val cropY = (h - alignedH) / 2
        AppLogger.i(TAG, "Aligning dimensions: ${w}x${h} -> ${alignedW}x${alignedH} (center-crop, offset $cropX,$cropY)")
        return Bitmap.createBitmap(bitmap, cropX, cropY, alignedW, alignedH)
    }

    private fun bitmapToTensor(bitmap: Bitmap): Tensor {
        val width = bitmap.width
        val height = bitmap.height
        val pixelCount = width * height
        val pixels = IntArray(pixelCount)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Build planar [1, 3, H, W] float array directly.
        // Inline bit-shift extraction + reciprocal multiply for throughput.
        // Avoids 3 Color.red/green/blue method calls per pixel.
        val floats = FloatArray(3 * pixelCount)
        val inv255 = 1.0f / 255.0f
        val planeG = pixelCount
        val planeB = pixelCount shl 1
        for (i in 0 until pixelCount) {
            val px = pixels[i]
            floats[i]          = ((px shr 16) and 0xFF) * inv255  // R plane
            floats[planeG + i] = ((px shr  8) and 0xFF) * inv255  // G plane
            floats[planeB + i] = ( px          and 0xFF) * inv255  // B plane
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

        // Inline ARGB packing with proper rounding (+0.5f) for accuracy.
        // Avoids Color.argb() call overhead per pixel.
        val alphaFull = 0xFF shl 24
        if (channels >= 3) {
            val pG = planeSize
            val pB = planeSize shl 1
            for (i in 0 until planeSize) {
                val r = (data[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                val g = (data[pG + i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                val b = (data[pB + i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                pixels[i] = alphaFull or (r shl 16) or (g shl 8) or b
            }
        } else {
            for (i in 0 until planeSize) {
                val r = (data[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                val g = (data[planeSize + i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                pixels[i] = alphaFull or (r shl 16) or (g shl 8)
            }
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
                    val buffer = ByteArray(262144) // 256KB buffer — faster extraction for large ESRGAN models (~64MB)
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
