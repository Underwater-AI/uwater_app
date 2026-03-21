with open("app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt", "r") as f:
    content = f.read()

content = content.replace("fun loadModel() {", "fun loadModelIfNeeded() {\n        if (module != null) return")

with open("app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt", "w") as f:
    f.write(content)
