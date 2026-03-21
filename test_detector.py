import torch
from torchvision.models.detection import ssdlite320_mobilenet_v3_large, SSDLite320_MobileNet_V3_Large_Weights
from torchvision.ops import boxes as box_ops
import types

# Custom NMS that uses torch.ops.torchvision.nms will fail on mobile if not bundled.
# We can replace box_ops.batched_nms with a pure torch implementation
def python_nms(boxes, scores, iou_threshold):
    # Pure Python NMS implementation (vectorized as much as possible)
    # just an approximation or skip it and let Kotlin do NMS.
    pass

model = ssdlite320_mobilenet_v3_large(weights=SSDLite320_MobileNet_V3_Large_Weights.DEFAULT)
