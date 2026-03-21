import glob
import os
import re

files = [
    "app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt",
    "app/src/main/java/com/underwaterai/enhance/model/ImageClassifier.kt",
    "app/src/main/java/com/underwaterai/enhance/model/ImageEnhancer.kt",
    "app/src/main/java/com/underwaterai/enhance/UnderwaterAIApp.kt"
]

for file in files:
    with open(file, 'r') as f:
        text = f.read()

    # Remove all setNumThreads
    text = re.sub(r'org\.pytorch\.PyTorchAndroid\.setNumThreads\([0-9]+\)', '', text)
    # Remove withContext(Dispatchers.Default) wrapper
    text = re.sub(r'=\s*withContext\(Dispatchers\.Default\)\s*\{', '= {', text)

    with open(file, 'w') as f:
        f.write(text)

