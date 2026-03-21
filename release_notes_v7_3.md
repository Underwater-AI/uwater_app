# v7.3.0 - PyTorch Native JNI Segfault & Threading Patches

## Critical Fixes
- **`libpytorch_jni.so` Threading Segfault Fix:** Addressed a critical crash originating within `libc.so` (`__pthread_start`) on heavy Android models (like POCO onyx). We observed that aggressively mapping `org.pytorch.PyTorchAndroid.setNumThreads()` concurrently and repeatedly with `Runtime.getRuntime().availableProcessors()` on big.LITTLE architectures destroyed PyTorch's native XNNPACK thread pool boundaries. The backend is now fully stable, allowing dynamic core scaling via standard QNN limits without segfaulting thread generation.
- **Deep Cleaned Native Concurrency:** Both `ObjectDetector` and `ImageClassifier` native inferences now initialize strictly bounded thread memory without overwriting global contexts aggressively on each viewmodel instantiation.

Enjoy fully stable inference across all 3 AI models concurrently!
