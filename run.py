import re

def insert_before(filepath, target, replacement):
    with open(filepath, 'r') as f:
        text = f.read()
    if target in text and replacement not in text:
        text = text.replace(target, replacement)
        with open(filepath, 'w') as f:
            f.write(text)

insert_before("app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt",
              "val outputTuple = mod.forward(inputs).toTuple()",
              "org.pytorch.PyTorchAndroid.setNumThreads(1)\n        val outputTuple = mod.forward(inputs).toTuple()")

insert_before("app/src/main/java/com/underwaterai/enhance/model/ImageClassifier.kt",
              "val outputTensor = mod.forward(IValue.from(inputTensor)).toTensor()",
              "org.pytorch.PyTorchAndroid.setNumThreads(1)\n        val outputTensor = mod.forward(IValue.from(inputTensor)).toTensor()")

insert_before("app/src/main/java/com/underwaterai/enhance/model/ImageEnhancer.kt",
              "val outTensor = mod.forward(IValue.from(inTensor)).toTensor()",
              "org.pytorch.PyTorchAndroid.setNumThreads(1)\n            val outTensor = mod.forward(IValue.from(inTensor)).toTensor()")

