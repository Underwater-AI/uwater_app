import torch
import torchvision
import sys

def inspect_model(path):
    print(f"Inspecting {path}")
    model = torch.jit.load(path)
    # The app passes [1, 3, 320, 320]
    dummy_input = torch.rand(1, 3, 320, 320)
    out = model(dummy_input)
    print(f"Output type: {type(out)}")
    if isinstance(out, tuple):
        print(f"Tuple length: {len(out)}")
        for i, item in enumerate(out):
            print(f"Tuple item {i} type: {type(item)}")
            if isinstance(item, list):
                print(f"List length: {len(item)}")
                for j, l_item in enumerate(item):
                     print(f"List item {j} type: {type(l_item)}")
                     if isinstance(l_item, dict):
                         print(f"Dict keys: {l_item.keys()}")
                         for k, v in l_item.items():
                             print(f"Key {k} type: {type(v)}, shape/value: {getattr(v, 'shape', v)}")
    elif isinstance(out, list):
        print(f"List length: {len(out)}")
        for j, l_item in enumerate(out):
             print(f"List item {j} type: {type(l_item)}")
             if isinstance(l_item, dict):
                 print(f"Dict keys: {l_item.keys()}")
                 for k, v in l_item.items():
                     print(f"Key {k} type: {type(v)}, shape/value: {getattr(v, 'shape', v)}")
                     
    else:
        print(out)

inspect_model("app/src/main/assets/models/detector_ssdlite.ptl")
