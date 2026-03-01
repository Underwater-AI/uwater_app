# Model Setup Guide

The app uses PyTorch Mobile (`.pt`) models stored in
`app/src/main/assets/`.  
These files are intentionally **not committed** to git — they are large binaries
that each developer must convert locally using the provided script.

---

## 1 — Prerequisites

| Tool | Minimum version | Install |
|------|----------------|---------|
| Python | 3.10+ | <https://python.org> |
| pip / uv | latest | bundled with Python or `pip install uv` |
| Git LFS (optional) | any | only needed if hosting converted models separately |

Create and activate a virtual environment:

```bash
python -m venv .venv
source .venv/bin/activate          # Linux / macOS
# .venv\Scripts\activate           # Windows PowerShell
```

Install required Python packages:

```bash
pip install torch torchvision spandrel
```

> **PyTorch version note:** Use a CPU-only wheel unless you have a CUDA GPU.
> CPU is sufficient — the conversion runs once and the output is tiny.
>
> ```bash
> pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
> pip install spandrel
> ```

---

## 2 — Download the source `.pth` models

All models come from [OpenModelDB](https://openmodeldb.info).  
Place the downloaded files inside the `models/` directory at the project root.

| File name | OpenModelDB page | Architecture | Scale |
|-----------|-----------------|--------------|-------|
| `4x-realesr-general-x4v3.pth` | <https://openmodeldb.info/models/4x-realesr-general-x4v3> | SRVGGNetCompact | 4× |
| `4xPurePhoto-span.pth` | <https://openmodeldb.info/models/4x-PurePhoto-SPAN> | SPAN | 4× |
| `4x-Rybu.pth` | <https://openmodeldb.info/models/4x-Rybu> | SRVGGNetCompact | 4× |
| `realesr-animevideov3.pth` | <https://openmodeldb.info/models/4x-realesr-animevideov3> | SRVGGNetCompact | 4× |
| `4x-Nomos8k-span-otf-medium.pth` | <https://openmodeldb.info/models/4x-Nomos8k-span-otf-medium> | SPAN | 4× |

### Quick download with `wget` (Linux / macOS)

```bash
mkdir -p models
cd models

wget -O "4x-realesr-general-x4v3.pth" \
  "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-general-x4v3.pth"

wget -O "4xPurePhoto-span.pth" \
  "https://github.com/the-database/spandrel-models/releases/download/4x-PurePhoto-SPAN/4xPurePhoto-SPAN.pth"

wget -O "4x-Rybu.pth" \
  "https://github.com/the-database/spandrel-models/releases/download/4x-Rybu/4x-Rybu.pth"

wget -O "realesr-animevideov3.pth" \
  "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.5.0/realesr-animevideov3.pth"

wget -O "4x-Nomos8k-span-otf-medium.pth" \
  "https://github.com/the-database/spandrel-models/releases/download/4x-Nomos8k-span-otf-medium/4x-Nomos8k-span-otf-medium.pth"

cd ..
```

---

## 3 — Convert to PyTorch Mobile format (`.pt`)

The script `convert_models.py` at the project root:

1. Loads each `.pth` via **spandrel** (handles architecture auto-detection).
2. Traces the model with `torch.jit.trace` on a 256 × 256 RGB input.
3. Runs `optimize_for_mobile()` — removes CPU-only ops, fuses batchnorm, etc.
4. Saves the result as an ARM64-friendly `.pt` file in `models/mobile/`.
5. Verifies the output by running inference at several different resolutions.

```bash
# From the project root, with .venv active:
python convert_models.py
```

Expected output (sizes are approximate):

```
Converting: RealESR General 4x
  Architecture: SRVGGNetCompact
  Scale: 4
  Tracing with input [1, 3, 256, 256]...
  Optimizing for mobile...
  Saved: model_realesr_general_4x.pt (4.7 MB)
  Verifying with multiple input sizes...
    [64x64] -> [256x256]  OK
    [128x192] -> [512x768]  OK
    [256x256] -> [1024x1024]  OK

... (repeated for each model)

=== All conversions complete ===
  model_animevideo_v3_4x.pt: 2.4 MB
  model_nomos8k_span_4x.pt: 1.6 MB
  model_purephoto_span_4x.pt: 1.6 MB
  model_realesr_general_4x.pt: 4.7 MB
  model_rybu_compact_4x.pt: 2.4 MB
```

---

## 4 — Copy converted models into the app assets

After conversion copy the `.pt` files to `app/src/main/assets/`:

```bash
cp models/mobile/*.pt app/src/main/assets/
```

These files are listed in `.gitignore` and must be present before building the
app — the build will succeed without them, but the app will crash at launch if
any model is missing.

---

## 5 — (Optional) Test models on desktop before installing on device

`test_model.py` lets you run any model against a local image using your GPU or
CPU, verifying the conversion is correct before deploying:

```bash
# Put a test image in test_images/  (JPEG / PNG)
python test_model.py
```

Output images are written to `output_images/` with the model name appended to
the filename.

---

## 6 — Architecture notes

### Why TorchScript + optimize_for_mobile?

| Property | Benefit |
|----------|---------|
| TorchScript `.pt` | No Python at runtime; loads directly in PyTorch Mobile JNI |
| `optimize_for_mobile()` | Removes Python-only ops, enables PackedSequences, faster cold start |
| Compact / SPAN only | Pure convolution — `torch.jit.trace` works without dynamic control flow |
| ARM64 + ARMv7 ABI filters | Minimal APK size; x86 emulators still run via software fallback |

### Why are `.ptl` files in `models/mobile/`?

The `models/mobile/` directory may also contain `.ptl` (PyTorch Lite
Interpreter) files produced during earlier experiments.  These are **not** used
by the current Android app — the full PyTorch Mobile runtime (`.pt`) is used
instead, which supports Vulkan GPU acceleration on supported Adreno / Mali
devices.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| `spandrel.UnsupportedModelError` | `.pth` file is corrupted or wrong URL | Re-download the model |
| `torch.jit.TracerWarning` | Model has data-dependent branches | Use `torch.jit.script` (not needed for current models) |
| App crashes with `FileNotFoundException` | `.pt` not in assets | Run step 4 above |
| Very slow inference on device | Vulkan not available | Normal on older devices (<= Mali-G57); slowest model ~12 s on Cortex-A55 |
