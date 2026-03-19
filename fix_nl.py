with open('app/src/main/java/com/underwaterai/enhance/model/ImageClassifier.kt', 'r') as f:
    text = f.read()

text = text.replace('\\n', '\n')

with open('app/src/main/java/com/underwaterai/enhance/model/ImageClassifier.kt', 'w') as f:
    f.write(text)
