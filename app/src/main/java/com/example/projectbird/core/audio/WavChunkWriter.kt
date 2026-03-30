package com.example.projectbird.core.audio

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavChunkWriter {

    fun writeMono16BitWav(
        outputFile: File,
        samples: FloatArray,
        sampleRateHz: Int,
    ) {
        outputFile.parentFile?.mkdirs()

        val pcmData = ByteArray(samples.size * BYTES_PER_SAMPLE)
        var offset = 0
        samples.forEach { sample ->
            val clamped = sample.coerceIn(-1f, 1f)
            val shortValue = (clamped * Short.MAX_VALUE).toInt().toShort()
            pcmData[offset] = (shortValue.toInt() and 0xFF).toByte()
            pcmData[offset + 1] = ((shortValue.toInt() shr 8) and 0xFF).toByte()
            offset += 2
        }

        val header = createWavHeader(
            pcmDataSize = pcmData.size,
            sampleRateHz = sampleRateHz,
        )

        FileOutputStream(outputFile).use { stream ->
            stream.write(header)
            stream.write(pcmData)
            stream.flush()
        }
    }

    private fun createWavHeader(
        pcmDataSize: Int,
        sampleRateHz: Int,
    ): ByteArray {
        val byteRate = sampleRateHz * CHANNEL_COUNT * BYTES_PER_SAMPLE
        val blockAlign = (CHANNEL_COUNT * BYTES_PER_SAMPLE).toShort()
        val bitsPerSample = (BYTES_PER_SAMPLE * 8).toShort()

        val totalDataLen = pcmDataSize + 36
        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalDataLen)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(CHANNEL_COUNT.toShort())
        buffer.putInt(sampleRateHz)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign)
        buffer.putShort(bitsPerSample)
        buffer.put("data".toByteArray())
        buffer.putInt(pcmDataSize)

        return buffer.array()
    }

    companion object {
        private const val CHANNEL_COUNT = 1
        private const val BYTES_PER_SAMPLE = 2
    }
}
