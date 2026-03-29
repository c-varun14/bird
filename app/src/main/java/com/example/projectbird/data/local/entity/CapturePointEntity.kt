package com.example.projectbird.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "capture_points",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index("timestamp")
    ]
)
data class CapturePointEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val timestamp: Long,
    val latitude: Double?,
    val longitude: Double?,
    val audioFilePath: String,
    val durationMs: Long,
    val intensity: Float,
    val environmentLabel: EnvironmentLabel,
    val retentionReason: RetentionReason,
    val visibility: Visibility = Visibility.PRIVATE,
    val isSynced: Boolean = false
)

enum class EnvironmentLabel {
    QUIET,
    MODERATE,
    ACTIVE,
    NOISY
}

enum class RetentionReason {
    DETECTION,
    INTENSITY,
    BOTH
}

enum class Visibility {
    PRIVATE,
    PUBLIC
}
