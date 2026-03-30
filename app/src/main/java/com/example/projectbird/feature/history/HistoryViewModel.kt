package com.example.projectbird.feature.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectbird.ProjectBirdApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SessionHistoryItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val summary: String,
)

data class HistoryUiState(
    val sessions: List<SessionHistoryItem> = emptyList(),
)

class HistoryViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val container = (application as ProjectBirdApplication).appContainer

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val sessions = container.sessionRepository.observeSessionsSnapshot()
            val mapped = sessions.map { session ->
                val captureCount = container.capturePointDao.getCaptureCountForSession(session.id)
                val top = container.detectedEntityDao.getTopEntityForSession(session.id)?.entityName
                val endTime = session.endTime ?: Instant.now().toEpochMilli()
                val duration = (endTime - session.startTime).coerceAtLeast(0L)

                SessionHistoryItem(
                    id = session.id,
                    title = session.name,
                    subtitle = "${session.startTime.toReadableDate()} • ${duration.toDurationLabel()}",
                    summary = "$captureCount meaningful captures • Top species: ${top ?: "-"}",
                )
            }
            _uiState.value = HistoryUiState(sessions = mapped)
        }
    }

    private fun Long.toReadableDate(): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
        return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(formatter)
    }

    private fun Long.toDurationLabel(): String {
        val duration = Duration.ofMillis(coerceAtLeast(0L))
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}
