package com.example.projectbird.core.audio

class FloatRingBuffer(
    capacity: Int,
) {
    private val buffer = FloatArray(capacity.coerceAtLeast(1))
    private var writeIndex: Int = 0
    private var count: Int = 0

    val size: Int
        get() = count

    fun append(samples: FloatArray) {
        if (samples.isEmpty()) return

        samples.forEach { value ->
            buffer[writeIndex] = value
            writeIndex = (writeIndex + 1) % buffer.size
            if (count < buffer.size) {
                count++
            }
        }
    }

    fun latest(sampleCount: Int): FloatArray {
        val requested = sampleCount.coerceAtLeast(0)
        val actualSize = requested.coerceAtMost(count)
        val output = FloatArray(actualSize)

        if (actualSize == 0) return output

        val startIndex = (writeIndex - actualSize + buffer.size) % buffer.size

        for (i in 0 until actualSize) {
            output[i] = buffer[(startIndex + i) % buffer.size]
        }

        return output
    }

    fun clear() {
        writeIndex = 0
        count = 0
    }
}
