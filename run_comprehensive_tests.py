import torch
import torchvision.transforms as transforms
from PIL import Image
import os
import time

print("--- Underwater AI Comprehensive Test Suite ---")

models_dir = "models"
assets_dir = "app/src/main/assets/models"
test_imgs_dir = "test_images"

images = {}
for file in os.listdir(test_imgs_dir):
    if file.endswith(".jpg") or file.endswith(".png"):
        img = Image.open(os.path.join(test_imgs_dir, file)).convert("RGB")
        images[file] = img

def test_inference(model, tensor_input, name):
    print(f"\n[{name}]")
    torch.set_num_threads(1)
    start = time.time()
    res1 = model(tensor_input)
    t1 = time.time() - start
    
    torch.set_num_threads(4)
    start = time.time()
    res2 = model(tensor_input)
    t2 = time.time() - start
    
    print(f"1-thread inference: {t1:.3f}s")
    print(f"4-thread inference: {t2:.3f}s")
    return res1

try:
    print("\n--- 1. Testing Classification ---")
    classifier = torch.jit.load(os.path.join(assets_dir, "classifier_mobilenet_v3.ptl"))
    with open(os.path.join(assets_dir, "imagenet_classes.txt")) as f:
        imagenet_classes = [line.strip() for line in f.readlines()]
        
    transform_clf = transforms.Compose([
        transforms.Resize(256),
        transforms.CenterCrop(224),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
    ])
    
    for name, img in images.items():
        inp = transform_clf(img).unsqueeze(0)
        out = test_inference(classifier, inp, f"Classifier - {name}")
        scores = torch.nn.functional.softmax(out[0], dim=0)
        top3_prob, top3_catid = torch.topk(scores, 3)
        for i in range(top3_prob.size(0)):
            print(f"  {imagenet_classes[top3_catid[i]]}: {top3_prob[i].item():.4f}")
except Exception as e:
    print("Classification failed:", e)

try:
    print("\n--- 2. Testing Detection ---")
    detector = torch.jit.load(os.path.join(assets_dir, "detector_ssdlite.ptl"))
    transform_det = transforms.Compose([
        transforms.Resize((320, 320)),
        transforms.ToTensor(),
    ])
    
    for name, img in images.items():
        inp = transform_det(img).unsqueeze(0)
        out = test_inference(detector, [inp.squeeze(0)], f"Detector - {name}")
        if isinstance(out, tuple):
            print(f"Detections shape: {out[0].shape}")
except Exception as e:
    print("Detection failed:", e)

print("Tests finished.")
