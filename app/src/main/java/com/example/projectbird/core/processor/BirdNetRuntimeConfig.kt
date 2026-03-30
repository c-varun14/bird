package com.example.projectbird.core.processor

object BirdNetRuntimeConfig {
    const val SAMPLE_RATE_HZ = 48_000
    const val WINDOW_DURATION_MS = 3_000L
    const val HOP_DURATION_MS = 1_000L
    const val WINDOW_SIZE_SAMPLES = 144_000
    const val HOP_SIZE_SAMPLES = 48_000

    const val DETECTION_THRESHOLD = 0.20f
    const val TOP_K = 6

    const val MULTI_OFFSET_ENABLED = true
    val MULTI_OFFSET_SAMPLES = intArrayOf(0, 2_400, 4_800)

    const val HPF_ALPHA = 0.985f
    const val NOISE_GATE_FACTOR = 1.25f
    const val TARGET_RMS = 0.12f
    const val MAX_GAIN = 8f

    const val SMOOTHING_WINDOW_COUNT = 4
    const val SMOOTHING_MIN_PRESENCE = 2
    const val SMOOTHING_ENTER_THRESHOLD = 0.24f
    const val SMOOTHING_EXIT_THRESHOLD = 0.16f
}
