package com.example.projectbird.core.processor

object BirdNetRuntimeConfig {
    const val SAMPLE_RATE_HZ = 48_000
    const val WINDOW_DURATION_MS = 3_000L
    const val HOP_DURATION_MS = 1_000L
    const val WINDOW_SIZE_SAMPLES = 144_000
    const val HOP_SIZE_SAMPLES = 48_000

    const val DETECTION_THRESHOLD = 0.20f
    const val TOP_K = 6

    const val SMOOTHING_WINDOW_COUNT = 4
    const val SMOOTHING_MIN_PRESENCE = 2
    const val SMOOTHING_ENTER_THRESHOLD = 0.24f
    const val SMOOTHING_EXIT_THRESHOLD = 0.16f
}
