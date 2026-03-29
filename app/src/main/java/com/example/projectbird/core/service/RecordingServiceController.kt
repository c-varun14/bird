package com.example.projectbird.core.service

import android.content.Context
import android.content.Intent
import android.os.Build

object RecordingServiceController {

    fun startRecording(context: Context) {
        val intent = Intent(context, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_START
        }
        startForegroundServiceCompat(context, intent)
    }

    fun stopRecording(context: Context) {
        val intent = Intent(context, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_STOP
        }
        startForegroundServiceCompat(context, intent)
    }

    private fun startForegroundServiceCompat(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
