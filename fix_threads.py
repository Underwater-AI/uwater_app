with open('app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt', 'r') as f:
    text = f.read()

text = text.replace('org.pytorch.PyTorchAndroid.setNumThreads(4)', 'org.pytorch.PyTorchAndroid.setNumThreads(Runtime.getRuntime().availableProcessors())')

with open('app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt', 'w') as f:
    f.write(text)

with open('app/src/main/java/com/underwaterai/enhance/model/ImageClassifier.kt', 'r') as f:
    text = f.read()

import re
text = text.replace('module = Module.load(modelFile)', 'org.pytorch.PyTorchAndroid.setNumThreads(Runtime.getRuntime().availableProcessors())\\n            module = Module.load(modelFile)')
with open('app/src/main/java/com/underwaterai/enhance/model/ImageClassifier.kt', 'w') as f:
    f.write(text)

