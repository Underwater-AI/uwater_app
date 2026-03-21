import os
filepath = 'app/src/main/java/com/underwaterai/enhance/model/ImageEnhancer.kt'
with open(filepath, 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    # Only comment out the CALL to configureThreads, not the definition
    if line.strip() == 'configureThreads()':
        new_lines.append('        // configureThreads()\n')
    else:
        new_lines.append(line)

with open(filepath, 'w') as f:
    f.writelines(new_lines)
