import re

files = [
    "app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt",
    "app/src/main/java/com/underwaterai/enhance/model/ImageClassifier.kt",
    "app/src/main/java/com/underwaterai/enhance/model/ImageEnhancer.kt",
]

for file_path in files:
    with open(file_path, "r") as f:
        text = f.read()
    
    # Remove setNumThreads everywhere
    text = re.sub(r'PyTorchAndroid\.setNumThreads\(1\)', '', text)
    
    # Change = withContext(Dispatchers.Default) { to just {
    text = re.sub(r'=\s*withContext\(Dispatchers\.Default\)\s*\{', '{', text)
    
    # Change return@withContext result to just return result
    text = re.sub(r'return@withContext\s+', 'return ', text)
    
    with open(file_path, "w") as f:
        f.write(text)

with open("app/src/main/java/com/underwaterai/enhance/UnderwaterAIApp.kt", "r") as f:
    text = f.read()
text = re.sub(r'PyTorchAndroid\.setNumThreads\(1\)', '', text)
with open("app/src/main/java/com/underwaterai/enhance/UnderwaterAIApp.kt", "w") as f:
    f.write(text)

