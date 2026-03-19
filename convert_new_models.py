import torch
import torchvision
from torch.utils.mobile_optimizer import optimize_for_mobile
import os

os.makedirs('app/src/main/assets/models', exist_ok=True)

print("Downloading and converting MobileNetV3 (Classification)...")
model_cls = torchvision.models.mobilenet_v3_small(weights=torchvision.models.MobileNet_V3_Small_Weights.DEFAULT)
model_cls.eval()

# Trace
example_input_cls = torch.rand(1, 3, 224, 224)
traced_cls = torch.jit.trace(model_cls, example_input_cls)
optimized_cls = optimize_for_mobile(traced_cls)
optimized_cls._save_for_lite_interpreter("app/src/main/assets/models/classifier_mobilenet_v3.ptl")
print("Saved classifier_mobilenet_v3.ptl")

try:
    print("Downloading and converting SSD Lite (Detection)...")
    model_det = torchvision.models.detection.ssdlite320_mobilenet_v3_large(weights=torchvision.models.detection.SSDLite320_MobileNet_V3_Large_Weights.DEFAULT)
    model_det.eval()
    example_input_det = torch.rand(1, 3, 320, 320)
    
    # Try tracing detection models (may need scripting instead)
    scripted_det = torch.jit.script(model_det)
    optimized_det = optimize_for_mobile(scripted_det)
    optimized_det._save_for_lite_interpreter("app/src/main/assets/models/detector_ssdlite.ptl")
    print("Saved detector_ssdlite.ptl")
except Exception as e:
    print(f"Error converting detection model: {e}")

