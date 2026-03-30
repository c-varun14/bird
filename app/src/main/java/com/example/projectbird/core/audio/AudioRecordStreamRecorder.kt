package com.example.projectbird.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.IOException
import kotlin.math.sqrt

data class PcmAudioFrame(
    val samples: FloatArray,
    val sampleRateHz: Int,
    val rms: Float,
)

interface StreamingAudioRecorder {
    fun start()

    fun readFrame(sampleCount: Int): PcmAudioFrame

    fun stop()

    fun release()
}

class AudioRecordStreamRecorder(
    private val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
) : StreamingAudioRecorder {

    private var audioRecord: AudioRecord? = null

    override fun start() {
        if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        if (minBufferSize <= 0) {
            throw IOException("Unable to initialize AudioRecord buffer")
        }

        val localRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )

        if (localRecord.state != AudioRecord.STATE_INITIALIZED) {
            localRecord.release()
            throw IOException("AudioRecord failed to initialize")
        }

        localRecord.startRecording()
        audioRecord = localRecord
    }

    override fun readFrame(sampleCount: Int): PcmAudioFrame {
        val localRecord = audioRecord ?: throw IllegalStateException("Recorder not started")
        val shortBuffer = ShortArray(sampleCount)
        var filled = 0

        while (filled < sampleCount) {
            val read = localRecord.read(
                shortBuffer,
                filled,
                sampleCount - filled,
                AudioRecord.READ_BLOCKING,
            )
            if (read <= 0) {
                throw IOException("AudioRecord read failed with code $read")
            }
            filled += read
        }

        val floatSamples = FloatArray(sampleCount)
        var powerSum = 0.0
        for (index in shortBuffer.indices) {
            val normalized = (shortBuffer[index] / MAX_PCM_16).coerceIn(-1f, 1f)
            floatSamples[index] = normalized
            powerSum += normalized * normalized
        }

        val rms = sqrt(powerSum / sampleCount.toDouble()).toFloat().coerceIn(0f, 1f)

        return PcmAudioFrame(
            samples = floatSamples,
            sampleRateHz = sampleRateHz,
            rms = rms,
        )
    }

    override fun stop() {
        audioRecord?.let { record ->
            runCatching { record.stop() }
        }
    }

    override fun release() {
        audioRecord?.let { record ->
            runCatching { record.stop() }
            runCatching { record.release() }
        }
        audioRecord = null
    }

    companion object {
        private const val DEFAULT_SAMPLE_RATE_HZ = 48_000
        private const val MAX_PCM_16 = 32_768f
    }
}
