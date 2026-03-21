# v7.1.0 - TorchVision NMS Bypass & Critical Fixes

## Critical Fixes
- **NMS Crash Resolved:** Explicitly bypassed missing C++ JNI `torchvision::nms` op boundaries by dynamically patching TorchVision with a native pure `torch` math Python NMS proxy prior to the `optimize_for_mobile` trace generation. Prevents fatal crashes originating during `com.facebook.jni.CppException` in standard setups.
- **Removed Deprecated Libraries:** Stripped non-usable `torchvision_ops` native imports inside Android manifest that caused conflicts and bloating. The object detector model natively bundles all suppression matrix boundaries effectively reducing size.

## Refinements & Core Optimization
- Cleaned up manual JNI injections and `System.loadLibrary` dependencies that proved irrelevant.
- Memory map preserved for fast pure ML analysis inference passes. 

Enjoy robust Object Detection running with 0 unhandled native bindings!
