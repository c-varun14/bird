package com.example.projectbird.feature.analytics

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val vm: AnalyticsViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refresh() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "Analytics",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    title = "Sessions",
                    value = state.totalSessions.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "Captures",
                    value = state.totalMeaningfulCaptures.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    title = "Top Species",
                    value = state.topSpecies,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "Avg Intensity",
                    value = "%.2f".format(state.averageIntensity),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            ChartCard(
                title = "Detection Timeline",
                emptyText = "No detections yet",
                hasData = state.timeline.isNotEmpty(),
            ) {
                DetectionTimelineChart(points = state.timeline)
            }
        }

        item {
            ChartCard(
                title = "Bird Distribution",
                emptyText = "No species detected yet",
                hasData = state.distribution.isNotEmpty(),
            ) {
                BirdDistributionChart(points = state.distribution)
            }
        }

        item {
            ChartCard(
                title = "Activity Intensity",
                emptyText = "No intensity data yet",
                hasData = state.intensity.isNotEmpty(),
            ) {
                IntensityLineChart(points = state.intensity)
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    hasData: Boolean,
    emptyText: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (!hasData) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                content()
            }
        }
    }
}

@Composable
private fun DetectionTimelineChart(
    points: List<TimelinePoint>,
) {
    val maxCount = points.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            points.forEach { point ->
                val ratio = point.count / maxCount.toFloat()
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = point.count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((84f * ratio).dp.coerceAtLeast(8.dp))
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            points.forEach { point ->
                Text(
                    text = point.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BirdDistributionChart(
    points: List<DistributionPoint>,
) {
    val maxCount = points.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        points.forEach { point ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = point.count.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LinearProgressIndicator(
                    progress = { point.count / maxCount.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun IntensityLineChart(
    points: List<IntensityPoint>,
) {
    val maxValue = points.maxOfOrNull { it.value }?.coerceAtLeast(0.01f) ?: 1f
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val chartPadding = 16.dp.toPx()
            val chartWidth = size.width - (chartPadding * 2)
            val chartHeight = size.height - (chartPadding * 2)

            drawLine(
                color = outlineColor,
                start = Offset(chartPadding, chartPadding + chartHeight),
                end = Offset(chartPadding + chartWidth, chartPadding + chartHeight),
                strokeWidth = 2f,
            )

            if (points.size < 2) {
                val single = points.firstOrNull()
                if (single != null) {
                    val y = chartPadding + chartHeight - ((single.value / maxValue) * chartHeight)
                    drawCircle(
                        color = primaryColor,
                        radius = 6.dp.toPx(),
                        center = Offset(chartPadding + chartWidth / 2f, y),
                    )
                }
                return@Canvas
            }

            val path = Path()
            points.forEachIndexed { index, point ->
                val x = chartPadding + (index.toFloat() / (points.lastIndex)) * chartWidth
                val y = chartPadding + chartHeight - ((point.value / maxValue) * chartHeight)
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round),
            )

            points.forEachIndexed { index, point ->
                val x = chartPadding + (index.toFloat() / (points.lastIndex)) * chartWidth
                val y = chartPadding + chartHeight - ((point.value / maxValue) * chartHeight)

                drawCircle(
                    color = surfaceColor,
                    radius = 5.dp.toPx(),
                    center = Offset(x, y),
                )
                drawCircle(
                    color = primaryColor,
                    radius = 3.dp.toPx(),
                    center = Offset(x, y),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            points.forEach { point ->
                Text(
                    text = "${point.label}\n${(point.value * 100f).roundToInt() / 100f}",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
