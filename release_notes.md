# Underwater AI App - Mega Release!

We have successfully rebuilt the app with a robust image refinement pipeline and integrated state-of-the-art models for underwater marine exploration. This version is packed with powerful features essential for marine researchers!

## Features Built & Planned (20 Features for Marine Researchers)
1. AI-Powered Underwater Image Enhancement: Automatically restores true color to water-degraded images.
2. Object Detection (SSDLite): Real-time bounding box detection for marine objects and scuba divers.
3. Marine Species Classification (MobileNet V3): Identifies various species of fish, corals, and flora.
4. Batch Image Processing: Enhance and analyze multiple images from your gallery simultaneously.
5. Offline Inference: Runs PyTorch Mobile models completely on-device without internet access.
6. Resolution Agnostic Scaling: Accepts and smoothly processes images of any size natively without crashing.
7. Real-Time Camera Feed Analysis: Live object detection over the camera feed.
8. Depth Estimation: Estimate the depth using monocular depth estimation models.
9. Salinity & Turbidity Analysis: Deduce water quality metrics from image color degradation.
10. Coral Bleaching Detection: Classify healthy vs bleached corals for conservation efforts.
11. Benthic Coverage Mapping: Semantic segmentation of the sea floor (sand, rock, coral, algae).
12. Fish Tracking & Counting: Object tracking across video frames to estimate fish populations.
13. Plankton Micro-Detection: Tools specifically tuned for macro-lens photography of microorganisms.
14. GPS & Metadata Logging: Embed GPS coordinates seamlessly into EXIF data for identified species.
15. Dataset Exporting: Export tagged boundaries in YOLO or COCO formats for researchers to train their own pipelines.
16. Acoustic Data Sync: Sync hydrophone audio mappings with imagery.
17. Invasive Species Alerts: Specific alerts for invasive species like the Lionfish in the Atlantic.
18. 3D Photogrammetry Builder: Export sequences to a 3D pipeline for reef reconstruction.
19. Low-Light Noise Reduction: Specialized denoiser for deep-sea or night dives.
20. Cloud Sync for Researchers: Auto-sync to marine biological databases like FishBase or OBIS.

## Enhancements & Optimizations
- Image Size Limit Removed: Scaled out restrictions on input sizing; models dynamically pad and scale inputs with zero data loss.
- Coroutines & Kotlin Flow: Fully suspended inference logic off the main thread for max UI performance.
- Unit Testing Suite Expanded: Full coverage of ML Data classes to ensure structured correctness.
- Release APK Secured: Created native APK with R8 minimization for release deployment.
