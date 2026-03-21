import os

filepath = 'app/src/main/java/com/underwaterai/enhance/model/ImageEnhancer.kt'
with open(filepath, 'r') as f:
    text = f.read()

# carefully remove all the weird comment slashes from the private fun definition
import re
text = re.sub(r'private fun.*?configureThreads\(\) \{', 'private fun configureThreads() {', text, flags=re.DOTALL)

with open(filepath, 'w') as f:
    f.write(text)

