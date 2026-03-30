package com.example.projectbird.core.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveDetection(
    val species: String,
    val confidence: Float,
    val source: DetectionSource,
)

enum class DetectionSource {
    BIRDNET,
    FALLBACK,
}

data class RecordingRuntimeState(
    val isRecording: Boolean = false,
    val activeSessionId: String? = null,
    val sessionName: String? = null,
    val sessionStartTimeMillis: Long? = null,
    val latestChunkTimestampMillis: Long? = null,
    val latestChunkFilePath: String? = null,
    val latestAmplitudeBars: List<Float> = DEFAULT_AMPLITUDE_BARS,
    val latestDetections: List<LiveDetection> = emptyList(),
    val elapsedTimeText: String = "00:00:00",
    val environmentLabel: String = "Idle",
    val inferenceModeLabel: String = "Idle",
    val inferenceWarning: String? = null,
    val locationText: String = "Location unavailable",
    val statusText: String = "Ready to record",
    val errorMessage: String? = null,
) {
    companion object {
        val DEFAULT_AMPLITUDE_BARS: List<Float> = listOf(
            0.12f, 0.35f, 0.20f, 0.55f, 0.30f, 0.42f, 0.24f, 0.50f,
        )
    }
}

object RecordingRuntimeStateHolder {
    private val mutableState = MutableStateFlow(RecordingRuntimeState())

    val state: StateFlow<RecordingRuntimeState> = mutableState.asStateFlow()

    fun update(transform: (RecordingRuntimeState) -> RecordingRuntimeState) {
        mutableState.value = transform(mutableState.value)
    }

    fun set(value: RecordingRuntimeState) {
        mutableState.value = value
    }

    fun reset() {
        mutableState.value = RecordingRuntimeState()
    }
}
