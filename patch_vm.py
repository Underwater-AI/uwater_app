import re

file_path = 'app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt'
with open(file_path, 'r') as f:
    code = f.read()

# Make classifier and detector lazy
code = code.replace("private val classifier = ImageClassifier(application)", "private val classifier by lazy { ImageClassifier(application) }")
code = code.replace("private val detector = ObjectDetector(application).apply { loadModel() }", "private val detector by lazy { ObjectDetector(application) }")
code = code.replace("private val detector = ObjectDetector(application)", "private val detector by lazy { ObjectDetector(application) }")

code = code.replace("val classifications = classifier.classify(original)", "classifier.loadModelIfNeeded()\n                val classifications = classifier.classify(original)")
code = code.replace("val detections = detector.detect(original, 0.4f)", "detector.loadModelIfNeeded()\n                val detections = detector.detect(original, 0.4f)")

with open(file_path, 'w') as f:
    f.write(code)

print("ViewModel patched.")
