# Dynamic Intelligence & UI Visuals: V6.0.0

You demanded deeper analytical visuals and direct optimizations. This release brings massive updates:
- **Visual Detections Overlay**: Object detections are no longer just text; the app dynamically draws high-definition bounding boxes and probability scores directly over your analyzed image inside the result card. 
- **RGB Histogram Plots**: A generated spectral plot graph is now displayed within the Analysis Report. This dynamically calculates the precise RGB color distribution curve directly from pixel arrays to assist in your chlorophyll/depth proxy checking.
- **Hardware Optimization Tracking**: We now fully poll your local system hardware. A new `Hardware Execution Trace` section prints out the active operating frequency of each accessible CPU core running the PyTorch workloads.
- **Removed Hardcoding**: Plankton counting and benthic scaling formulas now respond purely to the math running off the actual model bounding boxes rather than raw heuristic predictions. 
