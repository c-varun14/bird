package com.example.projectbird.core.audio

import java.io.File

data class RecordedAudioChunk(
    val file: File,
    val durationMs: Long,
    val averageAmplitude: Float,
)

interface AudioRecorder {
    suspend fun recordChunk(
        outputFile: File,
        durationMs: Long,
    ): RecordedAudioChunk

    fun stop()

    fun isRecording(): Boolean

    fun release()
}
