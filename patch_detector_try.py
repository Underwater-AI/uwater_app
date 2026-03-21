with open("app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if "} catch (e: Exception) {}" in line:
        continue
    if "org.pytorch.torchvision.TensorImageUtils.TORCHVISION_NORM_MEAN_RGB" in line:
        continue
    new_lines.append(line)

with open("app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt", "w") as f:
    f.writelines(new_lines)

