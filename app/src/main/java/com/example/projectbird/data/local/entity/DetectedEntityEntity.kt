package com.example.projectbird.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "detected_entities",
    foreignKeys = [
        ForeignKey(
            entity = AnalysisResultEntity::class,
            parentColumns = ["id"],
            childColumns = ["analysisResultId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["analysisResultId"]),
        Index(value = ["entityName"])
    ]
)
data class DetectedEntityEntity(
    @PrimaryKey
    val id: String,
    val analysisResultId: String,
    val entityName: String,
    val confidence: Float,
    val visibility: String = "PRIVATE",
    val isSynced: Boolean = false
)
