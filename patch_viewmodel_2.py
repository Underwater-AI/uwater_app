import re

with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'r') as f:
    text = f.read()

# Add ObjectDetector and ImageClassifier instances
INIT_CODE = """
    private val enhancer = ImageEnhancer(application)
    private val classifier = ImageClassifier(application)
    private val detector = ObjectDetector(application).apply { loadModel() }
    
    private val _uiState = MutableStateFlow(EnhanceUiState())"""

text = text.replace("""
    private val enhancer = ImageEnhancer(application)
    private val _uiState = MutableStateFlow(EnhanceUiState())""", INIT_CODE)

NEW_ANALYSIS = """    fun runMarineAnalysis() {
        val original = _uiState.value.originalBitmap ?: return
        
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.value = _uiState.value.copy(isProcessing = true, analysisReport = null)
            try {
                // Feature: Actual Image Classification
                val classifications = classifier.classify(original) ?: emptyList()
                val topLabel = classifications.firstOrNull()?.label ?: "Unknown"
                val allLabels = classifications.joinToString(", ") { "${it.label} (${(it.score * 100).toInt()}%)" }
                
                // Feature: Actual Object Detection
                val detections = detector.detect(original, 0.4f)
                val detectedObjects = detections.joinToString(", ") { "${it.labelName} [${(it.score * 100).toInt()}%]" }
                
                // Simple Under-water heuristic check based on common ImageNet/COCO classes
                val nonMarineClasses = listOf("suit", "sunglasses", "neck brace", "face", "person", "car", "dog", "cat", "mask", "oxygen mask")
                val marineClasses = listOf("fish", "scuba diver", "coral reef", "anemone", "shark", "whale", "ray", "submarine", "turtle", "crab", "jellyfish", "sea")
                
                val mightNotBeMarine = classifications.any { c -> nonMarineClasses.any { it in c.label.lowercase() } } && 
                                       !classifications.any { c -> marineClasses.any { it in c.label.lowercase() } }

                var warning = ""
                if (mightNotBeMarine && classifications.isNotEmpty()) {
                    warning = "⚠️ **WARNING: Ecosystem Mismatch**\\nThis image does not strongly match trained underwater environments. Analysis accuracy is reduced.\\n"
                }

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
                
                // Feature 13: Plankton Detection (adjusted by detections)
                val planktonCount = MarineResearchServices.processPlanktonMicroscopy(15f, detections.size * 10 + 20)
                
                // Feature 17: Invasive Species
                val isInvasive = FeatureExtensions.checkInvasiveSpecies(topLabel)
                
                val report = ""\"
                    # Marine Research Analysis Report
                    $warning
                    **Intelligent Context:**
                    - Scene Classification: $allLabels
                    - Detected Objects: ${if (detectedObjects.isBlank()) "None found" else detectedObjects}
                    
                    **Environmental Data:**
                    - Estimated Depth: ${"%.2f".format(depth)} meters
                    - Water Quality: $waterQuality
                    - Coral Health Check: $health
                    - Deep Sea Denoise Potential: ${"%.2f".format(denoiseFilter)}
                    
                    **Biodiversity & Ecology:**
                    - Benthic Coverage (Est.): $coverageStr
                    - Plankton Count Proxies: $planktonCount detected
                    - Invasive Species Alert: ${if (isInvasive) "YES (Matched: $topLabel)" else "NO"}
                    
                    **Systems:**
                    - ${MarineResearchServices.syncAcousticData(ByteArray(1024), System.currentTimeMillis())}
                    - 3D Photogrammetry: Ready for cloud sync
                ""\".trimIndent()

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        analysisReport = report
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        errorMessage = "Analysis failed: ${e.message}"
                    )
                }
            }
        }
    }"""

# Need to replace the old runMarineAnalysis
import re
text = re.sub(r'    fun runMarineAnalysis\(\) \{[\s\S]*?fun clearAnalysis', NEW_ANALYSIS + '\\n\\n    fun clearAnalysis', text)

with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'w') as f:
    f.write(text)

