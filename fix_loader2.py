for kt_file in ["app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt", "app/src/main/java/com/underwaterai/enhance/model/ImageClassifier.kt"]:
    with open(kt_file, 'r') as f:
        content = f.read()
    content = content.replace("org.pytorch.LiteModuleLoader.load(", "org.pytorch.Module.load(")
    content = content.replace("import org.pytorch.LiteModuleLoader\n", "")
    with open(kt_file, 'w') as f:
        f.write(content)
