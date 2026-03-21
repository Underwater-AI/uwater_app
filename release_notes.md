# v7.4.0 - PyTorch Threading & Memory Profiling Patch

## Critical Fixes
- **`libpytorch_jni.so` Threading Segfault Fix:** Deep cleaned `__pthread_start` JVM memory pool overlapping on big.LITTLE architectures (common in POCO & Xiaomi devices). Stripped `PyTorchAndroid.setNumThreads` to map strictly to PyTorch's native XNNPACK architecture limits dynamically without manual core overwriting, resolving total app lockups out-of-bounds.
- **Model Loading Stabilization:** Ensures `ObjectDetector`, `ImageEnhancer`, and `ImageClassifier` safely parse Tensors in optimized memory queues without causing native segmentation fault drops.

App is now completely stable inside analysis loops!
