package com.fossick.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryCategoryTest {
    @Test
    fun mapsSupportedMediaToUserFacingCategories() {
        assertEquals(MemoryCategory.SCREENSHOT, MemoryCategory.fromMedia("image/png", "Screenshot_1.png", "Pictures/"))
        assertEquals(MemoryCategory.CAMERA, MemoryCategory.fromMedia("image/jpeg", "trip.jpg", "DCIM/Camera/"))
        assertEquals(MemoryCategory.IMAGE, MemoryCategory.fromMedia("image/jpeg", "trip.jpg", "Pictures/Trips/"))
        assertEquals(MemoryCategory.VIDEO, MemoryCategory.fromMedia("video/mp4", "clip.mp4", null))
        assertEquals(MemoryCategory.PDF, MemoryCategory.fromMedia("application/pdf", "notes.pdf", null))
        assertEquals(MemoryCategory.DOCUMENT, MemoryCategory.fromMedia("text/plain", "notes.txt", null))
        assertEquals(
            MemoryCategory.PRESENTATION,
            MemoryCategory.fromMedia("application/octet-stream", "slides.pptx", null)
        )
        assertEquals(
            MemoryCategory.SPREADSHEET,
            MemoryCategory.fromMedia("application/octet-stream", "budget.xlsx", null)
        )
    }

    @Test
    fun doesNotExposeUnknownMimeAsSupportedContent() {
        assertNull(MemoryCategory.fromMedia("application/octet-stream", "archive.bin", null))
    }

    @Test
    fun genericImagesExcludeScreenshotAndCameraCategories() {
        assertEquals(true, MemoryCategory.matchesFilter("IMAGES", MemoryCategory.IMAGE))
        assertEquals(false, MemoryCategory.matchesFilter("IMAGES", MemoryCategory.SCREENSHOT))
        assertEquals(false, MemoryCategory.matchesFilter("IMAGES", MemoryCategory.CAMERA))
        assertEquals(true, MemoryCategory.matchesFilter(MemoryCategory.ALL, MemoryCategory.PDF))
    }
}