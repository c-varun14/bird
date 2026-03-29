package com.example.projectbird.core.location

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val timestampMillis: Long,
)

interface LocationProvider {
    suspend fun start()
    suspend fun stop()
    suspend fun getLatestLocation(): LocationSnapshot?
}
