package com.example.projectbird.core.processor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

class BirdNetTfliteAudioProcessor(
    context: Context,
    private val threshold: Float = 0.20f,
    private val topK: Int = 6,
    private val fallbackProcessor: AudioProcessor = MockAudioProcessor(detectionThreshold = threshold),
) : AudioProcessor {

    private val appContext = context.applicationContext

    @Volatile
    private var initialized = false

    @Volatile
    private var useFallback = false

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private val preprocessor = BirdNetInputPreprocessor(targetSampleRateHz = MODEL_SAMPLE_RATE_HZ)

    private var inputTensorShape: IntArray = intArrayOf(1, DEFAULT_INPUT_SAMPLES)
    private var expectedInputElementCount: Int = DEFAULT_INPUT_SAMPLES
    private var outputElementCount: Int = 1
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: Array<FloatArray>? = null
    private var consecutiveInferenceFailures: Int = 0
    private var lastInferenceError: String = "unknown"
    @Volatile
    private var fallbackReason: String = "BirdNET unavailable"

    override suspend fun process(input: ProcessingInput): DetectionResult = withContext(Dispatchers.Default) {
        ensureInitialized()

        if (useFallback) {
            return@withContext fallbackWithWarning(input)
        }

        val localInterpreter = interpreter ?: return@withContext fallbackWithWarning(input)
        val samples = input.pcmSamples ?: return@withContext fallbackWithWarning(input)

        val sourceRate = input.sampleRateHz ?: MODEL_SAMPLE_RATE_HZ
        val modelInput = preprocessor.prepare(
            rawSamples = samples,
            sourceSampleRateHz = sourceRate,
            inputShape = inputTensorShape,
            expectedElementCount = expectedInputElementCount,
        )

        val offsets = if (BirdNetRuntimeConfig.MULTI_OFFSET_ENABLED) {
            BirdNetRuntimeConfig.MULTI_OFFSET_SAMPLES.asList()
        } else {
            listOf(0)
        }

        val scoreFrames = mutableListOf<FloatArray>()
        for (offset in offsets) {
            val shifted = shiftWindow(modelInput, offset)
            val scores = runSingleInference(localInterpreter, shifted)
            if (scores != null) {
                scoreFrames += scores
            }
        }

        if (scoreFrames.isEmpty()) {
            consecutiveInferenceFailures += 1
            if (consecutiveInferenceFailures >= MAX_CONSECUTIVE_FAILURES) {
                useFallback = true
                fallbackReason = "BirdNET inference failed repeatedly: $lastInferenceError"
            }
            return@withContext fallbackWithWarning(input)
        }

        consecutiveInferenceFailures = 0
        val scores = fuseScoreFrames(scoreFrames)

        val detections = mutableListOf<DetectedItem>()
        for (index in 0 until outputElementCount) {
            val confidence = scores[index]
            val name = labels.getOrElse(index) { "Bird #$index" }
            val classThreshold = thresholdForLabel(name, threshold)
            if (confidence < classThreshold) continue
            detections += DetectedItem(name, confidence.coerceIn(0f, 1f))
        }

        val prunedDetections = detections
            .sortedByDescending { it.confidence }
            .take(topK)

        val intensity = computeIntensity(modelInput)

        DetectionResult(
            detectedItems = prunedDetections,
            intensity = intensity,
            environmentLabel = environmentFromIntensity(intensity),
            processorMode = ProcessorMode.BIRDNET,
        )
    }

    private fun runSingleInference(localInterpreter: Interpreter, modelInput: FloatArray): FloatArray? {
        val reusableInputBuffer = inputBuffer ?: return null
        val reusableOutputBuffer = outputBuffer ?: return null

        reusableInputBuffer.clear()
        modelInput.forEach { value -> reusableInputBuffer.putFloat(value.coerceIn(-1f, 1f)) }
        reusableInputBuffer.rewind()

        reusableOutputBuffer[0].fill(0f)

        return runCatching {
            localInterpreter.run(reusableInputBuffer, reusableOutputBuffer)
            calibrateScores(reusableOutputBuffer[0]).copyOf()
        }.onFailure {
            lastInferenceError = it.message ?: it.javaClass.simpleName
        }.getOrNull()
    }

    private fun fuseScoreFrames(frames: List<FloatArray>): FloatArray {
        if (frames.isEmpty()) return FloatArray(outputElementCount)
        if (frames.size == 1) return frames.first()

        val output = FloatArray(outputElementCount)
        for (i in 0 until outputElementCount) {
            var weightedSum = 0f
            var weightTotal = 0f
            for (frameIndex in frames.indices) {
                val weight = 1f + (frameIndex * 0.1f)
                weightedSum += frames[frameIndex][i] * weight
                weightTotal += weight
            }
            output[i] = (weightedSum / weightTotal.coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
        }
        return output
    }

    private fun shiftWindow(input: FloatArray, shiftSamples: Int): FloatArray {
        if (shiftSamples == 0 || input.isEmpty()) return input

        val output = FloatArray(input.size)
        for (i in output.indices) {
            val sourceIndex = (i + shiftSamples).coerceIn(0, input.lastIndex)
            output[i] = input[sourceIndex]
        }
        return output
    }

    private fun thresholdForLabel(label: String, baseThreshold: Float): Float {
        val lower = label.lowercase()
        val adjustment = when {
            "sparrow" in lower || "warbler" in lower || "sunbird" in lower -> -0.03f
            "owl" in lower || "nightjar" in lower -> -0.02f
            "pigeon" in lower || "crow" in lower || "dove" in lower -> 0.03f
            else -> 0f
        }
        return (baseThreshold + adjustment).coerceIn(0.08f, 0.65f)
    }

    @Synchronized
    private fun ensureInitialized() {
        if (initialized) return

        runCatching {
            labels = loadLabelsFromAssets(appContext, LABELS_ASSET_PATH)
            if (labels.isEmpty()) {
                fallbackReason = "BirdNET labels file is empty"
                useFallback = true
                initialized = true
                return
            }

            val options = Interpreter.Options().apply {
                numThreads = DEFAULT_NUM_THREADS
            }

            interpreter = Interpreter(loadModelFromAssets(appContext, MODEL_ASSET_PATH), options).also { loaded ->
                val tensor = loaded.getInputTensor(0)
                if (tensor.dataType() != DataType.FLOAT32) {
                    fallbackReason = "BirdNET input tensor type is not FLOAT32"
                    useFallback = true
                    return@also
                }

                inputTensorShape = tensor.shape()
                expectedInputElementCount = elementCountWithoutBatch(inputTensorShape)
                    .coerceAtLeast(1)

                val outputTensor = loaded.getOutputTensor(0)
                if (outputTensor.dataType() != DataType.FLOAT32) {
                    fallbackReason = "BirdNET output tensor type is not FLOAT32"
                    useFallback = true
                    return@also
                }

                outputElementCount = outputTensor.shape().lastOrNull()?.coerceAtLeast(1) ?: 1

                if (!isSupportedWaveformInput(expectedInputElementCount)) {
                    fallbackReason = "BirdNET input shape is incompatible"
                    useFallback = true
                    return@also
                }

                if (labels.size != outputElementCount) {
                    fallbackReason = "BirdNET label count does not match output classes"
                    useFallback = true
                    return@also
                }

                inputBuffer = ByteBuffer
                    .allocateDirect(expectedInputElementCount * FLOAT_BYTES)
                    .order(ByteOrder.nativeOrder())
                outputBuffer = Array(1) { FloatArray(outputElementCount) }
            }
            consecutiveInferenceFailures = 0
            lastInferenceError = "none"
            initialized = true
        }.onFailure {
            useFallback = true
            fallbackReason = "BirdNET model initialization failed: ${it.message ?: "unknown"}"
            initialized = true
        }
    }

    private suspend fun fallbackWithWarning(input: ProcessingInput): DetectionResult {
        val fallback = fallbackProcessor.process(input)
        return fallback.copy(warningMessage = fallbackReason, processorMode = ProcessorMode.FALLBACK)
    }

    private fun environmentFromIntensity(intensity: Float): EnvironmentLabel {
        return when {
            intensity >= 0.75f -> EnvironmentLabel.NOISY
            intensity >= 0.50f -> EnvironmentLabel.ACTIVE
            intensity >= 0.25f -> EnvironmentLabel.MODERATE
            else -> EnvironmentLabel.QUIET
        }
    }

    private fun computeIntensity(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        val rms = sqrt(samples.sumOf { value -> (value * value).toDouble() } / samples.size.toDouble())
        val normalized = (rms * 3.0).toFloat()
        return normalized.coerceIn(0f, 1f)
    }

    private fun calibrateScores(rawScores: FloatArray): FloatArray {
        if (rawScores.isEmpty()) return rawScores
        val appearsProbabilistic = rawScores.all { it in 0f..1f }
        if (appearsProbabilistic) return rawScores

        return FloatArray(rawScores.size) { index ->
            sigmoid(rawScores[index])
        }
    }

    private fun sigmoid(value: Float): Float {
        val clamped = value.coerceIn(-20f, 20f)
        return (1.0 / (1.0 + exp(-clamped.toDouble()))).toFloat()
    }

    private fun elementCountWithoutBatch(shape: IntArray): Int {
        if (shape.isEmpty()) return DEFAULT_INPUT_SAMPLES
        val dims = if (shape[0] == 1 && shape.size > 1) {
            shape.copyOfRange(1, shape.size)
        } else {
            shape
        }
        return dims.fold(1) { acc, dim ->
            val safe = dim.coerceAtLeast(1)
            acc * safe
        }
    }

    private fun loadModelFromAssets(context: Context, assetPath: String): ByteBuffer {
        context.assets.openFd(assetPath).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                return channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength,
                )
            }
        }
    }

    private fun loadLabelsFromAssets(context: Context, assetPath: String): List<String> {
        return context.assets.open(assetPath).bufferedReader().useLines { lines ->
            lines.map { line -> line.trim() }
                .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
                .toList()
        }
    }

    companion object {
        private const val MODEL_ASSET_PATH = "models/birdnet.tflite"
        private const val LABELS_ASSET_PATH = "models/birdnet_labels.txt"
        private const val FLOAT_BYTES = 4
        private const val DEFAULT_INPUT_SAMPLES = 144_000
        private const val MODEL_SAMPLE_RATE_HZ = 48_000
        private const val MIN_SUPPORTED_INPUT_SAMPLES = 96_000
        private const val MAX_SUPPORTED_INPUT_SAMPLES = 192_000
        private const val MAX_CONSECUTIVE_FAILURES = 3
        private val DEFAULT_NUM_THREADS = max(1, Runtime.getRuntime().availableProcessors().coerceAtMost(2))

        private fun isSupportedWaveformInput(inputSamples: Int): Boolean {
            return inputSamples in MIN_SUPPORTED_INPUT_SAMPLES..MAX_SUPPORTED_INPUT_SAMPLES
        }
    }
}
