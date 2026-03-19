import torch
import spandrel
from spandrel import ModelLoader

files = [
    'models/4x-UltraSharp.pth',
    'models/4x_foolhardy_Remacri.pth',
    'models/BSRGAN.pth',
    'models/RealESRGAN_x4plus.pth'
]

models = []
loader = ModelLoader()

for f in files:
    m = loader.load_from_file(f)
    print(f"Loaded {f}, arch: {m.architecture}")
    models.append(m)

# They should all be RRDBNet (or ESRGAN) and standard spandrel model architecture.
# We will use the first model's architecture to save the mixed state dict
base_model = models[0]
base_sd = base_model.model.state_dict()

# Create a new state dict
mixed_sd = {}
for k in base_sd.keys():
    tensors = [m.model.state_dict()[k] for m in models]
    mixed_sd[k] = torch.mean(torch.stack(tensors), dim=0)

base_model.model.load_state_dict(mixed_sd)

# To save in a format spandrel can load again, we can just save it. 
# But wait, we can just save the new state_dict directly! 
dict_to_save = mixed_sd
torch.save(dict_to_save, 'models/weird_mix.pth')
print("Saved models/weird_mix.pth!")
