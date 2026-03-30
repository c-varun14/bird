package com.example.projectbird.feature.map

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.heatmaps.WeightedLatLng
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.heatmaps.HeatmapTileProvider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    LaunchedEffect(state.points) {
        if (state.points.isEmpty()) return@LaunchedEffect
        val boundsBuilder = LatLngBounds.Builder()
        state.points.forEach { point -> boundsBuilder.include(point.latLng) }
        val bounds = boundsBuilder.build()
        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 140))
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = true,
                mapToolbarEnabled = false,
            ),
        ) {
            state.points.forEach { point ->
                Marker(
                    state = MarkerState(position = point.latLng),
                    title = point.title,
                    snippet = point.snippet,
                    onClick = {
                        vm.selectPoint(point.id)
                        false
                    },
                )
            }

            MapEffect(state.weightedPoints, state.mode) { map ->
                val weighted = state.weightedPoints.map { item ->
                    WeightedLatLng(item.latLng, item.weight)
                }
                if (weighted.isEmpty()) return@MapEffect

                val radius = when (state.mode) {
                    HeatmapWeightMode.DETECTIONS -> 42
                    HeatmapWeightMode.INTENSITY -> 36
                    HeatmapWeightMode.RECENCY -> 32
                }

                val provider = HeatmapTileProvider.Builder()
                    .weightedData(weighted)
                    .radius(radius)
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Biodiversity Map",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            LazyRow(
                contentPadding = PaddingValues(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    listOf(
                        "Hotspots: ${state.totalHotspots}",
                        "Species: ${state.uniqueSpeciesCount}",
                        "Last 24h: ${state.last24hCaptures}",
                    )
                ) { label ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                FilterChip(
                    selected = state.mode == HeatmapWeightMode.RECENCY,
                    onClick = { vm.setMode(HeatmapWeightMode.RECENCY) },
                    label = { Text("Recency") },
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            state.selectedPoint?.let { point ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                        .navigationBarsPadding(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = point.topSpecies ?: point.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = point.snippet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Last seen ${point.timestampMillis.toReadableTime()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private fun Long.toReadableTime(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
}
