import torch
import torchvision

model = torch.jit.load("app/src/main/assets/models/detector_ssdlite.ptl")
model.eval()
dummy_input = [torch.rand(3, 320, 320)]
out = model(dummy_input)
print(f"Output type: {type(out)}")
if isinstance(out, tuple):
    for i, item in enumerate(out):
        print(f"Item {i} type: {type(item)}")
        if isinstance(item, list):
            print(f"List length: {len(item)}")
            for j, d in enumerate(item):
                print(f"Dict {j} keys: {d.keys()}")
                for k, v in d.items():
                    print(f"  {k}: {v.shape}")
        elif isinstance(item, dict):
            print(f"Dict keys: {item.keys()}")
            for k, v in item.items():
                print(f"  {k}: {v.shape}")
