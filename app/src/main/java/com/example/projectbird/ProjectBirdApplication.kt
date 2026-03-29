package com.example.projectbird

import android.app.Application
import androidx.room.Room
import com.example.projectbird.data.local.db.AppDatabase
import com.example.projectbird.data.repository.SessionRepository

class ProjectBirdApplication : Application() {

    val appContainer: AppContainer by lazy {
        AppContainer(this)
    }
}

class AppContainer(
    application: Application,
) {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            application,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(
            sessionDao = database.sessionDao(),
        )
    }

    val capturePointDao by lazy { database.capturePointDao() }
    val analysisResultDao by lazy { database.analysisResultDao() }
    val detectedEntityDao by lazy { database.detectedEntityDao() }
}
