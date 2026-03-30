package com.example.projectbird.core.processor

import java.io.File

data class ProcessingInput(
    val audioFile: File? = null,
    val timestampMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val durationMs: Long,
    val averageAmplitude: Float,
    val pcmSamples: FloatArray? = null,
    val sampleRateHz: Int? = null,
)

data class DetectionResult(
    val detectedItems: List<DetectedItem>,
    val intensity: Float,
    val environmentLabel: EnvironmentLabel,
    val processorMode: ProcessorMode = ProcessorMode.FALLBACK,
    val warningMessage: String? = null,
)

enum class ProcessorMode {
    BIRDNET,
    FALLBACK,
}

data class DetectedItem(
    val entityName: String,
    val confidence: Float,
)

enum class EnvironmentLabel {
    QUIET,
    MODERATE,
    ACTIVE,
    NOISY,
}

enum class RetentionReason {
    DETECTION,
    INTENSITY,
    BOTH,
}

data class MeaningfulnessDecision(
    val isMeaningful: Boolean,
    val retentionReason: RetentionReason? = null,
)

interface AudioProcessor {
    suspend fun process(input: ProcessingInput): DetectionResult
}

interface MeaningfulnessEvaluator {
    fun evaluate(result: DetectionResult): MeaningfulnessDecision
}

class DefaultMeaningfulnessEvaluator(
    private val detectionConfidenceThreshold: Float = 0.65f,
    private val intensityThreshold: Float = 0.60f,
) : MeaningfulnessEvaluator {

    override fun evaluate(result: DetectionResult): MeaningfulnessDecision {
        val hasDetection = result.detectedItems.any { it.confidence >= detectionConfidenceThreshold }
        val hasIntensity = result.intensity >= intensityThreshold

        return when {
            hasDetection && hasIntensity -> MeaningfulnessDecision(
                isMeaningful = true,
                retentionReason = RetentionReason.BOTH,
            )

            hasDetection -> MeaningfulnessDecision(
                isMeaningful = true,
                retentionReason = RetentionReason.DETECTION,
            )

            hasIntensity -> MeaningfulnessDecision(
                isMeaningful = true,
                retentionReason = RetentionReason.INTENSITY,
            )

            else -> MeaningfulnessDecision(
                isMeaningful = false,
                retentionReason = null,
            )
        }
    }
}
