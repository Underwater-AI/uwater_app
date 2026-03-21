import os
filepath = 'app/build.gradle.kts'
with open(filepath, 'r') as f:
    text = f.read()

text = text.replace('pytorch_android_lite:2.1.0', 'pytorch_android:2.1.0')
text = text.replace('pytorch_android_torchvision_lite:2.1.0', 'pytorch_android_torchvision:2.1.0')
with open(filepath, 'w') as f:
    f.write(text)
