package com.example.projectbird.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.projectbird.ui.theme.ProjectBirdTheme

@Immutable
data class HomeUiState(
    val isRecording: Boolean = false,
    val statusText: String = "Ready to record",
    val elapsedTimeText: String = "00:00:00",
    val locationText: String = "Location unavailable",
    val environmentLabel: String = "Idle",
    val latestDetections: List<String> = emptyList(),
    val permissionGuidance: String? = null,
    val amplitudeBars: List<Float> = listOf(
        0.12f, 0.35f, 0.20f, 0.55f, 0.30f, 0.42f, 0.24f, 0.50f
    ),
)

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeHeader(
            title = "Project Bird",
            subtitle = "Local biodiversity intelligence",
        )

        RecordingStatusCard(
            statusText = uiState.statusText,
            elapsedTimeText = uiState.elapsedTimeText,
            locationText = uiState.locationText,
            environmentLabel = uiState.environmentLabel,
        )

        WaveformCard(
            amplitudeBars = uiState.amplitudeBars,
            isRecording = uiState.isRecording,
        )

        RecordingControls(
            isRecording = uiState.isRecording,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
        )

        uiState.permissionGuidance?.let { guidance ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = guidance,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        LiveDetectionsCard(
            detections = uiState.latestDetections,
            isRecording = uiState.isRecording,
        )

        Spacer(modifier = Modifier.weight(1f))

        BackgroundRecordingHint()
    }
}

@Composable
private fun HomeHeader(
    title: String,
    subtitle: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecordingStatusCard(
    statusText: String,
    elapsedTimeText: String,
    locationText: String,
    environmentLabel: String,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatusMetric(
                    label = "Elapsed",
                    value = elapsedTimeText,
                )
                StatusMetric(
                    label = "Environment",
                    value = environmentLabel,
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = locationText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusMetric(
    label: String,
    value: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun WaveformCard(
    amplitudeBars: List<Float>,
    isRecording: Boolean,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isRecording) "Live waveform" else "Waveform preview",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                amplitudeBars.forEach { amplitude ->
                    WaveBar(
                        amplitude = amplitude,
                        isRecording = isRecording,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveBar(
    amplitude: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
) {
    val normalized = amplitude.coerceIn(0.08f, 1f)
    val brush = if (isRecording) {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
            ),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                MaterialTheme.colorScheme.outline.copy(alpha = 0.9f),
            ),
        )
    }

    Box(
        modifier = modifier.heightIn(min = 10.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp * normalized)
                .clip(RoundedCornerShape(999.dp))
                .background(brush),
        )
    }
}

@Composable
private fun RecordingControls(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    if (isRecording) {
        FilledTonalButton(
            onClick = onStopRecording,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Stop recording",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    } else {
        Button(
            onClick = onStartRecording,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Start recording",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun LiveDetectionsCard(
    detections: List<String>,
    isRecording: Boolean,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Current detections",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (detections.isEmpty()) {
                Text(
                    text = if (isRecording) {
                        "Listening for meaningful activity..."
                    } else {
                        "Start recording to see live detections."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    detections.forEach { detection ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = detection,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundRecordingHint() {
    Text(
        text = "Once recording starts, it continues even if you leave the app until you stop it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenIdlePreview() {
    ProjectBirdTheme {
        HomeScreen(
            uiState = HomeUiState(),
            onStartRecording = {},
            onStopRecording = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenRecordingPreview() {
    ProjectBirdTheme {
        HomeScreen(
            uiState = HomeUiState(
                isRecording = true,
                statusText = "Recording in progress",
                elapsedTimeText = "00:12:18",
                locationText = "Lat 12.9716, Lng 77.5946",
                environmentLabel = "Active",
                latestDetections = listOf(
                    "Sparrow • 0.82",
                    "Myna • 0.74",
                    "Crow • 0.68",
                ),
            ),
            onStartRecording = {},
            onStopRecording = {},
        )
    }
}
