package com.fossick.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

data class ScreenshotSyncMetadata(
    val mediaStoreId: Long,
    val uri: String,
    val displayName: String,
    val relativePath: String?,
    val mimeType: String,
    val category: String,
    val size: Long,
    val sourceType: String,
    val dateAdded: Long,
    val dateModified: Long,
    val lastIndexedModifiedTime: Long,
    val contentIndexVersion: Int,
    val ocrStatus: String
)

data class ScreenshotIndexAggregate(
    val category: String,
    val sourceType: String,
    val ocrStatus: String,
    val count: Int
)

@Dao
interface ScreenshotDao {
    @Query("SELECT * FROM screenshots ORDER BY dateAdded DESC")
    suspend fun getAll(): List<ScreenshotEntity>

    @Query("SELECT * FROM screenshots WHERE (:category = 'ALL' OR category = :category OR (:category = 'IMAGES' AND category = 'IMAGE') OR (:category = 'DOCUMENTS' AND category = 'DOCUMENT')) ORDER BY dateModified DESC, dateAdded DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(category: String, limit: Int, offset: Int): List<ScreenshotEntity>

    @Query("SELECT mediaStoreId, uri, displayName, relativePath, mimeType, category, size, sourceType, dateAdded, dateModified, lastIndexedModifiedTime, contentIndexVersion, ocrStatus FROM screenshots WHERE mediaStoreId IN (:ids)")
    suspend fun getSyncMetadata(ids: List<Long>): List<ScreenshotSyncMetadata>

    @Query("SELECT * FROM screenshots WHERE ocrStatus = 'PENDING' ORDER BY dateAdded DESC LIMIT :limit")
    suspend fun getPending(limit: Int): List<ScreenshotEntity>

    @Query("UPDATE screenshots SET ocrStatus = 'PROCESSING' WHERE mediaStoreId = :mediaStoreId AND ocrStatus = 'PENDING'")
    suspend fun markProcessing(mediaStoreId: Long)

    @Query("SELECT * FROM screenshots WHERE (ocrText LIKE '%' || :query || '%' COLLATE NOCASE OR normalizedText LIKE '%' || :query || '%' COLLATE NOCASE OR structuredEntities LIKE '%' || :query || '%' COLLATE NOCASE OR displayName LIKE '%' || :query || '%' COLLATE NOCASE OR relativePath LIKE '%' || :query || '%' COLLATE NOCASE OR category LIKE '%' || :query || '%' COLLATE NOCASE OR mimeType LIKE '%' || :query || '%' COLLATE NOCASE) ORDER BY CASE WHEN displayName LIKE '%' || :query || '%' COLLATE NOCASE THEN 0 ELSE 1 END, CASE WHEN structuredEntities LIKE '%' || :query || '%' COLLATE NOCASE THEN 0 ELSE 1 END, CASE WHEN normalizedText LIKE '%' || :query || '%' COLLATE NOCASE OR ocrText LIKE '%' || :query || '%' COLLATE NOCASE THEN 0 ELSE 1 END, dateModified DESC, dateAdded DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<ScreenshotEntity>

    @Query("SELECT COUNT(*) FROM screenshots")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM screenshots WHERE category = :category")
    suspend fun countCategory(category: String): Int

    @Query("SELECT COUNT(*) FROM screenshots WHERE ocrStatus = 'INDEXED'")
    suspend fun countIndexed(): Int

    @Query("SELECT COUNT(*) FROM screenshots WHERE ocrStatus = 'PENDING' OR ocrStatus = 'PROCESSING'")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM screenshots WHERE ocrStatus = 'FAILED'")
    suspend fun countFailed(): Int

    @Query("SELECT COUNT(*) FROM screenshots WHERE ocrStatus = 'UNSUPPORTED'")
    suspend fun countUnsupported(): Int

    @Query("SELECT category, sourceType, ocrStatus, COUNT(*) AS count FROM screenshots GROUP BY category, sourceType, ocrStatus ORDER BY category, sourceType, ocrStatus")
    suspend fun getIndexAggregateCounts(): List<ScreenshotIndexAggregate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ScreenshotEntity>)

    @Query("UPDATE screenshots SET ocrStatus = 'PENDING' WHERE ocrStatus = 'PROCESSING'")
    suspend fun resetProcessing()

    @Query("SELECT mediaStoreId FROM screenshots WHERE sourceType = :sourceType AND mediaStoreId > :afterId ORDER BY mediaStoreId LIMIT :limit")
    suspend fun getIdsAfter(sourceType: String, afterId: Long, limit: Int): List<Long>

    @Query("DELETE FROM screenshots WHERE mediaStoreId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM screenshots")
    suspend fun deleteAll()

    @Query("UPDATE screenshots SET ocrStatus = :status, ocrText = :ocrText, normalizedText = :normalizedText, structuredEntities = :structuredEntities, lastIndexedModifiedTime = :indexedModifiedTime, lastIndexedAt = :indexedAt, errorState = :errorState WHERE mediaStoreId = :mediaStoreId")
    suspend fun updateOcr(mediaStoreId: Long, status: String, ocrText: String, normalizedText: String, structuredEntities: String, indexedModifiedTime: Long, indexedAt: Long, errorState: String?)

    @Query("UPDATE screenshots SET uri = :uri, displayName = :displayName, relativePath = :relativePath, mimeType = :mimeType, category = :category, size = :size, sourceType = :sourceType, dateAdded = :dateAdded, dateModified = :dateModified WHERE mediaStoreId = :mediaStoreId")
    suspend fun updateMetadata(mediaStoreId: Long, uri: String, displayName: String, relativePath: String?, mimeType: String, category: String, size: Long, sourceType: String, dateAdded: Long, dateModified: Long)
}
