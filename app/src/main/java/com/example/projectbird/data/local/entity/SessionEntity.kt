package com.example.projectbird.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val startTime: Long,
    val endTime: Long? = null,
    val isActive: Boolean = false,
    val createdAt: Long,
    val visibility: String = "PRIVATE",
    val isSynced: Boolean = false,
)
