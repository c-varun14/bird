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

    fun prepare(
        rawSamples: FloatArray,
        sourceSampleRateHz: Int,
        inputShape: IntArray,
        expectedElementCount: Int,
    ): FloatArray {
        if (rawSamples.isEmpty() || expectedElementCount <= 0) {
            return FloatArray(expectedElementCount.coerceAtLeast(0))
        }

        val normalized = normalizeWaveform(rawSamples)
        val resampled = resampleToTargetRate(
            input = normalized,
            sourceRateHz = sourceSampleRateHz,
            targetRateHz = targetSampleRateHz,
        )
        val highPassed = applyHighPassFilter(resampled)
        val denoised = applyAdaptiveNoiseGate(highPassed)
        val loudnessNormalized = normalizeLoudness(denoised)

        val noBatchShape = removeBatchDimension(inputShape)
        val expectedLength = when {
            noBatchShape.isEmpty() -> expectedElementCount
            noBatchShape.size == 1 -> noBatchShape[0].coerceAtLeast(1)
            else -> expectedElementCount
        }.coerceAtLeast(1)

        return fitLength(loudnessNormalized, expectedLength)
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

    private fun applyAdaptiveNoiseGate(input: FloatArray): FloatArray {
        if (input.isEmpty()) return input

        val absValues = input.map { abs(it) }.sorted()
        val medianNoiseFloor = absValues[(absValues.size * 0.5f).toInt().coerceIn(0, absValues.lastIndex)]
        val gateThreshold = (medianNoiseFloor * noiseGateFactor).coerceAtLeast(1e-5f)

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

    private fun normalizeLoudness(input: FloatArray): FloatArray {
        if (input.isEmpty()) return input

        val rms = sqrt(input.sumOf { (it * it).toDouble() } / input.size.toDouble()).toFloat()
        if (rms <= 1e-6f) return input

        val gain = (targetRms / rms).coerceIn(1f, maxGain)
        return FloatArray(input.size) { index ->
            (input[index] * gain).coerceIn(-1f, 1f)
        }
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
