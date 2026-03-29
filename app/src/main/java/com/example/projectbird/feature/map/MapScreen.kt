package com.example.projectbird.feature.map

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.heatmaps.WeightedLatLng
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.heatmaps.HeatmapTileProvider

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val vm: MapViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refresh() }

    val defaultCenter = state.points.firstOrNull()?.latLng ?: LatLng(12.9716, 77.5946)
    val cameraPositionState: CameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCenter, 12f)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Map",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.mode == HeatmapWeightMode.DETECTIONS,
                onClick = { vm.setMode(HeatmapWeightMode.DETECTIONS) },
                label = { Text("Detections") },
            )
            FilterChip(
                selected = state.mode == HeatmapWeightMode.INTENSITY,
                onClick = { vm.setMode(HeatmapWeightMode.INTENSITY) },
                label = { Text("Intensity") },
            )
        }

        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = true,
            ),
        ) {
            state.points.forEach { point ->
                Marker(
                    state = MarkerState(position = point.latLng),
                    title = point.title,
                    snippet = point.snippet,
                )
            }

            MapEffect(state.weightedPoints) { map ->
                val weighted = state.weightedPoints.map { item ->
                    WeightedLatLng(item.latLng, item.weight)
                }
                if (weighted.isEmpty()) return@MapEffect

                val provider = HeatmapTileProvider.Builder()
                    .weightedData(weighted)
                    .radius(40)
                    .build()

                map.clear()
                map.addTileOverlay(
                    com.google.android.gms.maps.model.TileOverlayOptions()
                        .tileProvider(provider)
                )

                state.points.forEach { point ->
                    map.addMarker(
                        com.google.android.gms.maps.model.MarkerOptions()
                            .position(point.latLng)
                            .title(point.title)
                            .snippet(point.snippet)
                    )
                }
            }
        }
    }
}
