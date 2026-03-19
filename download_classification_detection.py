import torch
import torchvision
from torch.utils.mobile_optimizer import optimize_for_mobile
from torchvision.models import mobilenet_v3_large, MobileNet_V3_Large_Weights
from torchvision.models.detection import ssdlite320_mobilenet_v3_large, SSDLite320_MobileNet_V3_Large_Weights

import os
os.makedirs("models/mobile", exist_ok=True)

print("Preparing Classification Model (MobileNetV3 Large)...")
class_model = mobilenet_v3_large(weights=MobileNet_V3_Large_Weights.DEFAULT)
class_model.eval()

# Trace
example_input = torch.rand(1, 3, 224, 224)
traced_class = torch.jit.trace(class_model, example_input)
opt_class = optimize_for_mobile(traced_class)
opt_class.save("models/mobile/mobilenet_v3_large.pt")
print("Saved models/mobile/mobilenet_v3_large.pt")

print("Preparing Object Detection Model (SSDLite320 MobileNetV3)...")
det_model = ssdlite320_mobilenet_v3_large(weights=SSDLite320_MobileNet_V3_Large_Weights.DEFAULT)
det_model.eval()

# Torchscript/Trace detection model
example_det_input = torch.rand(1, 3, 320, 320)
# Detection models return dicts which can be tricky to trace. We might need script.
# Let's try script first.
scripted_det = torch.jit.script(det_model)
opt_det = optimize_for_mobile(scripted_det)
opt_det.save("models/mobile/ssdlite320_mobilenet_v3_large.pt")
print("Saved models/mobile/ssdlite320_mobilenet_v3_large.pt")
