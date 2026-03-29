package com.example.projectbird.app

import android.content.Context
import androidx.room.Room
import com.example.projectbird.data.local.db.AppDatabase
import com.example.projectbird.data.repository.SessionRepository

class ProjectBirdContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(
            sessionDao = database.sessionDao()
        )
    }
}
