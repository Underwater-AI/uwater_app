"""
Comprehensive test suite for Android TorchScript super-resolution models.

Tests all 9 converted models against test images, measuring:
- Load time
- Inference time per model/image
- Output dimensions (4x upscale verification)
- Numerical stability (NaN/Inf checks)
- Memory usage
- Cross-model quality comparison (PSNR between models)
"""

import sys
import os
import time
import gc
import traceback
import torch
import numpy as np
from PIL import Image
from pathlib import Path
from dataclasses import dataclass, field
from typing import Optional


# ── Configuration ──────────────────────────────────────────────────────────

BASE_DIR = Path("/home/shuvam/codes/underwater-ai/uwater_app")
MODELS_DIR = BASE_DIR / "models" / "mobile"
INPUT_DIR = BASE_DIR / "test_images"
OUTPUT_DIR = BASE_DIR / "output_images"

# All 9 converted TorchScript models
MODELS = [
    # (filename, display_name, arch_type, expected_size_mb_approx)
    ("model_realesr_general_4x.pt",    "RealESR General v3",     "ESRGAN-lite",  4.7),
    ("model_purephoto_span_4x.pt",     "PurePhoto SPAN",         "SPAN",         1.6),
    ("model_rybu_compact_4x.pt",       "Rybu Compact",           "Compact",      2.4),
    ("model_animevideo_v3_4x.pt",      "AnimeVideo v3",          "Compact",      2.4),
    ("model_nomos8k_span_4x.pt",       "Nomos8k SPAN OTF",      "SPAN",         1.6),
    ("model_realesrgan_x4plus_4x.pt",  "RealESRGAN x4plus",     "ESRGAN",      63.9),
    ("model_ultrasharp_4x.pt",         "UltraSharp",             "ESRGAN",      63.9),
    ("model_remacri_4x.pt",            "Remacri",                "ESRGAN",      63.9),
    ("model_bsrgan_4x.pt",            "BSRGAN",                 "ESRGAN",      63.9),
]

SCALE_FACTOR = 4
# Max input size for testing (keep small for CPU speed)
MAX_TEST_SIDE = 128


# ── Data Classes ───────────────────────────────────────────────────────────

@dataclass
class ModelResult:
    name: str
    filename: str
    arch: str
    file_size_mb: float = 0.0
    load_time_s: float = 0.0
    infer_times_s: list = field(default_factory=list)
    input_shapes: list = field(default_factory=list)
    output_shapes: list = field(default_factory=list)
    has_nan: bool = False
    has_inf: bool = False
    scale_correct: bool = True
    error: Optional[str] = None
    peak_mem_mb: float = 0.0

    @property
    def avg_infer_s(self):
        return sum(self.infer_times_s) / len(self.infer_times_s) if self.infer_times_s else 0

    @property
    def passed(self):
        return (self.error is None and not self.has_nan and
                not self.has_inf and self.scale_correct)


# ── Utility Functions ──────────────────────────────────────────────────────

def load_image(path: Path, max_side: int = MAX_TEST_SIDE) -> torch.Tensor:
    """Load image, resize to max_side, ensure dims divisible by 4, return [1,3,H,W] float32."""
    img = Image.open(path).convert("RGB")

    # Resize preserving aspect ratio
    w, h = img.size
    ratio = min(max_side / w, max_side / h)
    if ratio < 1.0:
        new_w = int(w * ratio)
        new_h = int(h * ratio)
        img = img.resize((new_w, new_h), Image.LANCZOS)

    # Align to 4 (matching Android DIM_ALIGNMENT=4)
    w, h = img.size
    aligned_w = (w // 4) * 4
    aligned_h = (h // 4) * 4
    if aligned_w != w or aligned_h != h:
        # Center crop
        cx = (w - aligned_w) // 2
        cy = (h - aligned_h) // 2
        img = img.crop((cx, cy, cx + aligned_w, cy + aligned_h))

    arr = np.array(img).astype(np.float32) / 255.0
    tensor = torch.from_numpy(arr).permute(2, 0, 1).unsqueeze(0)
    return tensor


def save_image(tensor: torch.Tensor, path: Path):
    """Save tensor [1,C,H,W] in [0,1] to image."""
    arr = tensor.squeeze(0).permute(1, 2, 0).clamp(0, 1).detach().numpy()
    arr = (arr * 255 + 0.5).astype(np.uint8)  # Match Android rounding
    Image.fromarray(arr).save(path, quality=95)


def psnr(img1: torch.Tensor, img2: torch.Tensor) -> float:
    """Compute PSNR between two tensors of same shape."""
    mse = ((img1 - img2) ** 2).mean().item()
    if mse < 1e-10:
        return 100.0
    return 10 * np.log10(1.0 / mse)


def get_memory_mb() -> float:
    """Get current process RSS in MB."""
    try:
        import resource
        return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024  # KB -> MB
    except ImportError:
        return 0.0


# ── Core Test Runner ───────────────────────────────────────────────────────

def test_single_model(filename: str, display_name: str, arch: str,
                      expected_mb: float, test_images: list) -> ModelResult:
    """Test a single TorchScript model against all test images."""
    result = ModelResult(name=display_name, filename=filename, arch=arch)
    model_path = MODELS_DIR / filename

    # ── File existence & size ──
    if not model_path.exists():
        result.error = f"File not found: {model_path}"
        return result

    result.file_size_mb = model_path.stat().st_size / (1024 * 1024)

    # ── Load model ──
    gc.collect()
    mem_before = get_memory_mb()

    try:
        t0 = time.perf_counter()
        model = torch.jit.load(str(model_path), map_location="cpu")
        model.eval()
        result.load_time_s = time.perf_counter() - t0
    except Exception as e:
        result.error = f"Load failed: {e}"
        return result

    result.peak_mem_mb = get_memory_mb() - mem_before

    # ── Inference on each test image ──
    for img_path in test_images:
        try:
            img_tensor = load_image(img_path)
            _, c, h, w = img_tensor.shape
            result.input_shapes.append(f"{w}x{h}")

            t0 = time.perf_counter()
            with torch.no_grad():
                output = model(img_tensor)
            infer_time = time.perf_counter() - t0
            result.infer_times_s.append(infer_time)

            _, oc, oh, ow = output.shape
            result.output_shapes.append(f"{ow}x{oh}")

            # Verify 4x scale
            if ow != w * SCALE_FACTOR or oh != h * SCALE_FACTOR:
                result.scale_correct = False

            # NaN / Inf checks
            if torch.isnan(output).any():
                result.has_nan = True
            if torch.isinf(output).any():
                result.has_inf = True

            # Save output
            stem = img_path.stem
            out_name = f"{stem}_{filename.replace('.pt', '')}.png"
            save_image(output, OUTPUT_DIR / out_name)

        except Exception as e:
            result.error = f"Inference failed on {img_path.name}: {e}\n{traceback.format_exc()}"
            break

    # Free model
    del model
    gc.collect()

    return result


def run_torchscript_validation():
    """Validate each .pt file loads correctly as TorchScript."""
    print("\n" + "=" * 70)
    print("PHASE 1: TorchScript Validation")
    print("=" * 70)

    sizes = {}
    for filename, name, arch, expected_mb in MODELS:
        path = MODELS_DIR / filename
        if not path.exists():
            print(f"  SKIP  {name:30s}  (file not found)")
            continue

        size_mb = path.stat().st_size / (1024 * 1024)
        sizes[filename] = size_mb

        try:
            model = torch.jit.load(str(path), map_location="cpu")
            model.eval()

            # Quick forward pass with tiny input
            with torch.no_grad():
                test_in = torch.randn(1, 3, 16, 16)
                test_out = model(test_in)

            expected_out = (1, 3, 64, 64)
            actual_out = tuple(test_out.shape)

            if actual_out == expected_out:
                print(f"  OK    {name:30s}  {size_mb:6.1f} MB  16x16 -> 64x64")
            else:
                print(f"  WARN  {name:30s}  {size_mb:6.1f} MB  16x16 -> {actual_out}")

            del model, test_in, test_out
            gc.collect()

        except Exception as e:
            print(f"  FAIL  {name:30s}  {size_mb:6.1f} MB  Error: {e}")

    total_mb = sum(sizes.values())
    print(f"\n  Total model size: {total_mb:.1f} MB ({len(sizes)} models)")
    return sizes


def run_inference_benchmarks(test_images: list):
    """Run full inference benchmarks on all models."""
    print("\n" + "=" * 70)
    print(f"PHASE 2: Inference Benchmarks ({len(test_images)} images, max {MAX_TEST_SIDE}px)")
    print("=" * 70)

    results = []
    for filename, name, arch, expected_mb in MODELS:
        print(f"\n  Testing: {name} ({arch})...")
        result = test_single_model(filename, name, arch, expected_mb, test_images)
        results.append(result)

        if result.error:
            print(f"    ERROR: {result.error}")
        else:
            print(f"    Load: {result.load_time_s:.2f}s | "
                  f"Avg infer: {result.avg_infer_s:.2f}s | "
                  f"Size: {result.file_size_mb:.1f}MB | "
                  f"Mem: {result.peak_mem_mb:.0f}MB")
            for i, (inp, out, t) in enumerate(zip(
                    result.input_shapes, result.output_shapes, result.infer_times_s)):
                print(f"      [{inp}] -> [{out}]  {t:.2f}s")

    return results


def run_quality_comparison(test_images: list):
    """Compare output quality between models using PSNR."""
    print("\n" + "=" * 70)
    print("PHASE 3: Cross-Model Quality Comparison (PSNR)")
    print("=" * 70)

    if not test_images:
        print("  No test images found, skipping.")
        return

    # Use first test image only for comparison
    ref_image = test_images[0]
    img_tensor = load_image(ref_image)
    print(f"  Reference image: {ref_image.name} ({img_tensor.shape})")

    outputs = {}
    for filename, name, arch, _ in MODELS:
        path = MODELS_DIR / filename
        if not path.exists():
            continue
        try:
            model = torch.jit.load(str(path), map_location="cpu")
            model.eval()
            with torch.no_grad():
                out = model(img_tensor)
            outputs[name] = out
            del model
            gc.collect()
        except Exception:
            continue

    if len(outputs) < 2:
        print("  Not enough models loaded for comparison.")
        return

    # PSNR matrix (each model vs each model)
    names = list(outputs.keys())
    print(f"\n  PSNR matrix (higher = more similar):")
    header = f"  {'':25s}" + "".join(f"{n[:12]:>13s}" for n in names)
    print(header)

    for i, n1 in enumerate(names):
        row = f"  {n1:25s}"
        for j, n2 in enumerate(names):
            if i == j:
                row += f"{'---':>13s}"
            elif j > i:
                # Only compute upper triangle
                p = psnr(outputs[n1], outputs[n2])
                row += f"{p:>12.1f}dB"
            else:
                row += f"{'':>13s}"
        print(row)

    # Identify clusters (high PSNR = very similar output)
    print(f"\n  Similar model pairs (PSNR > 40dB):")
    found = False
    for i, n1 in enumerate(names):
        for j, n2 in enumerate(names):
            if j <= i:
                continue
            p = psnr(outputs[n1], outputs[n2])
            if p > 40:
                print(f"    {n1} <-> {n2}: {p:.1f}dB (very similar)")
                found = True
    if not found:
        print("    None found - all models produce distinct outputs.")


def run_efficiency_analysis(results: list):
    """Analyze efficiency/size tradeoffs and suggest optimizations."""
    print("\n" + "=" * 70)
    print("PHASE 4: Efficiency Analysis & Recommendations")
    print("=" * 70)

    if not results:
        print("  No results to analyze.")
        return

    passed = [r for r in results if r.passed]
    failed = [r for r in results if not r.passed]

    # Sort by efficiency (speed / size ratio)
    for r in passed:
        print(f"\n  {r.name} ({r.arch}):")
        print(f"    File size:     {r.file_size_mb:6.1f} MB")
        print(f"    Load time:     {r.load_time_s:6.2f} s")
        print(f"    Avg inference: {r.avg_infer_s:6.2f} s")
        print(f"    Memory delta:  {r.peak_mem_mb:6.0f} MB")

        # Efficiency score (lower is better: weighted combo of size + speed)
        eff = r.file_size_mb * 0.3 + r.avg_infer_s * 10
        print(f"    Efficiency:    {eff:6.1f} (lower=better)")

    if failed:
        print(f"\n  FAILED MODELS ({len(failed)}):")
        for r in failed:
            reason = []
            if r.error:
                reason.append(f"error={r.error[:80]}")
            if r.has_nan:
                reason.append("NaN output")
            if r.has_inf:
                reason.append("Inf output")
            if not r.scale_correct:
                reason.append("wrong scale")
            print(f"    {r.name}: {', '.join(reason)}")

    # APK size analysis
    total_mb = sum(r.file_size_mb for r in passed)
    compact_mb = sum(r.file_size_mb for r in passed if r.arch in ("Compact", "SPAN", "ESRGAN-lite"))
    esrgan_mb = sum(r.file_size_mb for r in passed if r.arch == "ESRGAN")

    print(f"\n  APK Size Impact:")
    print(f"    Lightweight models (Compact/SPAN): {compact_mb:.1f} MB")
    print(f"    ESRGAN models:                     {esrgan_mb:.1f} MB")
    print(f"    Total:                             {total_mb:.1f} MB")

    if esrgan_mb > 100:
        print(f"\n  WARNING: ESRGAN models add {esrgan_mb:.0f}MB to APK!")
        print(f"    Recommendations:")
        print(f"    1. Use Android App Bundle (AAB) with asset packs for on-demand download")
        print(f"    2. Ship only lightweight models in base APK ({compact_mb:.0f}MB)")
        print(f"    3. Offer ESRGAN models as optional downloads in-app")
        print(f"    4. Consider INT8 quantization (2-4x size reduction)")


# ── Main ───────────────────────────────────────────────────────────────────

def main():
    print("=" * 70)
    print("UwaterAI Model Test Suite")
    print(f"PyTorch: {torch.__version__}")
    print(f"Models dir: {MODELS_DIR}")
    print(f"Test images: {INPUT_DIR}")
    print("=" * 70)

    # Ensure output dir exists
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    # Gather test images
    test_images = sorted([
        p for p in INPUT_DIR.iterdir()
        if p.suffix.lower() in (".jpg", ".jpeg", ".png", ".bmp", ".webp")
    ]) if INPUT_DIR.exists() else []

    if not test_images:
        print(f"\nERROR: No test images found in {INPUT_DIR}")
        sys.exit(1)

    print(f"\nFound {len(test_images)} test images:")
    for p in test_images:
        img = Image.open(p)
        print(f"  {p.name}: {img.size[0]}x{img.size[1]}")

    # Phase 1: Quick validation
    sizes = run_torchscript_validation()

    # Phase 2: Full benchmark
    results = run_inference_benchmarks(test_images)

    # Phase 3: Quality comparison
    run_quality_comparison(test_images)

    # Phase 4: Efficiency analysis
    run_efficiency_analysis(results)

    # ── Final Summary ──
    passed = sum(1 for r in results if r.passed)
    total = len(results)
    print(f"\n{'=' * 70}")
    print(f"FINAL: {passed}/{total} models passed all checks")
    print(f"{'=' * 70}")

    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
