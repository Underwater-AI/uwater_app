package com.underwaterai.enhance.model

/**
 * All models use Compact (SRVGGNetCompact) or SPAN architectures — pure CNNs
 * that are fully compatible with TorchScript tracing and fast on mobile ARM64.
 *
 * [maxInputSide] is 640 for all models since they are lightweight enough
 * that model + tensors + bitmaps fit well within a 256 MB largeHeap.
 * Output: 640 * 4 = 2560px on the longest side.
 */
enum class ModelType(
    val displayName: String,
    val fileName: String,
    val description: String,
    val bestFor: String,
    val scaleFactor: Int,
    val maxInputSide: Int
) {
    MODEL_1(
        displayName = "RealESR General",
        fileName = "model_realesr_general_4x.pt",
        description = "Compact architecture \u2014 Fast general-purpose super-resolution",
        bestFor = "Best for: General use, quick enhancements",
        scaleFactor = 4,
        maxInputSide = 640
    ),
    MODEL_2(
        displayName = "PurePhoto SPAN",
        fileName = "model_purephoto_span_4x.pt",
        description = "SPAN architecture \u2014 Natural color fidelity with balanced detail",
        bestFor = "Best for: Underwater photography, natural colors",
        scaleFactor = 4,
        maxInputSide = 640
    ),
    MODEL_3(
        displayName = "Rybu Realistic",
        fileName = "model_rybu_compact_4x.pt",
        description = "Compact architecture \u2014 High-quality realistic photo upscaling",
        bestFor = "Best for: Realistic photos, landscapes, high detail",
        scaleFactor = 4,
        maxInputSide = 640
    ),
    MODEL_4(
        displayName = "AnimeVideo v3",
        fileName = "model_animevideo_v3_4x.pt",
        description = "Compact architecture \u2014 Smooth upscaling with clean output",
        bestFor = "Best for: Illustrations, clean art, video frames",
        scaleFactor = 4,
        maxInputSide = 640
    ),
    MODEL_5(
        displayName = "Nomos8k SPAN",
        fileName = "model_nomos8k_span_4x.pt",
        description = "SPAN architecture \u2014 Handles noise, blur and compression artifacts",
        bestFor = "Best for: Degraded photos, noisy/compressed images",
        scaleFactor = 4,
        maxInputSide = 640
    );
}
