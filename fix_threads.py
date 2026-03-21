import os
import glob

def remove_setNumThreads():
    files = glob.glob("app/src/main/java/com/underwaterai/enhance/model/*.kt")
    for filepath in files:
        with open(filepath, "r") as f:
            lines = f.readlines()
        
        new_lines = []
        for line in lines:
            if "org.pytorch.PyTorchAndroid.setNumThreads" not in line:
                new_lines.append(line)
        
        with open(filepath, "w") as f:
            f.writelines(new_lines)

remove_setNumThreads()
print("Done")
