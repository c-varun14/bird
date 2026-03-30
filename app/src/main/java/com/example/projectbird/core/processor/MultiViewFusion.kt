package com.example.projectbird.core.processor

data class WeightedScores(
    val scores: FloatArray,
    val weight: Float,
)

object MultiViewFusion {
    fun fuse(weightedScores: List<WeightedScores>, outputSize: Int): FloatArray {
        if (weightedScores.isEmpty()) return FloatArray(outputSize)
        if (weightedScores.size == 1) return weightedScores.first().scores.copyOf()

        val fused = FloatArray(outputSize)
        var totalWeight = 0f

        for (entry in weightedScores) {
            val weight = entry.weight.coerceAtLeast(0.05f)
            totalWeight += weight
            val scores = entry.scores
            for (index in 0 until outputSize) {
                val value = scores.getOrNull(index) ?: 0f
                fused[index] += value * weight
            }
        }

        val norm = totalWeight.coerceAtLeast(1e-6f)
        for (index in fused.indices) {
            fused[index] = (fused[index] / norm).coerceIn(0f, 1f)
        }

        return fused
    }
}
