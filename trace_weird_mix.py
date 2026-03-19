import torch
import spandrel
from torch.utils.mobile_optimizer import optimize_for_mobile
import time

path = "models/weird_mix.pth"
out_path_traced = "models/mobile/model_weird_mix_4x_traced.pt"
out_path = "models/mobile/model_weird_mix_4x.pt"

print("Loading...")
model_desc = spandrel.ModelLoader().load_from_file(path)
model = model_desc.model
model.eval()
model.cpu()

print("Tracing... (this might take a few minutes)")
start = time.time()
example_input = torch.randn(1, 3, 16, 16) # Very small input
with torch.no_grad():
    traced = torch.jit.trace(model, example_input, check_trace=False)
print(f"Tracing done in {time.time() - start:.2f}s")

torch.jit.save(traced, out_path_traced)
print(f"Traced model saved to {out_path_traced}")

print("Optimizing...")
start = time.time()
optimized = optimize_for_mobile(traced)
print(f"Optimizing done in {time.time() - start:.2f}s")

optimized._save_for_lite_interpreter(out_path)
print("Saved lite:", out_path)
