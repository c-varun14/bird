package com.example.projectbird.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.projectbird.data.local.entity.AnalysisResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysisResult(result: AnalysisResultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysisResults(results: List<AnalysisResultEntity>)

    @Query("SELECT * FROM analysis_results WHERE id = :analysisResultId LIMIT 1")
    suspend fun getAnalysisResultById(analysisResultId: String): AnalysisResultEntity?

    @Query("SELECT * FROM analysis_results WHERE capturePointId = :capturePointId LIMIT 1")
    suspend fun getAnalysisResultForCapture(capturePointId: String): AnalysisResultEntity?

    @Query("SELECT * FROM analysis_results WHERE capturePointId = :capturePointId LIMIT 1")
    fun observeAnalysisResultForCapture(capturePointId: String): Flow<AnalysisResultEntity?>

    @Query("SELECT * FROM analysis_results WHERE capturePointId IN (:capturePointIds)")
    suspend fun getAnalysisResultsForCaptures(capturePointIds: List<String>): List<AnalysisResultEntity>

    @Query(
        """
        DELETE FROM analysis_results
        WHERE capturePointId IN (
            SELECT id FROM capture_points WHERE sessionId = :sessionId
        )
        """
    )
    suspend fun deleteBySessionId(sessionId: String)

    @Query("DELETE FROM analysis_results WHERE capturePointId = :capturePointId")
    suspend fun deleteByCapturePointId(capturePointId: String)
}
