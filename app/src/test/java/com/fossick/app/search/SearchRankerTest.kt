package com.fossick.app.search

import com.fossick.app.data.local.ScreenshotEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRankerTest {
    @Test
    fun strongerOldMatchOutranksWeakerNewMatch() {
        val strongOld = memory(1L, "inheritance assignment polymorphism", modified = 1L)
        val weakNew = memory(2L, "college assignment", modified = 2L)

        val ranked = SearchRanker.rank(
            listOf(weakNew, strongOld),
            QueryProcessor.process("inheritance assignment")
        )

        assertEquals(listOf(strongOld, weakNew), ranked)
    }

    @Test
    fun newerWinsWhenRelevanceIsComparable() {
        val older = memory(1L, "assignment notes", modified = 1L)
        val newer = memory(2L, "assignment notes", modified = 2L)

        val ranked = SearchRanker.rank(listOf(older, newer), QueryProcessor.process("assignment"))

        assertEquals(listOf(newer, older), ranked)
    }

    @Test
    fun deterministicMoneyEntityEvidenceRanksPaymentMemory() {
        val payment = memory(1L, "Paid successfully to Rahul Shaikh", modified = 1L).copy(
            normalizedText = "paid successfully to rahul shaikh 30 inr",
            structuredEntities = "MONEY:30:INR"
        )
        val generic = memory(2L, "30 assignment notes", modified = 2L)

        val ranked = SearchRanker.rank(
            listOf(generic, payment),
            QueryProcessor.process("when I paid 30 rupees")
        )

        assertEquals(listOf(payment, generic), ranked)
    }

    private fun memory(id: Long, text: String, modified: Long) = ScreenshotEntity(
        mediaStoreId = id,
        uri = "content://media/$id",
        displayName = "memory_$id.png",
        relativePath = "Pictures/Screenshots",
        mimeType = "image/png",
        category = "SCREENSHOT",
        size = 100L,
        sourceType = "MEDIASTORE",
        dateAdded = modified,
        dateModified = modified,
        ocrText = text,
        normalizedText = text,
        structuredEntities = "",
        ocrStatus = "INDEXED",
        contentIndexVersion = 1,
        lastIndexedModifiedTime = modified,
        lastIndexedAt = modified,
        errorState = null
    )
}