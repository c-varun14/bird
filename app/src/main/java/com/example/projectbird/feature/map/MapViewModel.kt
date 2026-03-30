package com.example.projectbird.feature.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectbird.ProjectBirdApplication
import com.example.projectbird.data.local.dao.CaptureDetectionCountRow
import com.example.projectbird.data.local.dao.CaptureSpeciesCountRow
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import kotlin.math.max

enum class HeatmapWeightMode {
    DETECTIONS,
    INTENSITY,
    RECENCY,
}

data class MapPoint(
    val id: String,
    val latLng: LatLng,
    val title: String,
    val snippet: String,
    val intensity: Float,
    val detections: Int,
    val timestampMillis: Long,
    val topSpecies: String?,
    val topSpeciesCount: Int,
)

data class WeightedPoint(
    val latLng: LatLng,
    val weight: Double,
)

data class MapUiState(
    val points: List<MapPoint> = emptyList(),
    val weightedPoints: List<WeightedPoint> = emptyList(),
    val mode: HeatmapWeightMode = HeatmapWeightMode.DETECTIONS,
    val totalHotspots: Int = 0,
    val uniqueSpeciesCount: Int = 0,
    val last24hCaptures: Int = 0,
    val selectedPoint: MapPoint? = null,
)

class MapViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val container = (application as ProjectBirdApplication).appContainer

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun setMode(mode: HeatmapWeightMode) {
        _uiState.value = _uiState.value.copy(
            mode = mode,
            weightedPoints = toWeightedPoints(_uiState.value.points, mode),
        )
    }

    fun selectPoint(pointId: String?) {
        val selected = _uiState.value.points.firstOrNull { it.id == pointId }
        _uiState.value = _uiState.value.copy(selectedPoint = selected)
    }

    fun refresh() {
        viewModelScope.launch {
            val capturePoints = container.capturePointDao.getMapCapturePoints()
            val countsByCaptureId = container.detectedEntityDao.getDetectionCountsByCapturePoint()
                .associateBy(CaptureDetectionCountRow::capturePointId)
            val speciesByCaptureId = container.detectedEntityDao.getSpeciesCountsByCapturePoint()
                .groupBy(CaptureSpeciesCountRow::capturePointId)

            val now = Instant.now().toEpochMilli()

            val mapped = capturePoints.mapNotNull { point ->
                val lat = point.latitude ?: return@mapNotNull null
                val lng = point.longitude ?: return@mapNotNull null
                val detectionCount = countsByCaptureId[point.id]?.detectionCount ?: 0
                val topSpeciesRow = speciesByCaptureId[point.id]
                    ?.maxByOrNull { it.detectionCount }

                val topSpecies = topSpeciesRow?.entityName
                val topSpeciesCount = topSpeciesRow?.detectionCount ?: 0

                MapPoint(
                    id = point.id,
                    latLng = LatLng(lat, lng),
                    title = topSpecies ?: point.environmentLabel.name,
                    snippet = "${point.environmentLabel.name.lowercase().replaceFirstChar { it.uppercase() }} • Int ${"%.2f".format(point.intensity)} • Hits $detectionCount",
                    intensity = point.intensity,
                    detections = detectionCount,
                    timestampMillis = point.timestamp,
                    topSpecies = topSpecies,
                    topSpeciesCount = topSpeciesCount,
                )
            }

            val mode = _uiState.value.mode
            val uniqueSpecies = speciesByCaptureId.values
                .flatMap { rows -> rows.map { it.entityName } }
                .toSet()
                .size
            val last24hThreshold = now - Duration.ofHours(24).toMillis()
            val last24hCaptures = mapped.count { it.timestampMillis >= last24hThreshold }

            _uiState.value = MapUiState(
                points = mapped,
                weightedPoints = toWeightedPoints(mapped, mode),
                mode = mode,
                totalHotspots = mapped.size,
                uniqueSpeciesCount = uniqueSpecies,
                last24hCaptures = last24hCaptures,
                selectedPoint = mapped.firstOrNull(),
            )
        }
    }

    private fun toWeightedPoints(
        points: List<MapPoint>,
        mode: HeatmapWeightMode,
    ): List<WeightedPoint> {
        return points.map { point ->
            val weight = when (mode) {
                HeatmapWeightMode.DETECTIONS -> max(1, point.detections).toDouble()
                HeatmapWeightMode.INTENSITY -> point.intensity.coerceIn(0.05f, 1f).toDouble()
                HeatmapWeightMode.RECENCY -> {
                    val ageHours = ((Instant.now().toEpochMilli() - point.timestampMillis)
                        .coerceAtLeast(0L) / Duration.ofHours(1).toMillis().toDouble())
                    (1.0 / (1.0 + ageHours)).coerceIn(0.10, 1.0)
                }
            }
            WeightedPoint(point.latLng, weight)
        }
    }
}
