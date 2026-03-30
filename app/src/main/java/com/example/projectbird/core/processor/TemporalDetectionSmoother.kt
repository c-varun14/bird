package com.example.projectbird.core.processor

import java.util.ArrayDeque

class TemporalDetectionSmoother(
    private val historySize: Int = 3,
    private val minFramesPresent: Int = 2,
    private val maxOutputSize: Int = 6,
    private val enterConfidenceThreshold: Float = 0.24f,
    private val exitConfidenceThreshold: Float = 0.16f,
    private val stabilityBoostPerFrame: Float = 0.03f,
    private val immediateAcceptanceThreshold: Float = 0.72f,
    private val secondaryHoldFrames: Int = 2,
    private val dominantSuppressionMargin: Float = 0.33f,
) {

    private val history = ArrayDeque<List<DetectedItem>>()
    private val activeSpecies = mutableSetOf<String>()
    private val absentFrames = mutableMapOf<String, Int>()

    fun smooth(rawDetections: List<DetectedItem>): List<DetectedItem> {
        if (history.size >= historySize) {
            history.removeFirst()
        }
        history.addLast(rawDetections)

        if (history.isEmpty()) return emptyList()

        val presenceCount = mutableMapOf<String, Int>()
        val weightedConfidence = mutableMapOf<String, Float>()
        val weightSum = mutableMapOf<String, Float>()
        val currentFrameConfidence = mutableMapOf<String, Float>()
        val latestFrameIndex = history.size - 1

        history.forEachIndexed { index, frame ->
            val recencyWeight = ((index + 1).toFloat() / history.size.toFloat()).coerceAtLeast(0.2f)
            frame.forEach { item ->
                presenceCount[item.entityName] = (presenceCount[item.entityName] ?: 0) + 1
                weightedConfidence[item.entityName] =
                    (weightedConfidence[item.entityName] ?: 0f) + (item.confidence * recencyWeight)
                weightSum[item.entityName] = (weightSum[item.entityName] ?: 0f) + recencyWeight
                if (index == latestFrameIndex) {
                    currentFrameConfidence[item.entityName] = item.confidence
                }
            }
        }

        val dominantConfidence = currentFrameConfidence.values.maxOrNull() ?: 0f
        val suppressionFloor = (dominantConfidence - dominantSuppressionMargin).coerceIn(0f, 1f)

        val smoothed = presenceCount.entries
            .asSequence()
            .filter {
                it.value >= minFramesPresent ||
                    (currentFrameConfidence[it.key] ?: 0f) >= immediateAcceptanceThreshold
            }
            .map { (name, count) ->
                val averaged = (weightedConfidence[name] ?: 0f) / (weightSum[name] ?: 1f)
                val stabilityBoost = (count - 1) * stabilityBoostPerFrame
                val boosted = (averaged + stabilityBoost).coerceIn(0f, 1f)
                val threshold = if (activeSpecies.contains(name)) {
                    exitConfidenceThreshold
                } else {
                    enterConfidenceThreshold
                }
                val current = currentFrameConfidence[name] ?: 0f
                val suppressedByDominant =
                    dominantConfidence >= enterConfidenceThreshold &&
                        current > 0f &&
                        current < suppressionFloor

                if (boosted < threshold || suppressedByDominant) {
                    null
                } else {
                    DetectedItem(
                        entityName = name,
                        confidence = boosted,
                    )
                }
            }
            .filterNotNull()
            .sortedByDescending { it.confidence }
            .take(maxOutputSize)
            .toList()

        val heldSpecies = mutableListOf<DetectedItem>()
        val existingNames = smoothed.map { it.entityName }.toMutableSet()
        for (name in activeSpecies) {
            if (existingNames.contains(name)) {
                absentFrames.remove(name)
                continue
            }

            val missingCount = (absentFrames[name] ?: 0) + 1
            absentFrames[name] = missingCount
            val current = currentFrameConfidence[name] ?: 0f
            if (missingCount <= secondaryHoldFrames && current >= exitConfidenceThreshold) {
                heldSpecies += DetectedItem(
                    entityName = name,
                    confidence = current,
                )
                existingNames.add(name)
            }
        }

        val combined = (smoothed + heldSpecies)
            .sortedByDescending { it.confidence }
            .take(maxOutputSize)
            .toList()

        activeSpecies.clear()
        activeSpecies.addAll(combined.map { it.entityName })
        val activeSnapshot = activeSpecies.toSet()
        absentFrames.keys.retainAll(activeSnapshot)

        return combined
    }

    fun reset() {
        history.clear()
        activeSpecies.clear()
        absentFrames.clear()
    }
}
