import os

filepath = 'app/src/main/java/com/underwaterai/enhance/UnderwaterAIApp.kt'
with open(filepath, 'r') as f:
    text = f.read()

if 'org.pytorch.PyTorchAndroid.setNumThreads' not in text:
    text = text.replace('AppLogger.i(TAG, "Max memory', 'org.pytorch.PyTorchAndroid.setNumThreads(1)\n        AppLogger.i(TAG, "Max memory')
    with open(filepath, 'w') as f:
        f.write(text)
