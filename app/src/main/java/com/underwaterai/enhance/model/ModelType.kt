package com.underwaterai.enhance.model

/**
 * Models use Compact, SPAN, or ESRGAN (RRDBNet) architectures — all pure CNNs
 * fully compatible with TorchScript tracing and optimized for mobile ARM64.
 *
 * Compact/SPAN models (1–6 MB): [maxInputSide] = 640, giving 2560px output.
 * ESRGAN models (≈64 MB): [maxInputSide] = 480, giving 1920px output.
 * The reduced input size for ESRGAN prevents OOM on devices with 256 MB largeHeap.
 */
enum class ModelType(
    val displayName: String,
    val fileName: String,
    val description: String,
    val bestFor: String,
    val scaleFactor: Int,
    val maxInputSide: Int
) {
    // ── Lightweight models (Compact / SPAN) ─────────────────────────────

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
    ),

    // ── Heavy models (ESRGAN / RRDBNet) ─────────────────────────────────

    MODEL_6(
        displayName = "RealESRGAN x4plus",
        fileName = "model_realesrgan_x4plus_4x.pt",
        description = "ESRGAN architecture \u2014 Industry-standard high-fidelity upscaling",
        bestFor = "Best for: General photos, high detail preservation",
        scaleFactor = 4,
        maxInputSide = 480
    ),
    MODEL_7(
        displayName = "UltraSharp",
        fileName = "model_ultrasharp_4x.pt",
        description = "ESRGAN architecture \u2014 Maximum edge sharpness and texture detail",
        bestFor = "Best for: Sharp photos, texture-rich images",
        scaleFactor = 4,
        maxInputSide = 480
    ),
    MODEL_8(
        displayName = "Remacri",
        fileName = "model_remacri_4x.pt",
        description = "ESRGAN architecture \u2014 Photorealistic restoration with natural tones",
        bestFor = "Best for: Portraits, natural scenes, photorealism",
        scaleFactor = 4,
        maxInputSide = 480
    ),
    MODEL_9(
        displayName = "BSRGAN",
        fileName = "model_bsrgan_4x.pt",
        description = "ESRGAN architecture \u2014 Advanced degradation-aware restoration",
        bestFor = "Best for: Heavily degraded, noisy, or compressed images",
        scaleFactor = 4,
        maxInputSide = 480
    );
}
