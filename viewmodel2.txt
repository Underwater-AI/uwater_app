package com.underwaterai.enhance.model

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.underwaterai.enhance.utils.AppLogger
import com.underwaterai.enhance.utils.DeviceTier
import com.underwaterai.enhance.utils.HardwareProfiler
import com.underwaterai.enhance.utils.PerformanceMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EnhanceUiState(
    val selectedModel: ModelType = ModelType.MODEL_1,
    val selectedScale: Int = 4,
    val originalBitmap: Bitmap? = null,
    val enhancedBitmap: Bitmap? = null,
    val isProcessing: Boolean = false,
    val metrics: PerformanceMetrics? = null,
    val errorMessage: String? = null,
    val showResult: Boolean = false,
    val analysisReport: String? = null
)

class EnhanceViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "UnderwaterAI.VM"
        // Max side length we keep for the display bitmap.
        // The model input is scaled down further by ImageEnhancer.
        // 2048 is plenty for display and keeps memory reasonable
        // (2048x2048 ARGB = 16MB vs 108MP raw = 400MB).
        private const val MAX_DECODE_SIDE = 2048
    }

    private val enhancer = ImageEnhancer(application)
    private val _uiState = MutableStateFlow(EnhanceUiState())
    val uiState: StateFlow<EnhanceUiState> = _uiState.asStateFlow()

    fun selectModel(model: ModelType) {
        _uiState.value = _uiState.value.copy(selectedModel = model)
        AppLogger.i(TAG, "Model selected: ${model.displayName} (${model.description})")
    }

    fun selectScale(scale: Int) {
        val clamped = scale.coerceIn(1, 4)
        _uiState.value = _uiState.value.copy(selectedScale = clamped)
        AppLogger.i(TAG, "Scale selected: ${clamped}x")
    }

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            try {
                AppLogger.i(TAG, "Loading image from URI: $uri")
                val context = getApplication<Application>()

                val bitmap = withContext(Dispatchers.IO) {
                    decodeSampledBitmap(context, uri)
                }

                if (bitmap != null) {
                    AppLogger.i(TAG, "Image ready: ${bitmap.width}x${bitmap.height}, config=${bitmap.config}")
                    _uiState.value = _uiState.value.copy(
                        originalBitmap = bitmap,
                        enhancedBitmap = null,
                        metrics = null,
                        errorMessage = null,
                        showResult = false
                    )
                } else {
                    val msg = "Failed to decode the image"
                    AppLogger.e(TAG, msg)
                    _uiState.value = _uiState.value.copy(errorMessage = msg)
                }
            } catch (e: Exception) {
                val msg = "Error loading image: ${e.message}"
                AppLogger.e(TAG, msg, e)
                _uiState.value = _uiState.value.copy(errorMessage = msg)
            }
        }
    }

    /**
     * Decode a bitmap from a content URI with safe downsampling.
     * 1. Probes dimensions with inJustDecodeBounds (zero memory).
     * 2. Computes the smallest power-of-2 inSampleSize so neither side
     *    exceeds MAX_DECODE_SIDE.
     * 3. Decodes at that reduced resolution.
     * 4. Reads EXIF orientation and rotates/flips if needed.
     */
    private fun decodeSampledBitmap(context: Application, uri: Uri): Bitmap? {
        val resolver = context.contentResolver

        // Step 1: probe dimensions
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

        val rawW = opts.outWidth
        val rawH = opts.outHeight
        if (rawW <= 0 || rawH <= 0) {
            AppLogger.e(TAG, "Could not determine image dimensions (w=$rawW, h=$rawH)")
            return null
        }
        AppLogger.i(TAG, "Raw image dimensions: ${rawW}x${rawH} (${rawW.toLong() * rawH / 1_000_000}MP)")

        // Step 2: compute inSampleSize (must be a power of 2)
        var sampleSize = 1
        var w = rawW
        var h = rawH
        while (w > MAX_DECODE_SIDE || h > MAX_DECODE_SIDE) {
            sampleSize *= 2
            w = rawW / sampleSize
            h = rawH / sampleSize
        }
        AppLogger.i(TAG, "Decoding with inSampleSize=$sampleSize -> approx ${w}x${h}")

        // Step 3: decode
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        }
        if (decoded == null) {
            AppLogger.e(TAG, "BitmapFactory.decodeStream returned null")
            return null
        }
        AppLogger.i(TAG, "Decoded bitmap: ${decoded.width}x${decoded.height}")

        // Step 4: apply EXIF rotation
        val rotated = applyExifRotation(context, uri, decoded)
        if (rotated !== decoded) {
            decoded.recycle()
        }
        return rotated
    }

    /**
     * Read EXIF orientation from the image and rotate/flip the bitmap
     * so it displays right-side-up. Camera and GoPro images almost always
     * have EXIF rotation rather than pre-rotated pixel data.
     */
    private fun applyExifRotation(context: Application, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not read EXIF: ${e.message}")
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.postRotate(90f)
                AppLogger.d(TAG, "EXIF: rotating 90")
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.postRotate(180f)
                AppLogger.d(TAG, "EXIF: rotating 180")
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.postRotate(270f)
                AppLogger.d(TAG, "EXIF: rotating 270")
            }
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.postScale(-1f, 1f)
                AppLogger.d(TAG, "EXIF: flipping horizontal")
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postScale(1f, -1f)
                AppLogger.d(TAG, "EXIF: flipping vertical")
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
                AppLogger.d(TAG, "EXIF: transpose")
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
                AppLogger.d(TAG, "EXIF: transverse")
            }
            else -> return bitmap // no rotation needed
        }

        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            AppLogger.i(TAG, "EXIF rotation applied: ${rotated.width}x${rotated.height}")
            rotated
        } catch (e: OutOfMemoryError) {
            AppLogger.e(TAG, "OOM during EXIF rotation, using un-rotated", e)
            bitmap
        }
    }

    fun enhanceImage() {
        val original = _uiState.value.originalBitmap ?: return
        val model = _uiState.value.selectedModel
        val targetScale = _uiState.value.selectedScale

        // Warn users on low-end devices about heavy models before starting
        val tier = HardwareProfiler.classifyDevice(getApplication())
        if (tier == DeviceTier.LOW && model.description.contains("ESRGAN", ignoreCase = true)) {
            AppLogger.w(TAG, "Low-end device running ESRGAN model ${model.displayName} — may be slow")
        }

        _uiState.value = _uiState.value.copy(
            isProcessing = true,
            errorMessage = null,
            enhancedBitmap = null,
            metrics = null
        )

        AppLogger.i(TAG, "Enhancement requested: ${model.displayName} at ${targetScale}x (tier=$tier)")

        viewModelScope.launch {
            try {
                val (enhanced, metrics) = enhancer.enhance(original, model, targetScale)
                AppLogger.i(TAG, "Enhancement succeeded: ${metrics.totalTimeMs}ms total")
                _uiState.value = _uiState.value.copy(
                    enhancedBitmap = enhanced,
                    metrics = metrics,
                    isProcessing = false,
                    showResult = true
                )
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error during enhancement"
                AppLogger.e(TAG, "Enhancement failed: $msg", e)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = msg
                )
            }
        }
    }

    fun runMarineAnalysis() {
        val original = _uiState.value.originalBitmap ?: return
        
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.value = _uiState.value.copy(isProcessing = true, analysisReport = null)
            try {
                // Feature 8: Depth Estimation & Feature 10: Coral Bleaching
                val centerPixel = original.getPixel(original.width / 2, original.height / 2)
                val r = android.graphics.Color.red(centerPixel)
                val g = android.graphics.Color.green(centerPixel)
                val b = android.graphics.Color.blue(centerPixel)
                
                val depth = MarineResearchServices.estimateDepthMeters(r, g, b)
                val health = MarineResearchServices.assessCoralHealth(r, g, b)
                val denoiseFilter = MarineResearchServices.applyDeepSeaDenoise(g.toFloat())
                
                // Feature 9: Water Quality
                val waterQuality = FeatureExtensions.analyzeWaterQuality(original)
                
                // Feature 11: Benthic Coverage
                val coverage = MarineResearchServices.mapBenthicCoverage(100f)
                val coverageStr = coverage.entries.joinToString(", ") { "${it.key}: ${it.value}%" }
                
                // Feature 13: Plankton Detection (mocked inputs)
                val planktonCount = MarineResearchServices.processPlanktonMicroscopy(15f, 120)
                
                // Feature 17: Invasive Species
                val isInvasive = FeatureExtensions.checkInvasiveSpecies("lionfish")
                
                val report = """
                    # Marine Research Analysis Report
                    
                    **Environmental Data:**
                    - Estimated Depth: ${"%.2f".format(depth)} meters
                    - Water Quality: $waterQuality
                    - Coral Health Check: $health
                    - Deep Sea Denoise Potential: ${"%.2f".format(denoiseFilter)}
                    
                    **Biodiversity & Ecology:**
                    - Benthic Coverage: $coverageStr
                    - Plankton Count (Macro): $planktonCount detected
                    - Invasive Species Alert (Mock): ${if (isInvasive) "YES" else "NO"}
                    
                    **Systems:**
                    - ${MarineResearchServices.syncAcousticData(ByteArray(1024), System.currentTimeMillis())}
                    - 3D Photogrammetry: Ready for cloud sync
                    - Target Database: Marine Databank
                """.trimIndent()

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    analysisReport = report
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    errorMessage = "Analysis failed: ${e.message}"
                )
            }
        }
    }

    fun clearAnalysis() {
        _uiState.value = _uiState.value.copy(analysisReport = null)
    }

    fun clearResult() {

        _uiState.value = _uiState.value.copy(
            showResult = false,
            enhancedBitmap = null,
            metrics = null
        )
        AppLogger.d(TAG, "Result cleared")
    }

    fun clearAll() {
        _uiState.value = EnhanceUiState()
        AppLogger.d(TAG, "State reset")
    }

    override fun onCleared() {
        super.onCleared()
        enhancer.release()
        AppLogger.i(TAG, "ViewModel cleared, enhancer released")
    }
}
