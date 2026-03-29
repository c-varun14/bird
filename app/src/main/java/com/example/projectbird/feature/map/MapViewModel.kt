package com.example.projectbird.feature.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.projectbird.ProjectBirdApplication
import com.example.projectbird.data.local.dao.CaptureDetectionCountRow
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

enum class HeatmapWeightMode {
    DETECTIONS,
    INTENSITY,
}

data class MapPoint(
    val id: String,
    val latLng: LatLng,
    val title: String,
    val snippet: String,
    val intensity: Float,
    val detections: Int,
)

data class WeightedPoint(
    val latLng: LatLng,
    val weight: Double,
)

data class MapUiState(
    val points: List<MapPoint> = emptyList(),
    val weightedPoints: List<WeightedPoint> = emptyList(),
    val mode: HeatmapWeightMode = HeatmapWeightMode.DETECTIONS,
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

    fun refresh() {
        viewModelScope.launch {
            val capturePoints = container.capturePointDao.getMapCapturePoints()
            val countsByCaptureId = container.detectedEntityDao.getDetectionCountsByCapturePoint()
                .associateBy(CaptureDetectionCountRow::capturePointId)

            val mapped = capturePoints.mapNotNull { point ->
                val lat = point.latitude ?: return@mapNotNull null
                val lng = point.longitude ?: return@mapNotNull null
                val detectionCount = countsByCaptureId[point.id]?.detectionCount ?: 0

                MapPoint(
                    id = point.id,
                    latLng = LatLng(lat, lng),
                    title = point.environmentLabel.name,
                    snippet = "Intensity ${"%.2f".format(point.intensity)} | Detections $detectionCount",
                    intensity = point.intensity,
                    detections = detectionCount,
                )
            }

            val mode = _uiState.value.mode
            _uiState.value = MapUiState(
                points = mapped,
                weightedPoints = toWeightedPoints(mapped, mode),
                mode = mode,
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
            }
            WeightedPoint(point.latLng, weight)
        }
    }
}
