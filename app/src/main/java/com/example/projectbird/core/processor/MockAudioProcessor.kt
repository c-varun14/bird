package com.example.projectbird.core.processor

import kotlin.math.absoluteValue
import kotlin.math.roundToInt

class MockAudioProcessor(
    private val detectionThreshold: Float = 0.65f,
) : AudioProcessor {

    override suspend fun process(input: ProcessingInput): DetectionResult {
        val normalizedIntensity = input.averageAmplitude.coerceIn(0f, 1f)
        val seed = stableSeed(input)

        val candidates = candidateSpecies(normalizedIntensity)
        val detections = buildDetections(
            candidates = candidates,
            seed = seed,
            intensity = normalizedIntensity,
        )

        val environment = environmentFor(normalizedIntensity)

        return DetectionResult(
            detectedItems = detections,
            intensity = normalizedIntensity,
            environmentLabel = environment,
        )
    }

    private fun buildDetections(
        candidates: List<String>,
        seed: Int,
        intensity: Float,
    ): List<DetectedItem> {
        if (candidates.isEmpty()) return emptyList()

        val maxCount = when {
            intensity >= 0.75f -> 3
            intensity >= 0.50f -> 2
            intensity >= 0.30f -> 1
            else -> 0
        }

        if (maxCount == 0) return emptyList()

        val startIndex = seed.absoluteValue % candidates.size
        val selected = (0 until maxCount)
            .map { offset -> candidates[(startIndex + offset) % candidates.size] }
            .distinct()

        return selected.mapIndexed { index, name ->
            val confidence = confidenceFor(
                seed = seed,
                intensity = intensity,
                rank = index,
            )
            DetectedItem(
                entityName = name,
                confidence = confidence,
            )
        }.sortedByDescending { it.confidence }
    }

    private fun confidenceFor(
        seed: Int,
        intensity: Float,
        rank: Int,
    ): Float {
        val jitter = (((seed + (rank * 37)) % 100).absoluteValue / 100f) * 0.08f
        val rankPenalty = rank * 0.06f
        val base = 0.52f + (intensity * 0.40f)
        return (base + jitter - rankPenalty).coerceIn(0.50f, 0.97f)
    }

    private fun candidateSpecies(intensity: Float): List<String> {
        return when {
            intensity >= 0.70f -> listOf("Crow", "Parakeet", "Myna", "Koel", "Pigeon")
            intensity >= 0.45f -> listOf("Sparrow", "Myna", "Pigeon", "Bulbul", "Robin")
            intensity >= 0.20f -> listOf("Sparrow", "Pigeon", "Dove")
            else -> emptyList()
        }
    }

    private fun environmentFor(intensity: Float): EnvironmentLabel {
        return when {
            intensity >= 0.75f -> EnvironmentLabel.NOISY
            intensity >= 0.50f -> EnvironmentLabel.ACTIVE
            intensity >= 0.25f -> EnvironmentLabel.MODERATE
            else -> EnvironmentLabel.QUIET
        }
    }

    private fun stableSeed(input: ProcessingInput): Int {
        val coarseTime = (input.timestampMillis / 2_000L).toInt()
        val latPart = ((input.latitude ?: 0.0) * 10_000.0).roundToInt()
        val lonPart = ((input.longitude ?: 0.0) * 10_000.0).roundToInt()
        val confidenceSeed = (detectionThreshold * 100f).roundToInt()
        return coarseTime xor latPart xor (lonPart shl 1) xor confidenceSeed
    }
}
