package com.example.projectbird.core.common

import java.time.Instant

interface TimeProvider {
    fun now(): Instant
}

object SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Instant.now()
}
