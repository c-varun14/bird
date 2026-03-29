package com.example.projectbird.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.projectbird.data.local.converter.DbConverters
import com.example.projectbird.data.local.dao.AnalysisResultDao
import com.example.projectbird.data.local.dao.CapturePointDao
import com.example.projectbird.data.local.dao.DetectedEntityDao
import com.example.projectbird.data.local.dao.SessionDao
import com.example.projectbird.data.local.entity.AnalysisResultEntity
import com.example.projectbird.data.local.entity.CapturePointEntity
import com.example.projectbird.data.local.entity.DetectedEntityEntity
import com.example.projectbird.data.local.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        CapturePointEntity::class,
        AnalysisResultEntity::class,
        DetectedEntityEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DbConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun capturePointDao(): CapturePointDao
    abstract fun analysisResultDao(): AnalysisResultDao
    abstract fun detectedEntityDao(): DetectedEntityDao

    companion object {
        const val DATABASE_NAME: String = "project_bird.db"
    }
}
