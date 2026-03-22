import re

with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'r') as f:
    text = f.read()

text = text.replace(
    'private val aiDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()',
    'private val aiDispatcher = kotlinx.coroutines.Dispatchers.Default // Use multiple cores for max frequency'
)

# Fix runMarineAnalysis logic if needed
old_analysis = """        viewModelScope.launch(aiDispatcher) {
            _analysisInProgress.value = true
            
            try {
                // Execute sequentially to avoid libc.so big.LITTLE segmentation faults
                // while maintaining coroutine responsiveness
                
                classifier.loadModelIfNeeded()
                val classes = classifier.classify(bitmap)
                
                detector.loadModelIfNeeded()
                val boxes = detector.detect(bitmap)"""

new_analysis = """        viewModelScope.launch(aiDispatcher) {
            _analysisInProgress.value = true
            
            try {
                // Execute concurrently to utilize multiple cores and reduce latency
                val classesDeferred = kotlinx.coroutines.async {
                    classifier.loadModelIfNeeded()
                    classifier.classify(bitmap)
                }
                
                val boxesDeferred = kotlinx.coroutines.async {
                    detector.loadModelIfNeeded()
                    detector.detect(bitmap)
                }
                
                val classes = classesDeferred.await()
                val boxes = boxesDeferred.await()"""

if old_analysis in text:
    text = text.replace(old_analysis, new_analysis)
else:
    print("WARNING: Could not find old analysis block.")

with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'w') as f:
    f.write(text)
print("Updated EnhanceViewModel.kt")
