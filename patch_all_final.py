import re

# 1. Fix EnhanceViewModel.kt
with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'r') as f:
    vm_text = f.read()

# Replace dispatcher
vm_text = vm_text.replace(
    'private val aiDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()',
    'private val aiDispatcher = kotlinx.coroutines.Dispatchers.Default'
)

# Add async to imports if not there
if 'import kotlinx.coroutines.async' not in vm_text:
    vm_text = vm_text.replace('import kotlinx.coroutines.launch', 'import kotlinx.coroutines.launch\nimport kotlinx.coroutines.async')

# Replace sequential code
old_code = """                // Feature: Actual Image Classification
                classifier.loadModelIfNeeded()
                val classifications = classifier.classify(original) ?: emptyList()
                val topLabel = classifications.firstOrNull()?.label ?: "Unknown"
                val allLabels = classifications.joinToString(", ") { "${it.label} (${(it.score * 100).toInt()}%)" }
                
                // Feature: Actual Object Detection
                detector.loadModelIfNeeded()
                val detections = detector.detect(original, 0.4f)
                val detectedObjects = detections.joinToString(", ") { "${it.labelName} [${(it.score * 100).toInt()}%]" }"""

new_code = """                // Feature: Execute models in parallel for max multi-core utilizing max frequency
                val classificationsDeferred = async {
                    classifier.loadModelIfNeeded()
                    classifier.classify(original) ?: emptyList()
                }
                
                val detectionsDeferred = async {
                    detector.loadModelIfNeeded()
                    detector.detect(original, 0.4f)
                }
                
                val classifications = classificationsDeferred.await()
                val detections = detectionsDeferred.await()
                
                val topLabel = classifications.firstOrNull()?.label ?: "Unknown"
                val allLabels = classifications.joinToString(", ") { "${it.label} (${(it.score * 100).toInt()}%)" }
                val detectedObjects = detections.joinToString(", ") { "${it.labelName} [${(it.score * 100).toInt()}%]" }"""

if old_code in vm_text:
    vm_text = vm_text.replace(old_code, new_code)
else:
    print("WARNING: Could not find viewmodel code block to replace.")

with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'w') as f:
    f.write(vm_text)


# 2. Fix MarineResearchServices.kt
with open('app/src/main/java/com/underwaterai/enhance/model/MarineResearchServices.kt', 'r') as f:
    mrs_text = f.read()

# Make sure not to double add
if "fun calculateBiodiversityIndex" not in mrs_text:
    # insert before last brace
    insertion = """
    // Advanced Feature: Biodiversity Index Calculation (Shannon-Wiener Index Proxy)
    fun calculateBiodiversityIndex(detections: List<String>, classifications: List<String>): Double {
        val allEntities = detections + classifications
        if (allEntities.isEmpty()) return 0.0
        
        val counts = allEntities.groupingBy { it }.eachCount()
        val total = allEntities.size.toDouble()
        
        var shannonIndex = 0.0
        for (count in counts.values) {
            val p = count / total
            if (p > 0) {
                shannonIndex -= p * kotlin.math.ln(p)
            }
        }
        return kotlin.math.round(shannonIndex * 100.0) / 100.0
    }
"""
    mrs_text = mrs_text.rstrip()
    if mrs_text.endswith("}"):
        mrs_text = mrs_text[:-1] + insertion + "}\n"
        with open('app/src/main/java/com/underwaterai/enhance/model/MarineResearchServices.kt', 'w') as f:
            f.write(mrs_text)

print("Patched completely.")
