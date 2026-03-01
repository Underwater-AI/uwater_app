"""Test photo enhancer models from OpenModelDB using spandrel."""

import sys
import time
import torch
import numpy as np
from PIL import Image
from pathlib import Path

import spandrel

BASE_DIR = Path("/home/shuvam/codes/underwater-ai/underwater-ai.github.io")
MODELS_DIR = BASE_DIR / "models"
INPUT_DIR = BASE_DIR / "test_images"
OUTPUT_DIR = BASE_DIR / "output_images"

def load_image(path: Path) -> torch.Tensor:
    """Load image as float32 tensor in [0,1] range, shape (1, C, H, W)."""
    img = Image.open(path).convert("RGB")
    arr = np.array(img).astype(np.float32) / 255.0
    tensor = torch.from_numpy(arr).permute(2, 0, 1).unsqueeze(0)
    return tensor

def save_image(tensor: torch.Tensor, path: Path):
    """Save tensor (1, C, H, W) in [0,1] range to image file."""
    arr = tensor.squeeze(0).permute(1, 2, 0).clamp(0, 1).detach().numpy()
    arr = (arr * 255).astype(np.uint8)
    Image.fromarray(arr).save(path, quality=95)

def test_model(model_path: Path, input_path: Path, output_path: Path):
    """Load and run a model on an input image."""
    print(f"\n{'='*60}")
    print(f"Model: {model_path.name}")
    print(f"Input: {input_path.name}")
    print(f"{'='*60}")

    # Load model with spandrel
    print("Loading model...")
    t0 = time.time()
    model_desc = spandrel.ModelLoader().load_from_file(model_path)
    load_time = time.time() - t0
    print(f"  Architecture: {model_desc.architecture}")
    print(f"  Scale: {model_desc.scale}")
    print(f"  Input channels: {model_desc.input_channels}")
    print(f"  Output channels: {model_desc.output_channels}")
    print(f"  Load time: {load_time:.2f}s")

    # Get the model and set to eval mode
    model = model_desc.model
    model.eval()

    # Load input image
    img_tensor = load_image(input_path)
    print(f"  Input shape: {img_tensor.shape}")

    # Run inference
    print("Running inference (CPU)...")
    t0 = time.time()
    with torch.no_grad():
        output = model(img_tensor)
    infer_time = time.time() - t0
    print(f"  Output shape: {output.shape}")
    print(f"  Inference time: {infer_time:.2f}s")

    # Save output
    save_image(output, output_path)
    print(f"  Output saved: {output_path.name}")
    print(f"  Output size: {output_path.stat().st_size / 1024:.1f} KB")

    return True


def main():
    input_img = INPUT_DIR / "underwater_test.jpg"

    if not input_img.exists():
        print(f"ERROR: Input image not found: {input_img}")
        sys.exit(1)

    # Print input image info
    img = Image.open(input_img)
    print(f"Input image: {img.size[0]}x{img.size[1]}, mode={img.mode}")

    models = [
        ("4x-realesr-general-x4v3.pth", "realesr_general_4x_output.png"),
        ("4xPurePhoto-span.pth", "purephoto_span_4x_output.png"),
    ]

    results = {}
    for model_name, output_name in models:
        model_path = MODELS_DIR / model_name
        output_path = OUTPUT_DIR / output_name

        if not model_path.exists():
            print(f"SKIP: Model not found: {model_path}")
            continue

        try:
            success = test_model(model_path, input_img, output_path)
            results[model_name] = "SUCCESS" if success else "FAILED"
        except Exception as e:
            print(f"  ERROR: {e}")
            results[model_name] = f"ERROR: {e}"

    # Summary
    print(f"\n{'='*60}")
    print("SUMMARY")
    print(f"{'='*60}")
    for model_name, status in results.items():
        print(f"  {model_name}: {status}")


if __name__ == "__main__":
    main()
