import os

filepath = 'app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt'
with open(filepath, 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if 'org.pytorch.PyTorchAndroid.setNumThreads' in line:
        continue
    new_lines.append(line)

with open(filepath, 'w') as f:
    f.writelines(new_lines)
