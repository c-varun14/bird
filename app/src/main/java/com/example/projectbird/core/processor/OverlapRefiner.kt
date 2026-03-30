package com.example.projectbird.core.processor

interface OverlapRefiner {
    fun refine(
        detections: List<DetectedItem>,
        overlapSignal: OverlapSignal,
        maxOutputSize: Int,
    ): List<DetectedItem>
}

class HeuristicOverlapRefiner : OverlapRefiner {
    override fun refine(
        detections: List<DetectedItem>,
        overlapSignal: OverlapSignal,
        maxOutputSize: Int,
    ): List<DetectedItem> {
        if (detections.isEmpty()) return detections
        if (!overlapSignal.likelyOverlap) {
            return detections.sortedByDescending { it.confidence }.take(maxOutputSize)
        }

        val sorted = detections.sortedByDescending { it.confidence }
        val dominant = sorted.first().confidence.coerceIn(0f, 1f)
        val suppression = (0.06f * overlapSignal.normalizedEntropy).coerceIn(0f, 0.08f)

        return sorted
            .mapIndexed { index, item ->
                if (index == 0) {
                    item
                } else {
                    val reduced = (item.confidence - suppression)
                        .coerceAtLeast(0f)
                        .coerceAtMost(dominant)
                    item.copy(confidence = reduced)
                }
            }
            .sortedByDescending { it.confidence }
            .take(maxOutputSize)
    }
}
