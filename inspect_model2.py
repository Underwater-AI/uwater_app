import torch
import torchvision
print("Imports successful")
try:
    model = torch.jit.load("app/src/main/assets/models/detector_ssdlite.ptl")
    print("Model loaded")
except Exception as e:
    print(f"Exception: {e}")
