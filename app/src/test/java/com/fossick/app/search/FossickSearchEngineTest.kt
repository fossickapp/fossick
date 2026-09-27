package com.fossick.app.search

import com.fossick.app.data.local.ScreenshotDao
import com.fossick.app.data.local.ScreenshotEntity
import com.fossick.app.data.local.ScreenshotIndexAggregate
import com.fossick.app.data.local.ScreenshotSyncMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FossickSearchEngineTest {
    private val apologyRecord = ScreenshotEntity(
        mediaStoreId = 1L,
        uri = "content://media/1",
        displayName = "Screenshot_20260914.png",
        relativePath = "Pictures/Screenshots",
        mimeType = "image/png",
        category = "SCREENSHOT",
        size = 1024L,
        sourceType = "MEDIASTORE",
        dateAdded = 1_790_000_000L,
        dateModified = 1_790_000_000L,
        ocrText = "I'm sorry about what happened yesterday",
        normalizedText = "i m sorry about what happened yesterday",
        structuredEntities = "",
        ocrStatus = "INDEXED",
        contentIndexVersion = 1,
        lastIndexedModifiedTime = 1_790_000_000L,
        lastIndexedAt = 1_790_000_000L,
        errorState = null
    )

    @Test
    fun retrievesOcrTextForShortAndNaturalQueries() = kotlinx.coroutines.runBlocking {
        val engine = FossickSearchEngine(SearchingScreenshotDao(apologyRecord))

        assertEquals(listOf(apologyRecord), engine.search("sorry"))
        assertEquals(listOf(apologyRecord), engine.search("text where she said sorry"))
    }

    @Test
    fun queryProcessorKeepsContentTermsStrong() {
        val query = QueryProcessor.process("text where she said sorry")

        assertTrue(query.strongTerms.contains("sorry"))
        assertTrue(query.contextTerms.containsAll(listOf("text", "where", "she", "said")))
    }

    @Test
    fun queryProcessorSeparatesTypeHintsAndMoneyClues() {
        val query = QueryProcessor.process("screenshot where sir sent 30 rupees")

        assertTrue(query.strongTerms.containsAll(listOf("sir", "sent", "30", "inr")))
        assertTrue(query.contextTerms.contains("where"))
        assertEquals(listOf("SCREENSHOT"), query.categoryHints)
        assertTrue(query.amountClues.any { it.startsWith("MONEY:30:") })
    }

    @Test
    fun queryProcessorSeparatesDownloadedSourceHint() {
        val query = QueryProcessor.process("document I downloaded yesterday")

        assertEquals(listOf("DOCUMENT", "PDF"), query.categoryHints)
        assertEquals(listOf("download"), query.sourceHints)
        assertTrue(query.strongTerms.contains("yesterday"))
    }

    @Test
    fun filenameAndContentBothParticipateInRetrieval() = kotlinx.coroutines.runBlocking {
        val record = apologyRecord.copy(
            displayName = "IMG_1234.jpg",
            ocrText = "DBMS normalization assignment",
            normalizedText = "dbms normalization assignment"
        )

        assertEquals(listOf(record), FossickSearchEngine(SearchingScreenshotDao(record)).search("normalization assignment"))
    }

    @Test
    fun diagnosticTraceReportsCandidatesAndMatchSignals() = kotlinx.coroutines.runBlocking {
        val engine = FossickSearchEngine(SearchingScreenshotDao(apologyRecord))
        val trace = engine.diagnose("text where she said sorry")

        assertEquals(1, trace.candidateCount)
        assertTrue(trace.strongTerms.contains("sorry"))
        assertEquals(apologyRecord.mediaStoreId, trace.rankedMatches.first().memory.mediaStoreId)
        assertTrue(trace.rankedMatches.first().matchedContentTerms.contains("sorry"))
    }

    @Test
    fun fuzzyNameQueriesStillRetrieveTheCorrectMemory() = kotlinx.coroutines.runBlocking {
        val paymentRecord = ScreenshotEntity(
            mediaStoreId = 2L,
            uri = "content://media/2",
            displayName = "Receipt_20260914.png",
            relativePath = "Documents/Payments",
            mimeType = "image/png",
            category = "SCREENSHOT",
            size = 2048L,
            sourceType = "MEDIASTORE",
            dateAdded = 1_790_100_000L,
            dateModified = 1_790_100_000L,
            ocrText = "Paid Successfully to Rahul Shaikh",
            normalizedText = "paid successfully to rahul shaikh",
            structuredEntities = "PERSON:Rahul Shaikh",
            ocrStatus = "INDEXED",
            contentIndexVersion = 1,
            lastIndexedModifiedTime = 1_790_100_000L,
            lastIndexedAt = 1_790_100_000L,
            errorState = null
        )

        val engine = FossickSearchEngine(FuzzySearchingScreenshotDao(paymentRecord))
        assertEquals(listOf(paymentRecord), engine.search("Rahool"))
    }
}

private class FuzzySearchingScreenshotDao(private val record: ScreenshotEntity) : ScreenshotDao {
    override suspend fun getAll(): List<ScreenshotEntity> = listOf(record)
    override suspend fun getPage(category: String, limit: Int, offset: Int): List<ScreenshotEntity> = emptyList()
    override suspend fun getSyncMetadata(ids: List<Long>): List<ScreenshotSyncMetadata> = emptyList()
    override suspend fun getPending(limit: Int): List<ScreenshotEntity> = emptyList()
    override suspend fun markProcessing(mediaStoreId: Long) = Unit
    override suspend fun search(query: String, limit: Int): List<ScreenshotEntity> =
        if (query == "rahul" || query == "paid" || query == "shaikh" || query == "successfully") listOf(record) else emptyList()
    override suspend fun countAll(): Int = 1
    override suspend fun countCategory(category: String): Int = 1
    override suspend fun countIndexed(): Int = 1
    override suspend fun countPending(): Int = 0
    override suspend fun countFailed(): Int = 0
    override suspend fun countUnsupported(): Int = 0
    override suspend fun getIndexAggregateCounts(): List<ScreenshotIndexAggregate> = emptyList()
    override suspend fun upsertAll(items: List<ScreenshotEntity>) = Unit
    override suspend fun resetProcessing() = Unit
    override suspend fun getIdsAfter(sourceType: String, afterId: Long, limit: Int): List<Long> = emptyList()
    override suspend fun deleteByIds(ids: List<Long>) = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun updateOcr(
        mediaStoreId: Long,
        status: String,
        ocrText: String,
        normalizedText: String,
        structuredEntities: String,
        indexedModifiedTime: Long,
        indexedAt: Long,
        errorState: String?
    ) = Unit

    override suspend fun updateMetadata(
        mediaStoreId: Long,
        uri: String,
        displayName: String,
        relativePath: String?,
        mimeType: String,
        category: String,
        size: Long,
        sourceType: String,
        dateAdded: Long,
        dateModified: Long
    ) = Unit
}

private class SearchingScreenshotDao(private val record: ScreenshotEntity) : ScreenshotDao {
    override suspend fun getAll(): List<ScreenshotEntity> = listOf(record)
    override suspend fun getPage(category: String, limit: Int, offset: Int): List<ScreenshotEntity> = emptyList()
    override suspend fun getSyncMetadata(ids: List<Long>): List<ScreenshotSyncMetadata> = emptyList()
    override suspend fun getPending(limit: Int): List<ScreenshotEntity> = emptyList()
    override suspend fun markProcessing(mediaStoreId: Long) = Unit
    override suspend fun search(query: String, limit: Int): List<ScreenshotEntity> =
        if (listOf(record.ocrText, record.normalizedText, record.displayName, record.relativePath.orEmpty(), record.structuredEntities, record.category, record.mimeType)
                .any { it.contains(query, ignoreCase = true) }
        ) listOf(record) else emptyList()
    override suspend fun countAll(): Int = 1
    override suspend fun countCategory(category: String): Int = 1
    override suspend fun countIndexed(): Int = 1
    override suspend fun countPending(): Int = 0
    override suspend fun countFailed(): Int = 0
    override suspend fun countUnsupported(): Int = 0
    override suspend fun getIndexAggregateCounts(): List<ScreenshotIndexAggregate> = emptyList()
    override suspend fun upsertAll(items: List<ScreenshotEntity>) = Unit
    override suspend fun resetProcessing() = Unit
    override suspend fun getIdsAfter(sourceType: String, afterId: Long, limit: Int): List<Long> = emptyList()
    override suspend fun deleteByIds(ids: List<Long>) = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun updateOcr(
        mediaStoreId: Long,
        status: String,
        ocrText: String,
        normalizedText: String,
        structuredEntities: String,
        indexedModifiedTime: Long,
        indexedAt: Long,
        errorState: String?
    ) = Unit

    override suspend fun updateMetadata(
        mediaStoreId: Long,
        uri: String,
        displayName: String,
        relativePath: String?,
        mimeType: String,
        category: String,
        size: Long,
        sourceType: String,
        dateAdded: Long,
        dateModified: Long
    ) = Unit
}