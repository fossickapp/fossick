package com.fossick.app.data.model

import com.fossick.app.data.local.ScreenshotSyncMetadata

object IndexingPolicy {
    fun contentChanged(
        previous: ScreenshotSyncMetadata?,
        dateModified: Long,
        size: Long,
        mimeType: String,
        contentIndexVersion: Int
    ): Boolean = previous == null ||
        previous.lastIndexedModifiedTime != dateModified ||
        previous.size != size ||
        previous.mimeType != mimeType ||
        previous.contentIndexVersion != contentIndexVersion
}
