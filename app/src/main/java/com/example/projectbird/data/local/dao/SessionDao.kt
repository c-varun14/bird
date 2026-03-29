package com.example.projectbird.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.projectbird.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveSession(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE isActive = 1 LIMIT 1")
    fun observeActiveSession(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun observeAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    fun observeSessionById(sessionId: String): Flow<SessionEntity?>

    @Query(
        """
        UPDATE sessions
        SET endTime = :endTime,
            isActive = 0
        WHERE id = :sessionId
        """
    )
    suspend fun endSession(
        sessionId: String,
        endTime: Long
    )

    @Query("UPDATE sessions SET isActive = 0")
    suspend fun clearActiveSessions()

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()
}
