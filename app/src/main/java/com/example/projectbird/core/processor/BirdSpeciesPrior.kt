package com.example.projectbird.core.processor

import java.time.Instant
import java.time.ZoneOffset

class BirdSpeciesPrior {

    fun rescore(
        detections: List<DetectedItem>,
        latitude: Double?,
        longitude: Double?,
        timestampMillis: Long,
    ): List<DetectedItem> {
        if (detections.isEmpty() || latitude == null || longitude == null) return detections

        val month = Instant.ofEpochMilli(timestampMillis)
            .atZone(ZoneOffset.UTC)
            .monthValue

        return detections.map { item ->
            val multiplier = priorMultiplier(
                speciesName = item.entityName,
                latitude = latitude,
                month = month,
            )
            item.copy(confidence = (item.confidence * multiplier).coerceIn(0f, 1f))
        }
    }

    private fun priorMultiplier(
        speciesName: String,
        latitude: Double,
        month: Int,
    ): Float {
        val name = speciesName.lowercase()
        val tropicalBand = latitude in -24.0..24.0
        val temperateBand = latitude <= -24.0 || latitude >= 24.0

        return when {
            ("sparrow" in name || "bulbul" in name || "myna" in name || "sunbird" in name) && tropicalBand -> 1.12f
            ("warbler" in name || "thrush" in name || "finch" in name) && temperateBand && month in 3..8 -> 1.12f
            ("duck" in name || "goose" in name) && month in setOf(10, 11, 12, 1, 2) -> 1.10f
            ("owl" in name || "nightjar" in name) -> 1.06f
            else -> 1.0f
        }
    }
}
