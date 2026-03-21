import re

def insert_before(filepath, target, replacement):
    with open(filepath, 'r') as f:
        text = f.read()
    if target in text and replacement not in text:
        text = text.replace(target, replacement)
        with open(filepath, 'w') as f:
            f.write(text)


insert_before("app/src/main/java/com/underwaterai/enhance/model/ImageEnhancer.kt",
              "val result = module.forward(IValue.from(inputTensor))",
              "org.pytorch.PyTorchAndroid.setNumThreads(1)\n        val result = module.forward(IValue.from(inputTensor))")

