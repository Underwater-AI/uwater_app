import torch
import torchvision
from typing import List
from torch.utils.mobile_optimizer import optimize_for_mobile
from torchvision.models.detection import ssdlite320_mobilenet_v3_large, SSDLite320_MobileNet_V3_Large_Weights

def python_nms(boxes: torch.Tensor, scores: torch.Tensor, iou_threshold: float) -> torch.Tensor:
    keep: List[int] = []
    idxs = scores.argsort(descending=True)
    while idxs.numel() > 0:
        i = int(idxs[0])
        keep.append(i)
        if idxs.numel() == 1:
            break
        box_i = boxes[i]
        other_boxes = boxes[idxs[1:]]
        
        x1 = torch.max(box_i[0], other_boxes[:, 0])
        y1 = torch.max(box_i[1], other_boxes[:, 1])
        x2 = torch.min(box_i[2], other_boxes[:, 2])
        y2 = torch.min(box_i[3], other_boxes[:, 3])
        
        inter = torch.clamp(x2 - x1, min=0) * torch.clamp(y2 - y1, min=0)
        area_i = (box_i[2] - box_i[0]) * (box_i[3] - box_i[1])
        area_other = (other_boxes[:, 2] - other_boxes[:, 0]) * (other_boxes[:, 3] - other_boxes[:, 1])
        union = area_i + area_other - inter
        iou = inter / union
        
        idx_to_keep = iou <= iou_threshold
        idxs = idxs[1:][idx_to_keep]
    return torch.tensor(keep, dtype=torch.int64)

torchvision.ops.boxes.nms = python_nms

try:
    print("Preparing Object Detection Model (Pure Torch NMS)...")
    det_model = ssdlite320_mobilenet_v3_large(weights=SSDLite320_MobileNet_V3_Large_Weights.DEFAULT)
    det_model.eval()

    scripted_det = torch.jit.script(det_model)
    opt_det = optimize_for_mobile(scripted_det)
    opt_det.save("models/mobile/ssdlite320_mobilenet_v3_large.pt")
    print("Saved successfully!")
except Exception as e:
    print(f"Error: {e}")
