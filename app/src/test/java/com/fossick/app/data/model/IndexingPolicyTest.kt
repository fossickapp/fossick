package com.fossick.app.data.model

import com.fossick.app.data.local.ScreenshotSyncMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexingPolicyTest {
    @Test
    fun unchangedMetadataPreservesExistingIndexedContent() {
        val indexed = existing(status = "INDEXED")

        assertFalse(IndexingPolicy.contentChanged(indexed, 100L, 900L, "image/png", 1))
    }

    @Test
    fun changedSourceMetadataOrIndexVersionQueuesNewUnderstanding() {
        val indexed = existing(status = "INDEXED")

        assertTrue(IndexingPolicy.contentChanged(indexed, 101L, 900L, "image/png", 1))
        assertTrue(IndexingPolicy.contentChanged(indexed, 100L, 901L, "image/png", 1))
        assertTrue(IndexingPolicy.contentChanged(indexed, 100L, 900L, "image/jpeg", 1))
        assertTrue(IndexingPolicy.contentChanged(indexed, 100L, 900L, "image/png", 2))
    }

    private fun existing(status: String) = ScreenshotSyncMetadata(
        mediaStoreId = 1L,
        uri = "content://media/1",
        displayName = "memory.png",
        relativePath = "Pictures",
        mimeType = "image/png",
        category = "IMAGE",
        size = 900L,
        sourceType = "MEDIASTORE",
        dateAdded = 90L,
        dateModified = 100L,
        lastIndexedModifiedTime = 100L,
        contentIndexVersion = 1,
        ocrStatus = status
    )
}
