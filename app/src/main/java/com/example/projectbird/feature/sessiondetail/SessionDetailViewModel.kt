package com.example.projectbird.feature.sessiondetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectbird.ProjectBirdApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class SessionDetailUiState(
    val sessionName: String = "",
    val sessionId: String = "",
    val durationLabel: String = "0 min",
    val meaningfulCaptureCount: Int = 0,
    val averageIntensity: Float = 0f,
    val topSpecies: String = "-",
)

class SessionDetailViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val container = (application as ProjectBirdApplication).appContainer

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    fun load(sessionId: String) {
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            val session = container.sessionRepository.getSessionById(sessionId) ?: return@launch
            val captures = container.capturePointDao.getCapturePointsBySession(sessionId)
            val duration = (session.endTime ?: session.startTime) - session.startTime
            val avgIntensity = if (captures.isEmpty()) 0f else captures.map { it.intensity }.average().toFloat()
            val top = container.detectedEntityDao.getTopEntityForSession(sessionId)?.entityName ?: "-"

            _uiState.value = SessionDetailUiState(
                sessionName = session.name,
                sessionId = session.id,
                durationLabel = "${(duration.coerceAtLeast(0L) / 60_000L)} min",
                meaningfulCaptureCount = captures.size,
                averageIntensity = avgIntensity,
                topSpecies = top,
            )
        }
    }

    fun deleteSession(sessionId: String, onDeleted: () -> Unit) {
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            val captures = container.capturePointDao.getCapturePointsBySession(sessionId)
            captures.forEach { capture ->
                runCatching { File(capture.audioFilePath).delete() }
            }
            container.sessionRepository.deleteSession(sessionId)
            onDeleted()
        }
    }
}
