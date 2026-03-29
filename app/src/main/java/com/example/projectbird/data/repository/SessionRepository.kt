package com.example.projectbird.data.repository

import com.example.projectbird.data.local.dao.SessionDao
import com.example.projectbird.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

class SessionRepository(
    private val sessionDao: SessionDao
) {
    fun observeSessions(): Flow<List<SessionEntity>> = sessionDao.observeAllSessions()

    fun observeActiveSession(): Flow<SessionEntity?> = sessionDao.observeActiveSession()

    suspend fun observeSessionsSnapshot(): List<SessionEntity> = sessionDao.getAllSessions()

    suspend fun getActiveSession(): SessionEntity? = sessionDao.getActiveSession()

    suspend fun getSessionById(sessionId: String): SessionEntity? =
        sessionDao.getSessionById(sessionId)

    suspend fun startSession(name: String? = null): SessionEntity {
        val now = Instant.now().toEpochMilli()
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            name = name ?: defaultSessionName(now),
            startTime = now,
            endTime = null,
            isActive = true,
            createdAt = now,
            visibility = "PRIVATE",
            isSynced = false
        )

        sessionDao.clearActiveSessions()
        sessionDao.upsertSession(session)
        return session
    }

    suspend fun stopSession(
        sessionId: String,
        endedAtMillis: Long = Instant.now().toEpochMilli()
    ) {
        sessionDao.endSession(
            sessionId = sessionId,
            endTime = endedAtMillis
        )
    }

    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSessionById(sessionId)
    }

    private fun defaultSessionName(startedAtMillis: Long): String {
        return "Session ${Instant.ofEpochMilli(startedAtMillis)}"
    }
}
