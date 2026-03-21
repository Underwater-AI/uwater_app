file_path = "app/src/main/java/com/underwaterai/enhance/model/ImageEnhancer.kt"
with open(file_path, "r") as f:
    text = f.read()

text = text.replace("runEnhancement(inputBitmap, modelType, targetScale)", "return runEnhancement(inputBitmap, modelType, targetScale)")

with open(file_path, "w") as f:
    f.write(text)
