package com.example.projectbird.core.service

import android.content.Context

data class PersistedRecordingState(
    val isRecording: Boolean,
    val sessionId: String?,
    val sessionName: String?,
    val startedAtMillis: Long?,
    val lastHeartbeatMillis: Long,
)

class RecordingSessionStateStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun markStarted(
        sessionId: String,
        sessionName: String,
        startedAtMillis: Long,
    ) {
        prefs.edit()
            .putBoolean(KEY_IS_RECORDING, true)
            .putString(KEY_SESSION_ID, sessionId)
            .putString(KEY_SESSION_NAME, sessionName)
            .putLong(KEY_STARTED_AT, startedAtMillis)
            .putLong(KEY_LAST_HEARTBEAT, System.currentTimeMillis())
            .apply()
    }

    fun markHeartbeat(timestampMillis: Long = System.currentTimeMillis()) {
        if (!prefs.getBoolean(KEY_IS_RECORDING, false)) return
        prefs.edit()
            .putLong(KEY_LAST_HEARTBEAT, timestampMillis)
            .apply()
    }

    fun markStopped() {
        prefs.edit()
            .putBoolean(KEY_IS_RECORDING, false)
            .remove(KEY_SESSION_ID)
            .remove(KEY_SESSION_NAME)
            .remove(KEY_STARTED_AT)
            .putLong(KEY_LAST_HEARTBEAT, System.currentTimeMillis())
            .apply()
    }

    fun read(): PersistedRecordingState {
        val isRecording = prefs.getBoolean(KEY_IS_RECORDING, false)
        val startedAt = if (prefs.contains(KEY_STARTED_AT)) {
            prefs.getLong(KEY_STARTED_AT, 0L)
        } else {
            null
        }

        return PersistedRecordingState(
            isRecording = isRecording,
            sessionId = prefs.getString(KEY_SESSION_ID, null),
            sessionName = prefs.getString(KEY_SESSION_NAME, null),
            startedAtMillis = startedAt,
            lastHeartbeatMillis = prefs.getLong(KEY_LAST_HEARTBEAT, 0L),
        )
    }

    companion object {
        private const val PREFS_NAME = "recording_session_state"
        private const val KEY_IS_RECORDING = "is_recording"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_SESSION_NAME = "session_name"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_LAST_HEARTBEAT = "last_heartbeat"
    }
}
