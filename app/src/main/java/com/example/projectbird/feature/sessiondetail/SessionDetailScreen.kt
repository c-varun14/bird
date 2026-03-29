package com.example.projectbird.feature.sessiondetail

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

@Composable
fun SessionDetailScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val vm: SessionDetailViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) {
        vm.load(sessionId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
            )
        }

        Text(
            text = state.sessionName.ifBlank { "Session Detail" },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        StatCard(title = "Session ID", value = state.sessionId.ifBlank { sessionId })
        StatCard(title = "Duration", value = state.durationLabel)
        StatCard(title = "Meaningful captures", value = state.meaningfulCaptureCount.toString())
        StatCard(title = "Average intensity", value = "%.2f".format(state.averageIntensity))
        StatCard(title = "Top species", value = state.topSpecies)

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                vm.deleteSession(sessionId) {
                    onNavigateBack()
                }
            },
        ) {
            Text(text = "Delete session")
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
