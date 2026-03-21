with open("app/src/main/java/com/underwaterai/enhance/model/ImageClassifier.kt", "r") as f:
    content = f.read()

content = content.replace("init {\n        loadModel()\n    }", "")
content = content.replace("private fun loadModel() {", "fun loadModelIfNeeded() {\n        if (module != null) return")

with open("app/src/main/java/com/underwaterai/enhance/model/ImageClassifier.kt", "w") as f:
    f.write(content)
