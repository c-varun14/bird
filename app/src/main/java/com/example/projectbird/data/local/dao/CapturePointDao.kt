package com.example.projectbird.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.projectbird.data.local.entity.CapturePointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturePointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCapturePoint(capturePoint: CapturePointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCapturePoints(capturePoints: List<CapturePointEntity>)

    @Query(
        """
        SELECT * FROM capture_points
        WHERE sessionId = :sessionId
        ORDER BY timestamp DESC
        """
    )
    fun observeCapturePointsBySession(sessionId: String): Flow<List<CapturePointEntity>>

    @Query(
        """
        SELECT * FROM capture_points
        WHERE sessionId = :sessionId
        ORDER BY timestamp DESC
        """
    )
    suspend fun getCapturePointsBySession(sessionId: String): List<CapturePointEntity>

    @Query(
        """
        SELECT * FROM capture_points
        ORDER BY timestamp DESC
        """
    )
    fun observeAllCapturePoints(): Flow<List<CapturePointEntity>>

    @Query(
        """
        SELECT * FROM capture_points
        ORDER BY timestamp DESC
        """
    )
    suspend fun getAllCapturePoints(): List<CapturePointEntity>

    @Query(
        """
        SELECT COUNT(*) FROM capture_points
        WHERE sessionId = :sessionId
        """
    )
    suspend fun getCaptureCountForSession(sessionId: String): Int

    @Query(
        """
        SELECT * FROM capture_points
        WHERE latitude IS NOT NULL AND longitude IS NOT NULL
        ORDER BY timestamp DESC
        """
    )
    suspend fun getMapCapturePoints(): List<CapturePointEntity>

    @Query(
        """
        SELECT * FROM capture_points
        WHERE intensity IS NOT NULL
        ORDER BY timestamp ASC
        """
    )
    suspend fun getIntensityTimeline(): List<CapturePointEntity>

    @Query(
        """
        DELETE FROM capture_points
        WHERE sessionId = :sessionId
        """
    )
    suspend fun deleteCapturePointsBySession(sessionId: String)

    @Query(
        """
        DELETE FROM capture_points
        WHERE id = :capturePointId
        """
    )
    suspend fun deleteCapturePointById(capturePointId: String)
}
