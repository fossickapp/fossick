package com.fossick.app.data.model

import android.net.Uri

data class ScreenshotItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val dateModified: Long,
    val relativePath: String?,
    val mimeType: String = "image/*",
    val category: String = "SCREENSHOT",
    val size: Long = 0L,
    val sourceType: String = "MEDIASTORE"
)