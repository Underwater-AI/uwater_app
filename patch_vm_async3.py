import re

with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'r') as f:
    text = f.read()

# Make aiDispatcher Dispatchers.Default
text = text.replace(
    'private val aiDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()',
    'private val aiDispatcher = kotlinx.coroutines.Dispatchers.Default // Use multiple cores for max frequency'
)

old_analysis = """                // Feature: Actual Image Classification
                classifier.loadModelIfNeeded()
                val classifications = classifier.classify(original) ?: emptyList()
                val topLabel = classifications.firstOrNull()?.label ?: "Unknown"
                val allLabels = classifications.joinToString(", ") { "${it.label} (${(it.score * 100).toInt()}%)" }
                
                // Feature: Actual Object Detection
                detector.loadModelIfNeeded()
                val detections = detector.detect(original, 0.4f)
                val detectedObjects = detections.joinToString(", ") { "${it.labelName} [${(it.score * 100).toInt()}%]" }"""

new_analysis = """                // Feature: Execute concurrently to utilize multiple cores and reduce latency
                val classificationsDeferred = kotlinx.coroutines.async { 
                    classifier.loadModelIfNeeded()
                    classifier.classify(original) ?: emptyList()
                }
                
                val detectionsDeferred = kotlinx.coroutines.async {
                    detector.loadModelIfNeeded()
                    detector.detect(original, 0.4f)
                }
                
                val classifications = classificationsDeferred.await()
                val detections = detectionsDeferred.await()
                
                val topLabel = classifications.firstOrNull()?.label ?: "Unknown"
                val allLabels = classifications.joinToString(", ") { "${it.label} (${(it.score * 100).toInt()}%)" }
                val detectedObjects = detections.joinToString(", ") { "${it.labelName} [${(it.score * 100).toInt()}%]" }"""

if old_analysis in text:
    text = text.replace(old_analysis, new_analysis)
else:
    print("WARNING: Could not find old analysis block.")

with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'w') as f:
    f.write(text)
print("Updated EnhanceViewModel.kt")
