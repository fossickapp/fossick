package com.fossick.app.data.local

import androidx.room.Entity

import androidx.room.Index
import androidx.room.PrimaryKey

object OcrStatus {
    const val PENDING = "PENDING"
    const val PROCESSING = "PROCESSING"
    const val INDEXED = "INDEXED"
    const val FAILED = "FAILED"
    const val UNSUPPORTED = "UNSUPPORTED"
}

@Entity(
    tableName = "screenshots",
    indices = [Index(value = ["ocrStatus"]), Index(value = ["displayName"])]
)
data class ScreenshotEntity(
    @PrimaryKey val mediaStoreId: Long,
    val uri: String,
    val displayName: String,
    val relativePath: String?,
    val mimeType: String,
    val category: String,
    val size: Long,
    val sourceType: String,
    val dateAdded: Long,
    val dateModified: Long,
    val ocrText: String,
    val normalizedText: String,
    val structuredEntities: String,
    val ocrStatus: String,
    val contentIndexVersion: Int,
    val lastIndexedModifiedTime: Long,
    val lastIndexedAt: Long,
    val errorState: String?
)
