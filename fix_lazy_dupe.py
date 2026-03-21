file_path = 'app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt'
with open(file_path, 'r') as f:
    code = f.read()

code = code.replace("classifier.loadModelIfNeeded()\n                classifier.loadModelIfNeeded()", "classifier.loadModelIfNeeded()")
code = code.replace("detector.loadModelIfNeeded()\n                detector.loadModelIfNeeded()", "detector.loadModelIfNeeded()")

with open(file_path, 'w') as f:
    f.write(code)
