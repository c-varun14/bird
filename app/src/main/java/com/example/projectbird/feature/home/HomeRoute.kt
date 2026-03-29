package com.example.projectbird.feature.home

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.projectbird.core.service.RecordingServiceController

@Composable
fun HomeRoute() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: HomeViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application),
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var permissionGuidance by remember { mutableStateOf<String?>(null) }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_FINE_LOCATION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val deniedPermissions = requiredPermissions.filter { permission ->
            results[permission] != true
        }

        if (deniedPermissions.isEmpty()) {
            permissionGuidance = null
            viewModel.setPermissionGuidance(null)
            RecordingServiceController.startRecording(context)
        } else {
            permissionGuidance =
                "Microphone, location, and notification access are required to start background recording."
            viewModel.setPermissionGuidance(permissionGuidance)
        }
    }

    LaunchedEffect(uiState.isRecording) {
        if (uiState.isRecording) {
            permissionGuidance = null
            viewModel.setPermissionGuidance(null)
        }
    }

    HomeScreen(
        uiState = uiState.copy(
            permissionGuidance = permissionGuidance ?: uiState.permissionGuidance,
        ),
        onStartRecording = {
            val deniedPermissions = requiredPermissions.filter { permission ->
                ContextCompat.checkSelfPermission(
                    context,
                    permission,
                ) != PackageManager.PERMISSION_GRANTED
            }

            if (deniedPermissions.isEmpty()) {
                permissionGuidance = null
                viewModel.setPermissionGuidance(null)
                RecordingServiceController.startRecording(context)
            } else {
                permissionGuidance =
                    "Grant microphone, location, and notification access to begin recording."
                viewModel.setPermissionGuidance(permissionGuidance)
                permissionLauncher.launch(deniedPermissions.toTypedArray())
            }
        },
        onStopRecording = {
            permissionGuidance = null
            viewModel.setPermissionGuidance(null)
            RecordingServiceController.stopRecording(context)
        },
    )
}
