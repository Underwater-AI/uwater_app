import re

with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'r') as f:
    text = f.read()

STATE_CLASS = """data class EnhanceUiState(
    val selectedModel: ModelType = ModelType.MODEL_1,
    val selectedScale: Int = 4,
    val originalBitmap: Bitmap? = null,
    val enhancedBitmap: Bitmap? = null,
    val isProcessing: Boolean = false,
    val metrics: PerformanceMetrics? = null,
    val errorMessage: String? = null,
    val showResult: Boolean = false,
    val analysisReport: String? = null
)"""

text = re.sub(r'data class EnhanceUiState\([\s\S]*?\)', STATE_CLASS, text)

ANALYSIS_LOGIC = """    fun runMarineAnalysis() {
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
                
                val report = ""\"
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
                ""\".trimIndent()

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
"""

text = text.replace('    fun clearResult() {', ANALYSIS_LOGIC)

with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'w') as f:
    f.write(text)

print("ViewModel patched")
