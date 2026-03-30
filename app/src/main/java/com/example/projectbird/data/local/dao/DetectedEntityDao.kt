package com.example.projectbird.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.projectbird.data.local.entity.DetectedEntityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectedEntityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(detectedEntity: DetectedEntityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(detectedEntities: List<DetectedEntityEntity>)

    @Query(
        """
        SELECT *
        FROM detected_entities
        WHERE analysisResultId = :analysisResultId
        ORDER BY confidence DESC
        """
    )
    suspend fun getByAnalysisResultId(analysisResultId: String): List<DetectedEntityEntity>

    @Query(
        """
        SELECT entityName, COUNT(*) AS detectionCount
        FROM detected_entities
        GROUP BY entityName
        ORDER BY detectionCount DESC, entityName ASC
        """
    )
    fun getEntityFrequency(): Flow<List<EntityFrequencyRow>>

    @Query(
        """
        SELECT entityName, COUNT(*) AS detectionCount
        FROM detected_entities
        GROUP BY entityName
        ORDER BY detectionCount DESC, entityName ASC
        LIMIT :limit
        """
    )
    suspend fun getTopEntities(limit: Int): List<EntityFrequencyRow>

    @Query(
        """
        SELECT de.entityName AS entityName, COUNT(*) AS detectionCount
        FROM detected_entities de
        INNER JOIN analysis_results ar ON de.analysisResultId = ar.id
        INNER JOIN capture_points cp ON ar.capturePointId = cp.id
        WHERE cp.sessionId = :sessionId
        GROUP BY de.entityName
        ORDER BY detectionCount DESC, de.entityName ASC
        LIMIT 1
        """
    )
    suspend fun getTopEntityForSession(sessionId: String): EntityFrequencyRow?

    @Query(
        """
        SELECT ar.capturePointId AS capturePointId, COUNT(de.id) AS detectionCount
        FROM analysis_results ar
        LEFT JOIN detected_entities de ON de.analysisResultId = ar.id
        GROUP BY ar.capturePointId
        """
    )
    suspend fun getDetectionCountsByCapturePoint(): List<CaptureDetectionCountRow>

    @Query(
        """
        SELECT ar.capturePointId AS capturePointId, de.entityName AS entityName, COUNT(de.id) AS detectionCount
        FROM analysis_results ar
        INNER JOIN detected_entities de ON de.analysisResultId = ar.id
        GROUP BY ar.capturePointId, de.entityName
        ORDER BY ar.capturePointId ASC, detectionCount DESC, de.entityName ASC
        """
    )
    suspend fun getSpeciesCountsByCapturePoint(): List<CaptureSpeciesCountRow>

    @Query(
        """
        DELETE FROM detected_entities
        WHERE analysisResultId IN (
            SELECT id FROM analysis_results WHERE capturePointId IN (
                SELECT id FROM capture_points WHERE sessionId = :sessionId
            )
        )
        """
    )
    suspend fun deleteBySessionId(sessionId: String)

    @Query("DELETE FROM detected_entities")
    suspend fun clearAll()
}

data class EntityFrequencyRow(
    val entityName: String,
    val detectionCount: Int
)

data class CaptureDetectionCountRow(
    val capturePointId: String,
    val detectionCount: Int,
)

data class CaptureSpeciesCountRow(
    val capturePointId: String,
    val entityName: String,
    val detectionCount: Int,
)
