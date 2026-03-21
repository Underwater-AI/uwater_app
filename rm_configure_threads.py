import os
filepath = 'app/src/main/java/com/underwaterai/enhance/model/ImageEnhancer.kt'
with open(filepath, 'r') as f:
    text = f.read()

text = text.replace('configureThreads()', '// configureThreads()')
with open(filepath, 'w') as f:
    f.write(text)
