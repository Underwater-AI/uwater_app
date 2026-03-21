with open("app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt", "r") as f:
    content = f.read()

import re

old_code = """
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            paddedBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )

        // The model expects a List of Tensors: List[Tensor]
        val inputs = IValue.listFrom(inputTensor)
"""

new_code = """
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            paddedBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )

        // The model expects a List of 3D Tensors: List[Tensor[C, H, W]]
        val tensor3d = org.pytorch.Tensor.fromBlob(
            inputTensor.dataAsFloatArray,
            longArrayOf(3, targetSize.toLong(), targetSize.toLong())
        )
        val inputs = IValue.listFrom(tensor3d)
"""

if old_code.strip() in content:
    content = content.replace(old_code.strip(), new_code.strip())
else:
    print("Could not find exact code to replace, using regex...")
    pattern = re.compile(r"val inputTensor = TensorImageUtils\.bitmapToFloat32Tensor\(.*?\).*?val inputs = IValue\.listFrom\(inputTensor\)", re.DOTALL)
    content = re.sub(pattern, new_code.strip(), content)

with open("app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt", "w") as f:
    f.write(content)

