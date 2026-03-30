package com.example.projectbird.core.processor

import kotlin.math.ln

data class OverlapSignal(
    val likelyOverlap: Boolean,
    val polyphonyScore: Float,
    val topTwoMargin: Float,
    val normalizedEntropy: Float,
)

object OverlapAnalyzer {

    fun analyze(
        detections: List<DetectedItem>,
        strongThreshold: Float,
        triggerPolyphonyScore: Float,
        triggerEntropy: Float,
        triggerTop2Margin: Float,
    ): OverlapSignal {
        if (detections.isEmpty()) {
            return OverlapSignal(
                likelyOverlap = false,
                polyphonyScore = 0f,
                topTwoMargin = 1f,
                normalizedEntropy = 0f,
            )
        }

        val sorted = detections.sortedByDescending { it.confidence }
        val top = sorted.first().confidence.coerceIn(0f, 1f)
        val second = sorted.getOrNull(1)?.confidence?.coerceIn(0f, 1f) ?: 0f
        val margin = (top - second).coerceIn(0f, 1f)

        val strongCount = sorted.count { it.confidence >= strongThreshold.coerceIn(0.05f, 0.9f) }
        val polyphonyScore = (strongCount / 3f).coerceIn(0f, 1f)

        val confidenceSum = sorted.sumOf { it.confidence.toDouble() }.toFloat().coerceAtLeast(1e-6f)
        val probabilities = sorted.map { (it.confidence / confidenceSum).coerceIn(1e-6f, 1f) }
        val entropy = -probabilities.sumOf { p -> (p * ln(p)).toDouble() }.toFloat()
        val maxEntropy = ln(probabilities.size.toFloat().coerceAtLeast(1f))
        val normalizedEntropy = if (maxEntropy <= 1e-6f) 0f else (entropy / maxEntropy).coerceIn(0f, 1f)

        val likelyOverlap =
            polyphonyScore >= triggerPolyphonyScore.coerceIn(0f, 1f) ||
                (normalizedEntropy >= triggerEntropy.coerceIn(0f, 1f) &&
                    margin <= triggerTop2Margin.coerceIn(0f, 1f))

        return OverlapSignal(
            likelyOverlap = likelyOverlap,
            polyphonyScore = polyphonyScore,
            topTwoMargin = margin,
            normalizedEntropy = normalizedEntropy,
        )
    }
}
