with open('app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt', 'r') as f:
    text = f.read()

import re
text = text.replace('org.pytorch.torchvision.TensorImageUtils.TORCHVISION_NORM_MEAN_RGB', 
'''org.pytorch.torchvision.TensorImageUtils.TORCHVISION_NORM_MEAN_RGB
            org.pytorch.PyTorchAndroid.setNumThreads(4)''')

text = text.replace('// Force torchvision JNI init', '''// Force torchvision JNI init
        try {
            System.loadLibrary("pytorch_vision_jni")
        } catch (e: Exception) {
            e.printStackTrace()
        }''')

with open('app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt', 'w') as f:
    f.write(text)

