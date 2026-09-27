package com.fossick.app.search

import com.fossick.app.data.local.ScreenshotDao
import com.fossick.app.data.local.ScreenshotEntity

data class SearchDiagnostics(
    val rawQuery: String,
    val normalizedQuery: String,
    val entities: List<String>,
    val strongTerms: List<String>,
    val contextTerms: List<String>,
    val categoryHints: List<String>,
    val sourceHints: List<String>,
    val candidateCount: Int,
    val rankedMatches: List<SearchMatchDiagnostic>
)

class FossickSearchEngine(private val dao: ScreenshotDao) {
    suspend fun search(rawQuery: String): List<ScreenshotEntity> {
        val (query, candidates) = findCandidates(rawQuery)
        return SearchRanker.rank(candidates, query)
            .take(RESULT_LIMIT)
    }

    suspend fun diagnose(rawQuery: String): SearchDiagnostics {
        val (query, candidates) = findCandidates(rawQuery)
        return SearchDiagnostics(
            rawQuery = rawQuery,
            normalizedQuery = query.normalizedPhrase,
            entities = query.entities,
            strongTerms = query.strongTerms,
            contextTerms = query.contextTerms,
            categoryHints = query.categoryHints,
            sourceHints = query.sourceHints,
            candidateCount = candidates.size,
            rankedMatches = SearchRanker.explain(candidates, query).take(RESULT_LIMIT)
        )
    }

    private suspend fun findCandidates(rawQuery: String): Pair<ProcessedQuery, List<ScreenshotEntity>> {
        val query = QueryProcessor.process(rawQuery)
        if (query.normalizedPhrase.isBlank()) return query to emptyList()
        val candidateTerms = (query.expandedTerms + query.strongTerms + query.categoryHints + query.sourceHints)
            .distinct()
            .ifEmpty { query.contextTerms }
        val candidates = candidateTerms.flatMap { dao.search(it, CANDIDATE_LIMIT) }
            .distinctBy { it.mediaStoreId }
        return query to candidates
    }

    companion object {
        private const val RESULT_LIMIT = 50
        private const val CANDIDATE_LIMIT = 5000
    }
}