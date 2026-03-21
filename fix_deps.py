import os

filepath = 'app/build.gradle.kts'
with open(filepath, 'r') as f:
    text = f.read()

text = text.replace('implementation("org.pytorch:pytorch_android_torchvision:2.1.0")', 'implementation("org.pytorch:pytorch_android_torchvision_lite:2.1.0")')

with open(filepath, 'w') as f:
    f.write(text)
