"""Convert OpenModelDB models to optimized TorchScript (.pt) for Android.

Supported architectures:
- SRVGGNetCompact (Real-ESRGAN Compact) — lightweight, fastest on mobile
- SPAN — pure-CNN, fast on mobile ARM64
- RRDBNet (ESRGAN/BSRGAN) — heavier but highest quality, viable on mobile

All are pure-CNN and fully compatible with torch.jit.trace.
"""

import torch
import spandrel
from torch.utils.mobile_optimizer import optimize_for_mobile
from pathlib import Path

BASE_DIR = Path("/home/shuvam/codes/underwater-ai/uwater_app")
MODELS_DIR = BASE_DIR / "models"

# Trace input sized to match actual mobile usage (not 64x64).
# Larger trace inputs make the traced graph more representative.
TRACE_H, TRACE_W = 256, 256

CONVERSIONS = [
    {
        "src": "4x-realesr-general-x4v3.pth",
        "dst": "model_realesr_general_4x.pt",
        "name": "RealESR General 4x",
    },
    {
        "src": "4xPurePhoto-span.pth",
        "dst": "model_purephoto_span_4x.pt",
        "name": "PurePhoto SPAN 4x",
    },
    {
        "src": "4x-Rybu.pth",
        "dst": "model_rybu_compact_4x.pt",
        "name": "Rybu Compact 4x",
    },
    {
        "src": "realesr-animevideov3.pth",
        "dst": "model_animevideo_v3_4x.pt",
        "name": "RealESR AnimeVideo v3 4x",
    },
    {
        "src": "4x-Nomos8k-span-otf-medium.pth",
        "dst": "model_nomos8k_span_4x.pt",
        "name": "Nomos8k SPAN OTF Medium 4x",
    },
    # === New ESRGAN/RRDBNet models (high quality, heavier) ===
    {
        "src": "RealESRGAN_x4plus.pth",
        "dst": "model_realesrgan_x4plus_4x.pt",
        "name": "RealESRGAN x4plus 4x",
    },
    {
        "src": "4x-UltraSharp.pth",
        "dst": "model_ultrasharp_4x.pt",
        "name": "UltraSharp 4x",
    },
    {
        "src": "4x_foolhardy_Remacri.pth",
        "dst": "model_remacri_4x.pt",
        "name": "Remacri 4x",
    },
    {
        "src": "BSRGAN.pth",
        "dst": "model_bsrgan_4x.pt",
        "name": "BSRGAN 4x",
    },
]

def convert_model(src_path: Path, dst_path: Path, name: str):
    print(f"\nConverting: {name}")
    print(f"  Source: {src_path}")

    model_desc = spandrel.ModelLoader().load_from_file(src_path)
    model = model_desc.model
    model.eval()
    model.cpu()

    print(f"  Architecture: {model_desc.architecture}")
    print(f"  Scale: {model_desc.scale}")

    example_input = torch.randn(1, 3, TRACE_H, TRACE_W)

    print(f"  Tracing with input [1, 3, {TRACE_H}, {TRACE_W}]...")
    with torch.no_grad():
        traced = torch.jit.trace(model, example_input)

    print("  Optimizing for mobile...")
    optimized = optimize_for_mobile(traced)

    optimized.save(str(dst_path))
    size_mb = dst_path.stat().st_size / (1024 * 1024)
    print(f"  Saved: {dst_path.name} ({size_mb:.1f} MB)")

    # Verify with a different input size to confirm dynamic shape support
    print("  Verifying with multiple input sizes...")
    loaded = torch.jit.load(str(dst_path))
    for h, w in [(64, 64), (128, 192), (256, 256)]:
        test_in = torch.randn(1, 3, h, w)
        with torch.no_grad():
            test_out = loaded(test_in)
        print(f"    [{h}x{w}] -> [{test_out.shape[2]}x{test_out.shape[3]}]  OK")

    print(f"  SUCCESS")


def main():
    output_dir = MODELS_DIR / "mobile"
    output_dir.mkdir(exist_ok=True)

    for conv in CONVERSIONS:
        src = MODELS_DIR / conv["src"]
        dst = output_dir / conv["dst"]
        if not src.exists():
            print(f"SKIP: {src} not found")
            continue
        convert_model(src, dst, conv["name"])

    print("\n=== All conversions complete ===")
    for f in sorted(output_dir.glob("*.pt")):
        print(f"  {f.name}: {f.stat().st_size / (1024*1024):.1f} MB")


if __name__ == "__main__":
    main()
