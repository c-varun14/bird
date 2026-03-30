package com.example.projectbird.core.processor

object BirdNetRuntimeConfig {
    const val SAMPLE_RATE_HZ = 48_000
    const val WINDOW_DURATION_MS = 3_000L
    const val HOP_DURATION_MS = 1_000L
    const val WINDOW_SIZE_SAMPLES = 144_000
    const val HOP_SIZE_SAMPLES = 48_000

    const val DETECTION_THRESHOLD = 0.24f
    const val TOP_K = 8
    const val ADAPTIVE_TOP_K = 5

    const val MULTI_OFFSET_ENABLED = true
    val MULTI_OFFSET_SAMPLES = intArrayOf(0, 2_400, 4_800)
    const val OVERLAP_MIN_OFFSETS_PRESENT = 2
    const val OVERLAP_STRONG_SINGLE_THRESHOLD = 0.85f
    const val OVERLAP_FUSION_MAX_WEIGHT = 0.70f

    const val HPF_ALPHA = 0.985f
    const val NOISE_GATE_FACTOR = 1.25f
    const val TARGET_RMS = 0.12f
    const val MAX_GAIN = 8f

    const val SMOOTHING_WINDOW_COUNT = 4
    const val SMOOTHING_MIN_PRESENCE = 2
    const val SMOOTHING_ENTER_THRESHOLD = 0.28f
    const val SMOOTHING_EXIT_THRESHOLD = 0.20f
    const val SMOOTHING_IMMEDIATE_ACCEPTANCE_THRESHOLD = 0.76f
    const val SMOOTHING_SECONDARY_HOLD_FRAMES = 2
    const val SMOOTHING_DOMINANT_SUPPRESSION_MARGIN = 0.40f

    const val OVERLAP_TRIGGER_POLYPHONY_SCORE = 0.60f
    const val OVERLAP_TRIGGER_ENTROPY = 0.74f
    const val OVERLAP_TRIGGER_TOP2_MARGIN = 0.22f
}
