with open('app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt', 'r') as f:
    text = f.read()

import re
text = text.replace('org.pytorch.LiteModuleLoader.load(modelPath)', 'org.pytorch.Module.load(modelPath)')

with open('app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt', 'w') as f:
    f.write(text)

