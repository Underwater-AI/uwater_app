# v7.2.0 - TorchVision Input Dimensionality Patched

## Critical Fixes
- **SSD Tensor Bypass:** By default, Pytorch Android's `TensorImageUtils.bitmapToFloat32Tensor()` serializes standard inputs into strictly `[1, C, H, W]` shape batches. However, TorchVision's pure Object Detection pipeline script natively throws `builtins.ValueError` when scanning for sizes not mapped equally to `[C, H, W]`. 
- **Array Memory Slice:** Explicitly resolved the 3D-shape bounds by bypassing the underlying standard PyTorch Android Tensor image loader, slicing off the native 1D head element inside the object detector memory footprint.

App is now completely stable directly inside the bounding evaluation hooks on custom object detection models!
