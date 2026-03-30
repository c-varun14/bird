package com.example.projectbird.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectbird.ProjectBirdApplication
import com.example.projectbird.core.service.RecordingRuntimeStateHolder
import com.example.projectbird.core.service.RecordingSessionStateStore
import com.example.projectbird.data.local.entity.SessionEntity
import com.example.projectbird.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import kotlin.math.absoluteValue

class HomeViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val sessionRepository: SessionRepository =
        (application as ProjectBirdApplication).appContainer.sessionRepository
    private val sessionStateStore = RecordingSessionStateStore(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        applyPersistedRecordingState()
        observeServiceState()
        observeActiveSession()
    }

    private fun applyPersistedRecordingState() {
        val persisted = sessionStateStore.read()
        if (!persisted.isRecording) return

        val startedAt = persisted.startedAtMillis ?: return
        val now = Instant.now().toEpochMilli()
        val elapsed = (now - startedAt).coerceAtLeast(0L)

        _uiState.value = _uiState.value.copy(
            isRecording = true,
            statusText = "Recording in background",
            elapsedTimeText = formatElapsed(elapsed),
            inferenceModeLabel = "Reconnecting to service",
            inferenceWarning = null,
        )
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            RecordingRuntimeStateHolder.state.collectLatest { serviceState ->
                _uiState.value = _uiState.value.copy(
                    isRecording = serviceState.isRecording,
                    statusText = serviceState.statusText,
                    elapsedTimeText = serviceState.elapsedTimeText,
                    locationText = serviceState.locationText,
                    environmentLabel = serviceState.environmentLabel,
                    inferenceModeLabel = serviceState.inferenceModeLabel,
                    inferenceWarning = serviceState.inferenceWarning,
                    overlapLikely = serviceState.overlapLikely,
                    overlapScore = serviceState.overlapScore,
                    latencySummary = serviceState.latencySummary,
                    latestDetections = serviceState.latestDetections,
                    amplitudeBars = serviceState.latestAmplitudeBars.ifEmpty {
                        defaultAmplitudeBars()
                    },
                    permissionGuidance = null,
                )
            }
        }
    }

    private fun observeActiveSession() {
        viewModelScope.launch {
            sessionRepository.observeActiveSession().collectLatest { session ->
                mergeSessionState(session)
            }
        }
    }

    private fun mergeSessionState(session: SessionEntity?) {
        val currentState = _uiState.value

        val statusText = when {
            currentState.isRecording -> currentState.statusText
            session != null -> "Session ready"
            else -> "Ready to record"
        }

        val elapsed = when {
            currentState.isRecording -> currentState.elapsedTimeText
            session != null -> formatElapsedFromSession(session)
            else -> "00:00:00"
        }

        _uiState.value = currentState.copy(
            statusText = statusText,
            elapsedTimeText = elapsed,
        )
    }

    fun setPermissionGuidance(message: String?) {
        _uiState.value = _uiState.value.copy(
            permissionGuidance = message,
        )
    }

    private fun formatElapsedFromSession(session: SessionEntity): String {
        val endTime = session.endTime ?: session.startTime
        return formatElapsed((endTime - session.startTime).coerceAtLeast(0L))
    }

    private fun formatElapsed(elapsedMillis: Long): String {
        val totalSeconds = (elapsedMillis / 1000L).absoluteValue
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds,
        )
    }

    private fun defaultAmplitudeBars(): List<Float> {
        return listOf(
            0.12f,
            0.35f,
            0.20f,
            0.55f,
            0.30f,
            0.42f,
            0.24f,
            0.50f,
        )
    }
}
