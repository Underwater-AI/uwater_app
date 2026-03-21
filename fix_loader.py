import os

files = [
    "app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt",
    "app/src/main/java/com/underwaterai/enhance/model/ImageClassifier.kt"
]

for file in files:
    with open(file, 'r') as f:
        content = f.read()
        
    old_fun = "fun loadModel() {"
    new_fun = "fun loadModelIfNeeded() {\n        if (module != null) return\n"
    
    content = content.replace(old_fun, new_fun)
    with open(file, 'w') as f:
        f.write(content)

