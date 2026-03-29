package com.example.projectbird.core.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.projectbird.ProjectBirdApplication
import com.example.projectbird.core.audio.AudioRecorder
import com.example.projectbird.core.audio.MediaRecorderAudioRecorder
import com.example.projectbird.core.location.FusedLocationProvider
import com.example.projectbird.core.processor.AudioProcessor
import com.example.projectbird.core.processor.DefaultMeaningfulnessEvaluator
import com.example.projectbird.core.processor.MockAudioProcessor
import com.example.projectbird.core.processor.ProcessingInput
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
import java.time.Instant
import java.util.UUID

class RecordingForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var locationProvider: FusedLocationProvider
    private lateinit var tempFileManager: TempFileManager
    private lateinit var audioProcessor: AudioProcessor
    private val meaningfulnessEvaluator = DefaultMeaningfulnessEvaluator()

    private val appContainer by lazy {
        (application as ProjectBirdApplication).appContainer
    }

    override fun onCreate() {
        super.onCreate()
        RecordingNotificationFactory.ensureChannel(this)
        audioRecorder = MediaRecorderAudioRecorder(this)
        locationProvider = FusedLocationProvider(this)
        tempFileManager = TempFileManager(this)
        audioProcessor = MockAudioProcessor()
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
        audioRecorder.release()
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
            locationProvider.start()

            RecordingRuntimeStateHolder.set(
                RecordingRuntimeState(
                    isRecording = true,
                    activeSessionId = session.id,
                    sessionName = session.name,
                    sessionStartTimeMillis = session.startTime,
                    statusText = "Recording in progress",
                )
            )

            while (isActive) {
                runCatching {
                    processSingleChunk(session.id, session.startTime)
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

    private suspend fun processSingleChunk(sessionId: String, sessionStart: Long) {
        val tempFile = tempFileManager.createTempAudioFile()
        val chunkTimestamp = Instant.now().toEpochMilli()

        val recorded = runCatching {
            audioRecorder.recordChunk(
                outputFile = tempFile,
                durationMs = CHUNK_DURATION_MS,
            )
        }.getOrElse {
            tempFileManager.deleteTempFile(tempFile)
            throw it
        }

        val location = locationProvider.getLatestLocation()
        val result = audioProcessor.process(
            ProcessingInput(
                audioFile = recorded.file,
                timestampMillis = chunkTimestamp,
                latitude = location?.latitude,
                longitude = location?.longitude,
                durationMs = recorded.durationMs,
                averageAmplitude = recorded.averageAmplitude,
            )
        )
        val decision = meaningfulnessEvaluator.evaluate(result)

        if (decision.isMeaningful) {
            persistMeaningfulChunk(
                sessionId = sessionId,
                chunkTimestamp = chunkTimestamp,
                recordedFilePath = recorded.file.absolutePath,
                durationMs = recorded.durationMs,
                latitude = location?.latitude,
                longitude = location?.longitude,
                intensity = result.intensity,
                environmentLabel = mapEnvironment(result.environmentLabel),
                retentionReason = mapRetention(decision.retentionReason),
                detections = result.detectedItems,
            )
        } else {
            tempFileManager.deleteTempFile(recorded.file)
        }

        val elapsed = (chunkTimestamp - sessionStart).coerceAtLeast(0L)
        RecordingRuntimeStateHolder.update {
            it.copy(
                isRecording = true,
                latestChunkTimestampMillis = chunkTimestamp,
                latestChunkFilePath = recorded.file.absolutePath,
                latestAmplitudeBars = amplitudeBarsFromIntensity(result.intensity),
                latestDetections = result.detectedItems.map { item ->
                    "${item.entityName} - ${"%.2f".format(item.confidence)}"
                },
                environmentLabel = result.environmentLabel.name.lowercase().replaceFirstChar { c -> c.uppercase() },
                locationText = location?.let {
                    "Lat ${"%.5f".format(it.latitude)}, Lng ${"%.5f".format(it.longitude)}"
                } ?: "Location unavailable",
                statusText = "Recording in progress",
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
    ) {
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
                processorType = "MOCK",
                processorVersion = "1.0",
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
        private const val CHUNK_DURATION_MS = 2_000L

        @Volatile
        var isServiceRunning: Boolean = false
            private set
    }
}
