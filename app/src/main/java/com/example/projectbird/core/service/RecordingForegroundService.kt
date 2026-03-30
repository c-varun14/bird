package com.example.projectbird.core.service

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.example.projectbird.ProjectBirdApplication
import com.example.projectbird.core.audio.AudioRecordStreamRecorder
import com.example.projectbird.core.audio.FloatRingBuffer
import com.example.projectbird.core.audio.StreamingAudioRecorder
import com.example.projectbird.core.audio.WavChunkWriter
import com.example.projectbird.core.location.FusedLocationProvider
import com.example.projectbird.core.processor.AudioProcessor
import com.example.projectbird.core.processor.BirdNetTfliteAudioProcessor
import com.example.projectbird.core.processor.BirdNetRuntimeConfig
import com.example.projectbird.core.processor.DefaultMeaningfulnessEvaluator
import com.example.projectbird.core.processor.MeaningfulnessEvaluator
import com.example.projectbird.core.processor.OverlapAnalyzer
import com.example.projectbird.core.processor.OverlapRefiner
import com.example.projectbird.core.processor.ProcessorMode
import com.example.projectbird.core.processor.ProcessingInput
import com.example.projectbird.core.processor.TemporalDetectionSmoother
import com.example.projectbird.core.processor.HeuristicOverlapRefiner
import com.example.projectbird.core.storage.TempFileManager
import com.example.projectbird.data.local.entity.AnalysisResultEntity
import com.example.projectbird.data.local.entity.CapturePointEntity
import com.example.projectbird.data.local.entity.DetectedEntityEntity
import com.example.projectbird.data.local.entity.EnvironmentLabel
import com.example.projectbird.data.local.entity.RetentionReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.room.withTransaction
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

class RecordingForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null

    private lateinit var streamingAudioRecorder: StreamingAudioRecorder
    private lateinit var wavChunkWriter: WavChunkWriter
    private lateinit var locationProvider: FusedLocationProvider
    private lateinit var tempFileManager: TempFileManager
    private lateinit var sessionStateStore: RecordingSessionStateStore
    private lateinit var audioProcessor: AudioProcessor
    private lateinit var meaningfulnessEvaluator: MeaningfulnessEvaluator
    private val overlapRefiner: OverlapRefiner = HeuristicOverlapRefiner()
    private lateinit var fastDetectionSmoother: TemporalDetectionSmoother
    private lateinit var refinementDetectionSmoother: TemporalDetectionSmoother
    private val latencyTracker = LatencyTracker()
    private val slidingBuffer = FloatRingBuffer(BirdNetRuntimeConfig.WINDOW_SIZE_SAMPLES * 2)

    private val appContainer by lazy {
        (application as ProjectBirdApplication).appContainer
    }

    override fun onCreate() {
        super.onCreate()
        RecordingNotificationFactory.ensureChannel(this)
        streamingAudioRecorder = AudioRecordStreamRecorder(sampleRateHz = BirdNetRuntimeConfig.SAMPLE_RATE_HZ)
        wavChunkWriter = WavChunkWriter()
        locationProvider = FusedLocationProvider(this)
        tempFileManager = TempFileManager(this)
        sessionStateStore = RecordingSessionStateStore(this)
        audioProcessor = BirdNetTfliteAudioProcessor(
            context = this,
            threshold = BirdNetRuntimeConfig.DETECTION_THRESHOLD,
            topK = BirdNetRuntimeConfig.TOP_K,
            overlapMinOffsetsPresent = BirdNetRuntimeConfig.OVERLAP_MIN_OFFSETS_PRESENT,
            overlapStrongSingleThreshold = BirdNetRuntimeConfig.OVERLAP_STRONG_SINGLE_THRESHOLD,
            overlapFusionMaxWeight = BirdNetRuntimeConfig.OVERLAP_FUSION_MAX_WEIGHT,
        )
        meaningfulnessEvaluator = DefaultMeaningfulnessEvaluator(
            detectionConfidenceThreshold = BirdNetRuntimeConfig.DETECTION_THRESHOLD,
        )
        fastDetectionSmoother = TemporalDetectionSmoother(
            historySize = 3,
            minFramesPresent = 2,
            maxOutputSize = BirdNetRuntimeConfig.ADAPTIVE_TOP_K,
            enterConfidenceThreshold = BirdNetRuntimeConfig.SMOOTHING_ENTER_THRESHOLD,
            exitConfidenceThreshold = BirdNetRuntimeConfig.SMOOTHING_EXIT_THRESHOLD,
            immediateAcceptanceThreshold = BirdNetRuntimeConfig.SMOOTHING_IMMEDIATE_ACCEPTANCE_THRESHOLD,
            secondaryHoldFrames = BirdNetRuntimeConfig.SMOOTHING_SECONDARY_HOLD_FRAMES,
            dominantSuppressionMargin = BirdNetRuntimeConfig.SMOOTHING_DOMINANT_SUPPRESSION_MARGIN,
        )
        refinementDetectionSmoother = TemporalDetectionSmoother(
            historySize = BirdNetRuntimeConfig.SMOOTHING_WINDOW_COUNT,
            minFramesPresent = BirdNetRuntimeConfig.SMOOTHING_MIN_PRESENCE,
            maxOutputSize = BirdNetRuntimeConfig.TOP_K,
            enterConfidenceThreshold = BirdNetRuntimeConfig.SMOOTHING_ENTER_THRESHOLD,
            exitConfidenceThreshold = BirdNetRuntimeConfig.SMOOTHING_EXIT_THRESHOLD,
            immediateAcceptanceThreshold = BirdNetRuntimeConfig.SMOOTHING_IMMEDIATE_ACCEPTANCE_THRESHOLD,
            secondaryHoldFrames = BirdNetRuntimeConfig.SMOOTHING_SECONDARY_HOLD_FRAMES,
            dominantSuppressionMargin = BirdNetRuntimeConfig.SMOOTHING_DOMINANT_SUPPRESSION_MARGIN,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecordingForeground()
            ACTION_STOP -> stopRecordingForeground()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRecordingLoop()
        streamingAudioRecorder.release()
        serviceScope.cancel()
        isServiceRunning = false
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (recordingJob?.isActive == true) {
            val restartIntent = Intent(applicationContext, RecordingForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(restartIntent)
            } else {
                applicationContext.startService(restartIntent)
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun startRecordingForeground() {
        if (recordingJob?.isActive == true) return

        startForeground(
            RecordingNotificationFactory.NOTIFICATION_ID,
            RecordingNotificationFactory.createRecordingNotification(this),
        )

        isServiceRunning = true

        recordingJob = serviceScope.launch {
            val session = appContainer.sessionRepository.getActiveSession()
                ?: appContainer.sessionRepository.startSession()
            tempFileManager.clearAllTempFiles()
            locationProvider.start()
            streamingAudioRecorder.start()
            fastDetectionSmoother.reset()
            refinementDetectionSmoother.reset()
            latencyTracker.reset()
            slidingBuffer.clear()

            RecordingRuntimeStateHolder.set(
                RecordingRuntimeState(
                    isRecording = true,
                    activeSessionId = session.id,
                    sessionName = session.name,
                    sessionStartTimeMillis = session.startTime,
                    statusText = "Recording in progress",
                    inferenceModeLabel = "BirdNET v2.4 (Noise+Overlap)",
                    latencySummary = "Latency: warming up",
                )
            )
            sessionStateStore.markStarted(
                sessionId = session.id,
                sessionName = session.name,
                startedAtMillis = session.startTime,
            )

            while (isActive) {
                runCatching {
                    processSlidingWindow(session.id, session.startTime)
                    sessionStateStore.markHeartbeat()
                }.onFailure { error ->
                    RecordingRuntimeStateHolder.update {
                        it.copy(
                            statusText = "Recording error",
                            errorMessage = error.message,
                        )
                    }
                }

            }
        }
    }

    private suspend fun processSlidingWindow(sessionId: String, sessionStart: Long) {
        val loopStart = SystemClock.elapsedRealtimeNanos()

        val frame = streamingAudioRecorder.readFrame(BirdNetRuntimeConfig.HOP_SIZE_SAMPLES)
        slidingBuffer.append(frame.samples)

        val chunkTimestamp = Instant.now().toEpochMilli()
        val elapsed = (chunkTimestamp - sessionStart).coerceAtLeast(0L)

        if (slidingBuffer.size < BirdNetRuntimeConfig.WINDOW_SIZE_SAMPLES) {
            val loopEnd = SystemClock.elapsedRealtimeNanos()
            latencyTracker.record((loopEnd - loopStart) / 1_000_000L)
            RecordingRuntimeStateHolder.update {
                it.copy(
                    isRecording = true,
                    latestChunkTimestampMillis = chunkTimestamp,
                    latestChunkFilePath = null,
                    latestAmplitudeBars = amplitudeBarsFromIntensity(frame.rms),
                    latestDetections = emptyList(),
                    elapsedTimeText = elapsed.toClockString(),
                    inferenceModeLabel = "Warming up",
                    inferenceWarning = null,
                    statusText = "Warming up BirdNET",
                    latencySummary = latencyTracker.summaryText(),
                )
            }
            return
        }

        val windowSamples = slidingBuffer.latest(BirdNetRuntimeConfig.WINDOW_SIZE_SAMPLES)

        val location = locationProvider.getLatestLocation()
        val inferenceStart = SystemClock.elapsedRealtimeNanos()
        val rawResult = audioProcessor.process(
            ProcessingInput(
                timestampMillis = chunkTimestamp,
                latitude = location?.latitude,
                longitude = location?.longitude,
                durationMs = BirdNetRuntimeConfig.WINDOW_DURATION_MS,
                averageAmplitude = frame.rms,
                pcmSamples = windowSamples,
                sampleRateHz = BirdNetRuntimeConfig.SAMPLE_RATE_HZ,
            )
        )
        val inferenceMs = ((SystemClock.elapsedRealtimeNanos() - inferenceStart) / 1_000_000L)

        val rescoredDetections = rawResult.detectedItems

        val stableDetections = if (rawResult.processorMode == ProcessorMode.BIRDNET) {
            fastDetectionSmoother.smooth(rescoredDetections)
        } else {
            emptyList()
        }

        val overlapSignal = OverlapAnalyzer.analyze(
            detections = stableDetections,
            strongThreshold = BirdNetRuntimeConfig.DETECTION_THRESHOLD,
            triggerPolyphonyScore = BirdNetRuntimeConfig.OVERLAP_TRIGGER_POLYPHONY_SCORE,
            triggerEntropy = BirdNetRuntimeConfig.OVERLAP_TRIGGER_ENTROPY,
            triggerTop2Margin = BirdNetRuntimeConfig.OVERLAP_TRIGGER_TOP2_MARGIN,
        )

        val refinedDetections = if (
            rawResult.processorMode == ProcessorMode.BIRDNET &&
            overlapSignal.likelyOverlap
        ) {
            val refined = stableDetections
                .sortedByDescending { it.confidence }
                .take(BirdNetRuntimeConfig.ADAPTIVE_TOP_K)
            val smoothed = refinementDetectionSmoother.smooth(refined)
            overlapRefiner.refine(
                detections = smoothed,
                overlapSignal = overlapSignal,
                maxOutputSize = BirdNetRuntimeConfig.TOP_K,
            )
        } else {
            stableDetections
        }

        val stableResult = rawResult.copy(detectedItems = refinedDetections)
        val decision = meaningfulnessEvaluator.evaluate(stableResult)

        var persistedPath: String? = null

        if (decision.isMeaningful) {
            val meaningfulFile = tempFileManager.createTempAudioFile(extension = "wav")
            wavChunkWriter.writeMono16BitWav(
                outputFile = meaningfulFile,
                samples = windowSamples,
                sampleRateHz = BirdNetRuntimeConfig.SAMPLE_RATE_HZ,
            )
            persistedPath = meaningfulFile.absolutePath

            val persisted = persistMeaningfulChunk(
                sessionId = sessionId,
                chunkTimestamp = chunkTimestamp,
                recordedFilePath = meaningfulFile.absolutePath,
                durationMs = BirdNetRuntimeConfig.WINDOW_DURATION_MS,
                latitude = location?.latitude,
                longitude = location?.longitude,
                intensity = stableResult.intensity,
                environmentLabel = mapEnvironment(stableResult.environmentLabel),
                retentionReason = mapRetention(decision.retentionReason),
                detections = stableResult.detectedItems,
            )
            if (!persisted) {
                tempFileManager.deleteTempFile(meaningfulFile)
                persistedPath = null
            }
        }

        val loopEnd = SystemClock.elapsedRealtimeNanos()
        latencyTracker.record((loopEnd - loopStart) / 1_000_000L)

        RecordingRuntimeStateHolder.update {
            it.copy(
                isRecording = true,
                latestChunkTimestampMillis = chunkTimestamp,
                latestChunkFilePath = persistedPath,
                latestAmplitudeBars = amplitudeBarsFromIntensity(stableResult.intensity),
                latestDetections = stableResult.detectedItems.map { item ->
                    LiveDetection(
                        species = item.entityName,
                        confidence = item.confidence,
                        source = if (stableResult.processorMode == ProcessorMode.BIRDNET) {
                            DetectionSource.BIRDNET
                        } else {
                            DetectionSource.FALLBACK
                        },
                    )
                },
                environmentLabel = stableResult.environmentLabel.name.lowercase().replaceFirstChar { c -> c.uppercase() },
                inferenceModeLabel = if (stableResult.processorMode == ProcessorMode.BIRDNET) {
                    "BirdNET v2.4 (Noise+Overlap)"
                } else {
                    "Fallback mode"
                },
                overlapLikely = overlapSignal.likelyOverlap,
                overlapScore = overlapSignal.polyphonyScore,
                inferenceWarning = stableResult.warningMessage,
                latencySummary = latencyTracker.summaryText(inferenceMs = inferenceMs),
                locationText = location?.let {
                    "Lat ${"%.5f".format(it.latitude)}, Lng ${"%.5f".format(it.longitude)}"
                } ?: "Location unavailable",
                statusText = if (stableResult.processorMode == ProcessorMode.BIRDNET) {
                    "Recording in progress | Noise ${(stableResult.noiseScore * 100f).toInt()}% | SNR ${"%.1f".format(stableResult.snrDb)} dB"
                } else {
                    "Recording (BirdNET unavailable)"
                },
                elapsedTimeText = elapsed.toClockString(),
                errorMessage = null,
            )
        }
    }

    private suspend fun persistMeaningfulChunk(
        sessionId: String,
        chunkTimestamp: Long,
        recordedFilePath: String,
        durationMs: Long,
        latitude: Double?,
        longitude: Double?,
        intensity: Float,
        environmentLabel: EnvironmentLabel,
        retentionReason: RetentionReason,
        detections: List<com.example.projectbird.core.processor.DetectedItem>,
    ): Boolean {
        if (!File(recordedFilePath).exists()) return false

        return runCatching {
            appContainer.database.withTransaction {
                val captureId = UUID.randomUUID().toString()
                appContainer.capturePointDao.insertCapturePoint(
                    CapturePointEntity(
                        id = captureId,
                        sessionId = sessionId,
                        timestamp = chunkTimestamp,
                        latitude = latitude,
                        longitude = longitude,
                        audioFilePath = recordedFilePath,
                        durationMs = durationMs,
                        intensity = intensity,
                        environmentLabel = environmentLabel,
                        retentionReason = retentionReason,
                    )
                )

                val analysisId = UUID.randomUUID().toString()
                appContainer.analysisResultDao.insertAnalysisResult(
                    AnalysisResultEntity(
                        id = analysisId,
                        capturePointId = captureId,
                        processedAt = Instant.now().toEpochMilli(),
                        processorType = "BIRDNET_TFLITE",
                        processorVersion = "2.4.0",
                    )
                )

                if (detections.isNotEmpty()) {
                    appContainer.detectedEntityDao.insertAll(
                        detections.map { detected ->
                            DetectedEntityEntity(
                                id = UUID.randomUUID().toString(),
                                analysisResultId = analysisId,
                                entityName = detected.entityName,
                                confidence = detected.confidence,
                            )
                        }
                    )
                }
            }
        }.isSuccess
    }

    private fun mapRetention(reason: com.example.projectbird.core.processor.RetentionReason?): RetentionReason {
        return when (reason) {
            com.example.projectbird.core.processor.RetentionReason.DETECTION -> RetentionReason.DETECTION
            com.example.projectbird.core.processor.RetentionReason.INTENSITY -> RetentionReason.INTENSITY
            com.example.projectbird.core.processor.RetentionReason.BOTH -> RetentionReason.BOTH
            null -> RetentionReason.INTENSITY
        }
    }

    private fun mapEnvironment(label: com.example.projectbird.core.processor.EnvironmentLabel): EnvironmentLabel {
        return when (label) {
            com.example.projectbird.core.processor.EnvironmentLabel.QUIET -> EnvironmentLabel.QUIET
            com.example.projectbird.core.processor.EnvironmentLabel.MODERATE -> EnvironmentLabel.MODERATE
            com.example.projectbird.core.processor.EnvironmentLabel.ACTIVE -> EnvironmentLabel.ACTIVE
            com.example.projectbird.core.processor.EnvironmentLabel.NOISY -> EnvironmentLabel.NOISY
        }
    }

    private fun stopRecordingForeground() {
        stopRecordingLoop()
        isServiceRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopRecordingLoop() {
        recordingJob?.cancel()
        recordingJob = null
        streamingAudioRecorder.stop()
        if (::fastDetectionSmoother.isInitialized) {
            fastDetectionSmoother.reset()
        }
        if (::refinementDetectionSmoother.isInitialized) {
            refinementDetectionSmoother.reset()
        }
        latencyTracker.reset()
        slidingBuffer.clear()
        if (::sessionStateStore.isInitialized) {
            sessionStateStore.markStopped()
        }

        serviceScope.launch {
            runCatching { locationProvider.stop() }

            val activeSessionId = RecordingRuntimeStateHolder.state.value.activeSessionId
            if (!activeSessionId.isNullOrBlank()) {
                appContainer.sessionRepository.stopSession(activeSessionId)
            }

            RecordingRuntimeStateHolder.reset()
        }
    }

    private fun amplitudeBarsFromIntensity(intensity: Float): List<Float> {
        val clamped = intensity.coerceIn(0f, 1f)
        return listOf(
            (clamped * 0.4f + 0.10f).coerceIn(0.08f, 1f),
            (clamped * 0.6f + 0.12f).coerceIn(0.08f, 1f),
            (clamped * 0.5f + 0.08f).coerceIn(0.08f, 1f),
            (clamped * 0.8f + 0.10f).coerceIn(0.08f, 1f),
            (clamped * 0.45f + 0.09f).coerceIn(0.08f, 1f),
            (clamped * 0.65f + 0.11f).coerceIn(0.08f, 1f),
            (clamped * 0.5f + 0.07f).coerceIn(0.08f, 1f),
            (clamped * 0.75f + 0.10f).coerceIn(0.08f, 1f),
        )
    }

    private fun Long.toClockString(): String {
        val totalSeconds = this / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    companion object {
        const val ACTION_START = "com.example.projectbird.action.START_RECORDING"
        const val ACTION_STOP = "com.example.projectbird.action.STOP_RECORDING"

        @Volatile
        var isServiceRunning: Boolean = false
            private set
    }

    private class LatencyTracker(
        private val capacity: Int = 30,
    ) {
        private val samplesMs = ArrayDeque<Long>()

        fun record(totalMs: Long) {
            val clamped = totalMs.coerceAtLeast(0L)
            samplesMs.addLast(clamped)
            while (samplesMs.size > capacity) {
                samplesMs.removeFirst()
            }
        }

        fun reset() {
            samplesMs.clear()
        }

        fun summaryText(inferenceMs: Long? = null): String {
            if (samplesMs.isEmpty()) return "Latency: --"
            val sorted = samplesMs.sorted()
            val p50 = sorted[sorted.size / 2]
            val p95Index = ((sorted.size - 1) * 0.95f).roundToInt().coerceIn(0, sorted.lastIndex)
            val p95 = sorted[p95Index]
            val inf = inferenceMs?.coerceAtLeast(0L)
            return if (inf == null) {
                "Latency p50 ${p50}ms p95 ${p95}ms"
            } else {
                "Latency p50 ${p50}ms p95 ${p95}ms inf ${inf}ms"
            }
        }
    }
}
