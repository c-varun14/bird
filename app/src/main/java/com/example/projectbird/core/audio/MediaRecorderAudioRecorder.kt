package com.example.projectbird.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException

class MediaRecorderAudioRecorder(
    private val context: Context,
) : AudioRecorder {

    private var mediaRecorder: MediaRecorder? = null

    @Volatile
    private var recording = false

    override suspend fun recordChunk(
        outputFile: File,
        durationMs: Long,
    ): RecordedAudioChunk {
        require(durationMs > 0) { "durationMs must be > 0" }

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val recorder = createRecorder(outputFile)
        mediaRecorder = recorder
        recording = true

        return try {
            recorder.prepare()
            recorder.start()

            val amplitudes = mutableListOf<Int>()
            val sampleCount = maxOf(1, (durationMs / AMPLITUDE_SAMPLE_INTERVAL_MS).toInt())

            repeat(sampleCount) {
                delay(AMPLITUDE_SAMPLE_INTERVAL_MS)
                amplitudes += readAmplitudeSafely(recorder)
            }

            stopRecorderSafely(recorder)

            RecordedAudioChunk(
                file = outputFile,
                durationMs = durationMs,
                averageAmplitude = amplitudes.averageNormalizedAmplitude(),
            )
        } catch (error: Exception) {
            stopRecorderSafely(recorder)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw IOException(
                "Failed to record audio chunk at ${outputFile.absolutePath}",
                error,
            )
        } finally {
            releaseRecorder(recorder)
            if (mediaRecorder === recorder) {
                mediaRecorder = null
            }
            recording = false
        }
    }

    override fun stop() {
        mediaRecorder?.let { stopRecorderSafely(it) }
        recording = false
    }

    override fun isRecording(): Boolean = recording

    override fun release() {
        mediaRecorder?.let { releaseRecorder(it) }
        mediaRecorder = null
        recording = false
    }

    private fun createRecorder(outputFile: File): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(AUDIO_BIT_RATE)
            setAudioSamplingRate(AUDIO_SAMPLE_RATE)
            setOutputFile(outputFile.absolutePath)
        }
    }

    private fun stopRecorderSafely(recorder: MediaRecorder) {
        try {
            recorder.stop()
        } catch (_: RuntimeException) {
        } catch (_: IllegalStateException) {
        }
    }

    private fun releaseRecorder(recorder: MediaRecorder) {
        try {
            recorder.reset()
        } catch (_: Exception) {
        }

        try {
            recorder.release()
        } catch (_: Exception) {
        }
    }

    private fun readAmplitudeSafely(recorder: MediaRecorder): Int {
        return try {
            recorder.maxAmplitude
        } catch (_: Exception) {
            0
        }
    }

    private fun List<Int>.averageNormalizedAmplitude(): Float {
        if (isEmpty()) return 0f
        val average = average().toFloat()
        return (average / MAX_AMPLITUDE).coerceIn(0f, 1f)
    }

    companion object {
        private const val AUDIO_BIT_RATE = 64_000
        private const val AUDIO_SAMPLE_RATE = 16_000
        private const val AMPLITUDE_SAMPLE_INTERVAL_MS = 200L
        private const val MAX_AMPLITUDE = 32_767f
    }
}
