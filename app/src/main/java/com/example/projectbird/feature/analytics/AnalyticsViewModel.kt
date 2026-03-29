package com.example.projectbird.feature.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectbird.ProjectBirdApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

data class TimelinePoint(
    val label: String,
    val count: Int,
)

data class DistributionPoint(
    val label: String,
    val count: Int,
)

data class IntensityPoint(
    val label: String,
    val value: Float,
)

data class AnalyticsUiState(
    val totalSessions: Int = 0,
    val totalMeaningfulCaptures: Int = 0,
    val topSpecies: String = "-",
    val averageIntensity: Float = 0f,
    val timeline: List<TimelinePoint> = emptyList(),
    val distribution: List<DistributionPoint> = emptyList(),
    val intensity: List<IntensityPoint> = emptyList(),
)

class AnalyticsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val container = (application as ProjectBirdApplication).appContainer

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val sessions = container.sessionRepository.observeSessionsSnapshot()
            val captures = container.capturePointDao.getAllCapturePoints()
            val topEntities = container.detectedEntityDao.getTopEntities(limit = 6)

            val avgIntensity = if (captures.isEmpty()) {
                0f
            } else {
                captures.map { it.intensity }.average().toFloat()
            }

            _uiState.value = AnalyticsUiState(
                totalSessions = sessions.size,
                totalMeaningfulCaptures = captures.size,
                topSpecies = topEntities.firstOrNull()?.entityName ?: "-",
                averageIntensity = avgIntensity,
                timeline = buildTimeline(captures.map { it.timestamp }),
                distribution = topEntities.map { DistributionPoint(it.entityName, it.detectionCount) },
                intensity = buildIntensity(captures.map { it.timestamp to it.intensity }),
            )
        }
    }

    private fun buildTimeline(timestamps: List<Long>): List<TimelinePoint> {
        if (timestamps.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        val grouped = timestamps.groupBy { ts ->
            val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), zone)
            "%02d:%02d".format(dt.hour, dt.minute)
        }
        return grouped.entries
            .sortedBy { it.key }
            .takeLast(8)
            .map { TimelinePoint(label = it.key, count = it.value.size) }
    }

    private fun buildIntensity(points: List<Pair<Long, Float>>): List<IntensityPoint> {
        if (points.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        val grouped = points.groupBy { (ts, _) ->
            val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), zone)
            "%02d:%02d".format(dt.hour, dt.minute)
        }

        return grouped.entries
            .sortedBy { it.key }
            .takeLast(8)
            .map { (label, values) ->
                val avg = values.map { it.second }.average().toFloat()
                IntensityPoint(label = label, value = ((avg * 100).roundToInt() / 100f))
            }
    }
}
