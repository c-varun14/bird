package com.example.projectbird.core.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
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
import com.example.projectbird.core.processor.BirdSpeciesPrior
import com.example.projectbird.core.processor.ProcessorMode
import com.example.projectbird.core.processor.ProcessingInput
import com.example.projectbird.core.processor.TemporalDetectionSmoother
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

class RecordingForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null

    private lateinit var streamingAudioRecorder: StreamingAudioRecorder
    private lateinit var wavChunkWriter: WavChunkWriter
    private lateinit var locationProvider: FusedLocationProvider
    private lateinit var tempFileManager: TempFileManager
    private lateinit var audioProcessor: AudioProcessor
    private val birdSpeciesPrior = BirdSpeciesPrior()
    private val meaningfulnessEvaluator = DefaultMeaningfulnessEvaluator(
        detectionConfidenceThreshold = BirdNetRuntimeConfig.DETECTION_THRESHOLD,
    )
    private val detectionSmoother = TemporalDetectionSmoother(
        historySize = BirdNetRuntimeConfig.SMOOTHING_WINDOW_COUNT,
        minFramesPresent = BirdNetRuntimeConfig.SMOOTHING_MIN_PRESENCE,
        maxOutputSize = BirdNetRuntimeConfig.TOP_K,
        enterConfidenceThreshold = BirdNetRuntimeConfig.SMOOTHING_ENTER_THRESHOLD,
        exitConfidenceThreshold = BirdNetRuntimeConfig.SMOOTHING_EXIT_THRESHOLD,
    )
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
        audioProcessor = BirdNetTfliteAudioProcessor(
            context = this,
            threshold = BirdNetRuntimeConfig.DETECTION_THRESHOLD,
            topK = BirdNetRuntimeConfig.TOP_K,
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
            detectionSmoother.reset()
            slidingBuffer.clear()

            RecordingRuntimeStateHolder.set(
                RecordingRuntimeState(
                    isRecording = true,
                    activeSessionId = session.id,
                    sessionName = session.name,
                    sessionStartTimeMillis = session.startTime,
                    statusText = "Recording in progress",
                    inferenceModeLabel = "Initializing BirdNET",
                )
            )

            while (isActive) {
                runCatching {
                    processSlidingWindow(session.id, session.startTime)
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
        val frame = streamingAudioRecorder.readFrame(BirdNetRuntimeConfig.HOP_SIZE_SAMPLES)
        slidingBuffer.append(frame.samples)

        val chunkTimestamp = Instant.now().toEpochMilli()
        val elapsed = (chunkTimestamp - sessionStart).coerceAtLeast(0L)

        if (slidingBuffer.size < BirdNetRuntimeConfig.WINDOW_SIZE_SAMPLES) {
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
                )
            }
            return
        }

        val windowSamples = slidingBuffer.latest(BirdNetRuntimeConfig.WINDOW_SIZE_SAMPLES)

        val location = locationProvider.getLatestLocation()
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

        val rescoredDetections = if (rawResult.processorMode == ProcessorMode.BIRDNET) {
            birdSpeciesPrior.rescore(
                detections = rawResult.detectedItems,
                latitude = location?.latitude,
                longitude = location?.longitude,
                timestampMillis = chunkTimestamp,
            )
        } else {
            rawResult.detectedItems
        }

        val stableDetections = if (rawResult.processorMode == ProcessorMode.BIRDNET) {
            detectionSmoother.smooth(rescoredDetections)
        } else {
            emptyList()
        }

        val stableResult = rawResult.copy(detectedItems = stableDetections)
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
                    "BirdNET active"
                } else {
                    "Fallback mode"
                },
                inferenceWarning = stableResult.warningMessage,
                locationText = location?.let {
                    "Lat ${"%.5f".format(it.latitude)}, Lng ${"%.5f".format(it.longitude)}"
                } ?: "Location unavailable",
                statusText = if (stableResult.processorMode == ProcessorMode.BIRDNET) {
                    "Recording in progress"
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
                        processorVersion = "1.1.0",
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
        detectionSmoother.reset()
        slidingBuffer.clear()

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
}
