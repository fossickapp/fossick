package com.fossick.app.search

import com.fossick.app.data.local.ScreenshotEntity
import java.util.Locale

 data class SearchMatchDiagnostic(
    val memory: ScreenshotEntity,
    val matchedContentTerms: List<String>,
    val matchedFilenameTerms: List<String>,
    val matchedEntityTerms: List<String>,
    val matchedCategoryHints: List<String>,
    val matchedSourceHints: List<String>,
    val exactPhrase: Boolean,
    val lexicalScore: Int,
    val entityScore: Int,
    val typeScore: Int,
    val sourceScore: Int,
    val phraseAndCoverageScore: Int,
    val totalScore: Int
)

object SearchRanker {
    fun rank(items: List<ScreenshotEntity>, query: ProcessedQuery): List<ScreenshotEntity> =
        explain(items, query).map { it.memory }

    fun explain(items: List<ScreenshotEntity>, query: ProcessedQuery): List<SearchMatchDiagnostic> {
        if (items.isEmpty()) return emptyList()
        val normalizedContent = items.map { TextNormalizer.normalize(it.normalizedText.ifBlank { it.ocrText }) }
        val documentFrequency = query.strongTerms.associateWith { term ->
            normalizedContent.count { it.containsTerm(term) }
        }
        return items.mapIndexed { index, item ->
            score(item, normalizedContent[index], query, documentFrequency, items.size)
        }.sortedWith(
            compareByDescending<SearchMatchDiagnostic> { it.totalScore }
                .thenByDescending {
                    it.memory.dateModified.takeIf { modified -> modified > 0L } ?: it.memory.dateAdded
                }
                .thenBy { it.memory.mediaStoreId }
        )
    }

    private fun score(
        item: ScreenshotEntity,
        content: String,
        query: ProcessedQuery,
        documentFrequency: Map<String, Int>,
        candidateCount: Int
    ): SearchMatchDiagnostic {
        val name = TextNormalizer.normalize(item.displayName)
        val path = TextNormalizer.normalize(item.relativePath.orEmpty())
        val entityText = item.structuredEntities.lowercase(Locale.ROOT)
        val category = item.category.uppercase(Locale.ROOT)
        val source = "${item.sourceType} ${item.relativePath.orEmpty()} ${item.displayName}".lowercase(Locale.ROOT)
        val matchedContent = mutableListOf<String>()
        val matchedName = mutableListOf<String>()
        val matchedEntities = mutableListOf<String>()
        var lexicalScore = 0

        for (term in query.strongTerms) {
            val frequency = documentFrequency[term]?.coerceAtLeast(1) ?: 1
            val rarityBoost = ((candidateCount * 100) / frequency).coerceAtMost(500)
            val weight = 100 + term.codePointCount(0, term.length).coerceAtMost(16) * 12 + rarityBoost
            val aliases = QueryProcessor.expandTermVariants(term).ifEmpty { setOf(term) }
            val matchedVariant = aliases.firstOrNull { alias ->
                name.containsTerm(alias) || content.containsTerm(alias) || path.containsTerm(alias)
            }
            if (matchedVariant != null) {
                if (name.containsTerm(matchedVariant)) {
                    matchedName += term
                    lexicalScore += weight * 3
                }
                if (content.containsTerm(matchedVariant)) {
                    matchedContent += term
                    lexicalScore += weight * 2
                }
                if (path.containsTerm(matchedVariant)) lexicalScore += weight
            }
        }

        var entityScore = 0
        for (entity in query.entities) {
            val normalizedEntity = entity.lowercase(Locale.ROOT)
            if (entityText.contains(normalizedEntity)) {
                matchedEntities += entity
                entityScore += 500
            }
        }

        val matchedCategories = query.categoryHints.filter { category == it }
        val typeScore = matchedCategories.size * 400
        val matchedSources = query.sourceHints.filter { hint ->
            source.contains(hint.lowercase(Locale.ROOT))
        }
        val sourceScore = matchedSources.size * 250
        val strongMatches = (matchedContent + matchedName + matchedEntities).toSet()
        val completeCoverage = query.strongTerms.isNotEmpty() &&
            query.strongTerms.all { term -> strongMatches.contains(term) || path.containsTerm(term) }
        val phrase = query.strongTerms.joinToString(" ")
        val exactPhrase = phrase.isNotBlank() &&
            (content.containsPhrase(phrase) || name.containsPhrase(phrase) || path.containsPhrase(phrase))
        val phraseAndCoverageScore = (strongMatches.size * 100) +
            (if (completeCoverage) 200 else 0) +
            (if (exactPhrase) 1_000 else 0)

        return SearchMatchDiagnostic(
            memory = item,
            matchedContentTerms = matchedContent,
            matchedFilenameTerms = matchedName,
            matchedEntityTerms = matchedEntities,
            matchedCategoryHints = matchedCategories,
            matchedSourceHints = matchedSources,
            exactPhrase = exactPhrase,
            lexicalScore = lexicalScore,
            entityScore = entityScore,
            typeScore = typeScore,
            sourceScore = sourceScore,
            phraseAndCoverageScore = phraseAndCoverageScore,
            totalScore = lexicalScore + entityScore + typeScore + sourceScore + phraseAndCoverageScore
        )
    }

    private fun String.containsTerm(term: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(term)}(?![\\p{L}\\p{N}])").containsMatchIn(this)

    private fun String.containsPhrase(phrase: String): Boolean =
        contains(" $phrase ") || startsWith("$phrase ") || endsWith(" $phrase") || this == phrase
}
