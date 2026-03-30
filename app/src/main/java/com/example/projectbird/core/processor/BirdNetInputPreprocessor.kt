package com.example.projectbird.core.processor

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class BirdNetInputPreprocessor(
    private val targetSampleRateHz: Int = 48_000,
    private val preEmphasisAlpha: Float = 0.97f,
    private val highPassAlpha: Float = BirdNetRuntimeConfig.HPF_ALPHA,
    private val noiseGateFactor: Float = BirdNetRuntimeConfig.NOISE_GATE_FACTOR,
    private val targetRms: Float = BirdNetRuntimeConfig.TARGET_RMS,
    private val maxGain: Float = BirdNetRuntimeConfig.MAX_GAIN,
) {

    data class Diagnostics(
        val estimatedNoiseFloor: Float,
        val estimatedSignalRms: Float,
        val estimatedSnrDb: Float,
        val noiseScore: Float,
    )

    data class PreparedInput(
        val modelInput: FloatArray,
        val denoisedInput: FloatArray,
        val bandEmphasizedInput: FloatArray,
        val diagnostics: Diagnostics,
    )

    fun prepare(
        rawSamples: FloatArray,
        sourceSampleRateHz: Int,
        inputShape: IntArray,
        expectedElementCount: Int,
    ): FloatArray {
        return prepareDetailed(
            rawSamples = rawSamples,
            sourceSampleRateHz = sourceSampleRateHz,
            inputShape = inputShape,
            expectedElementCount = expectedElementCount,
        ).modelInput
    }

    fun prepareDetailed(
        rawSamples: FloatArray,
        sourceSampleRateHz: Int,
        inputShape: IntArray,
        expectedElementCount: Int,
    ): PreparedInput {
        if (rawSamples.isEmpty() || expectedElementCount <= 0) {
            val empty = FloatArray(expectedElementCount.coerceAtLeast(0))
            return PreparedInput(
                modelInput = empty,
                denoisedInput = empty,
                bandEmphasizedInput = empty,
                diagnostics = Diagnostics(0f, 0f, 0f, 1f),
            )
        }

        val normalized = normalizeWaveform(rawSamples)
        val resampled = resampleToTargetRate(
            input = normalized,
            sourceRateHz = sourceSampleRateHz,
            targetRateHz = targetSampleRateHz,
        )
        val highPassed = applyHighPassFilter(resampled)
        val diagnostics = computeDiagnostics(highPassed)
        val dynamicNoiseGateFactor = adjustedNoiseGateFactor(diagnostics)
        val dynamicTargetRms = adjustedTargetRms(diagnostics)
        val denoised = applyAdaptiveNoiseGate(highPassed)
        val denoisedAggressive = applyAdaptiveNoiseGate(highPassed, dynamicNoiseGateFactor)
        val loudnessNormalized = normalizeLoudness(denoisedAggressive, dynamicTargetRms)
        val bandEmphasized = applyBirdBandEmphasis(denoised)

        val noBatchShape = removeBatchDimension(inputShape)
        val expectedLength = when {
            noBatchShape.isEmpty() -> expectedElementCount
            noBatchShape.size == 1 -> noBatchShape[0].coerceAtLeast(1)
            else -> expectedElementCount
        }.coerceAtLeast(1)

        return PreparedInput(
            modelInput = fitLength(loudnessNormalized, expectedLength),
            denoisedInput = fitLength(normalizeLoudness(denoised, dynamicTargetRms), expectedLength),
            bandEmphasizedInput = fitLength(normalizeLoudness(bandEmphasized, dynamicTargetRms), expectedLength),
            diagnostics = diagnostics,
        )
    }

    private fun normalizeWaveform(input: FloatArray): FloatArray {
        if (input.isEmpty()) return input

        val mean = input.average().toFloat()
        val centered = FloatArray(input.size)
        for (i in input.indices) {
            centered[i] = input[i] - mean
        }

        val emphasized = FloatArray(centered.size)
        emphasized[0] = centered[0]
        for (i in 1 until centered.size) {
            emphasized[i] = centered[i] - (preEmphasisAlpha * centered[i - 1])
        }

        val peak = emphasized.maxOf { value -> abs(value) }.coerceAtLeast(1e-6f)
        return FloatArray(emphasized.size) { index ->
            (emphasized[index] / peak).coerceIn(-1f, 1f)
        }
    }

    private fun applyHighPassFilter(input: FloatArray): FloatArray {
        if (input.isEmpty()) return input

        val output = FloatArray(input.size)
        output[0] = input[0]

        for (i in 1 until input.size) {
            output[i] = highPassAlpha * (output[i - 1] + input[i] - input[i - 1])
        }

        return output
    }

    private fun applyAdaptiveNoiseGate(input: FloatArray, gateFactor: Float = noiseGateFactor): FloatArray {
        if (input.isEmpty()) return input

        val absValues = input.map { abs(it) }.sorted()
        val medianNoiseFloor = absValues[(absValues.size * 0.5f).toInt().coerceIn(0, absValues.lastIndex)]
        val gateThreshold = (medianNoiseFloor * gateFactor).coerceAtLeast(1e-5f)

        val output = FloatArray(input.size)
        for (i in input.indices) {
            val v = input[i]
            val av = abs(v)
            output[i] = if (av < gateThreshold) {
                val attenuation = (av / gateThreshold).coerceIn(0f, 1f)
                v * attenuation * attenuation
            } else {
                v
            }
        }

        return output
    }

    private fun normalizeLoudness(input: FloatArray, preferredTargetRms: Float = targetRms): FloatArray {
        if (input.isEmpty()) return input

        val rms = sqrt(input.sumOf { (it * it).toDouble() } / input.size.toDouble()).toFloat()
        if (rms <= 1e-6f) return input

        val gain = (preferredTargetRms / rms).coerceIn(1f, maxGain)
        return FloatArray(input.size) { index ->
            (input[index] * gain).coerceIn(-1f, 1f)
        }
    }

    private fun applyBirdBandEmphasis(input: FloatArray): FloatArray {
        if (input.isEmpty()) return input
        val output = FloatArray(input.size)
        output[0] = input[0]
        for (i in 1 until input.size) {
            val hp = input[i] - (0.94f * input[i - 1])
            val lp = 0.92f * output[i - 1] + 0.08f * hp
            output[i] = (0.65f * hp + 0.35f * lp).coerceIn(-1f, 1f)
        }
        return output
    }

    private fun computeDiagnostics(input: FloatArray): Diagnostics {
        if (input.isEmpty()) return Diagnostics(0f, 0f, 0f, 1f)
        val absValues = input.map { abs(it) }.sorted()
        val noiseFloor = absValues[(absValues.size * 0.2f).toInt().coerceIn(0, absValues.lastIndex)]
        val rms = sqrt(input.sumOf { (it * it).toDouble() } / input.size.toDouble()).toFloat().coerceAtLeast(1e-6f)
        val ratio = (rms / noiseFloor.coerceAtLeast(1e-4f)).coerceAtLeast(1f)
        val snrDb = (20f * kotlin.math.log10(ratio)).coerceIn(0f, 40f)
        val noiseScore = (1f - (snrDb / 20f)).coerceIn(0f, 1f)
        return Diagnostics(
            estimatedNoiseFloor = noiseFloor,
            estimatedSignalRms = rms,
            estimatedSnrDb = snrDb,
            noiseScore = noiseScore,
        )
    }

    private fun adjustedNoiseGateFactor(diagnostics: Diagnostics): Float {
        return when {
            diagnostics.estimatedSnrDb < 6f -> (noiseGateFactor * 1.45f)
            diagnostics.estimatedSnrDb < 10f -> (noiseGateFactor * 1.25f)
            else -> noiseGateFactor
        }.coerceIn(1.1f, 2.2f)
    }

    private fun adjustedTargetRms(diagnostics: Diagnostics): Float {
        return when {
            diagnostics.noiseScore > 0.75f -> (targetRms * 0.85f)
            diagnostics.noiseScore > 0.50f -> (targetRms * 0.92f)
            else -> targetRms
        }.coerceIn(0.07f, 0.14f)
    }

    private fun resampleToTargetRate(
        input: FloatArray,
        sourceRateHz: Int,
        targetRateHz: Int,
    ): FloatArray {
        if (input.isEmpty()) return input
        if (sourceRateHz <= 0 || sourceRateHz == targetRateHz) return input

        val outputLength = max(1, (input.size.toLong() * targetRateHz / sourceRateHz).toInt())
        val output = FloatArray(outputLength)
        val scale = (input.size - 1).toFloat() / (outputLength - 1).coerceAtLeast(1)

        for (i in output.indices) {
            val source = i * scale
            val left = source.toInt().coerceIn(0, input.lastIndex)
            val right = (left + 1).coerceIn(0, input.lastIndex)
            val fraction = source - left
            output[i] = input[left] * (1f - fraction) + input[right] * fraction
        }

        return output
    }

    private fun fitLength(input: FloatArray, targetLength: Int): FloatArray {
        if (targetLength <= 0) return FloatArray(0)
        if (input.size == targetLength) return input

        if (input.isEmpty()) return FloatArray(targetLength)

        val output = FloatArray(targetLength)
        if (input.size > targetLength) {
            val stride = input.size.toFloat() / targetLength
            for (i in output.indices) {
                val index = (i * stride).toInt().coerceIn(0, input.lastIndex)
                output[i] = input[index]
            }
            return output
        }

        input.copyInto(output, endIndex = input.size)
        return output
    }

    private fun removeBatchDimension(shape: IntArray): IntArray {
        if (shape.isEmpty()) return shape
        return if (shape[0] == 1 && shape.size > 1) {
            shape.copyOfRange(1, shape.size)
        } else {
            shape
        }
    }
}
