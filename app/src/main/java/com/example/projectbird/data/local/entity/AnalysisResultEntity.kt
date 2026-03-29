package com.example.projectbird.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "analysis_results",
    foreignKeys = [
        ForeignKey(
            entity = CapturePointEntity::class,
            parentColumns = ["id"],
            childColumns = ["capturePointId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["capturePointId"])
    ]
)
data class AnalysisResultEntity(
    @PrimaryKey
    val id: String,
    val capturePointId: String,
    val processedAt: Long,
    val processorType: String,
    val processorVersion: String,
    val visibility: String = "PRIVATE",
    val isSynced: Boolean = false
)
