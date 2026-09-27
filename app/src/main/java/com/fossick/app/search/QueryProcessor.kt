package com.fossick.app.search

data class ProcessedQuery(
    val normalizedPhrase: String,
    val strongTerms: List<String>,
    val contextTerms: List<String>,
    val entities: List<String> = emptyList(),
    val categoryHints: List<String> = emptyList(),
    val sourceHints: List<String> = emptyList(),
    val dateClues: List<String> = emptyList(),
    val amountClues: List<String> = emptyList(),
    val expandedTerms: List<String> = emptyList()
)

object QueryProcessor {
    private val conversationalScaffolding = setOf(
        "find", "me", "show", "where", "the", "one", "something", "remember",
        "said", "she", "he", "i", "think", "maybe", "that", "what", "do",
        "you", "with", "text", "message", "when", "please"
    )
    private val categoryTokens = mapOf(
        "screenshot" to listOf("SCREENSHOT"),
        "screenshots" to listOf("SCREENSHOT"),
        "camera" to listOf("CAMERA"),
        "photo" to listOf("CAMERA", "IMAGE"),
        "photos" to listOf("CAMERA", "IMAGE"),
        "picture" to listOf("CAMERA", "IMAGE"),
        "pictures" to listOf("CAMERA", "IMAGE"),
        "image" to listOf("IMAGE"),
        "images" to listOf("IMAGE"),
        "video" to listOf("VIDEO"),
        "videos" to listOf("VIDEO"),
        "pdf" to listOf("PDF"),
        "document" to listOf("DOCUMENT", "PDF"),
        "documents" to listOf("DOCUMENT", "PDF"),
        "presentation" to listOf("PRESENTATION"),
        "slides" to listOf("PRESENTATION"),
        "spreadsheet" to listOf("SPREADSHEET"),
        "excel" to listOf("SPREADSHEET"),
        "csv" to listOf("SPREADSHEET")
    )
    private val sourceTokens = mapOf(
        "download" to "download",
        "downloads" to "download",
        "downloaded" to "download",
        "whatsapp" to "whatsapp",
        "telegram" to "telegram",
        "phonepe" to "phonepe"
    )
    private val categoryWords = categoryTokens.keys
    private val sourceWords = sourceTokens.keys

    fun process(rawQuery: String): ProcessedQuery {
        val normalizedPhrase = TextNormalizer.normalize(rawQuery)
        val terms = normalizedPhrase.split(' ').filter(String::isNotBlank).distinct()
        val entities = StructuredEntityExtractor.extract(normalizedPhrase)
            .split('|')
            .filter(String::isNotBlank)
        val categoryHints = terms.flatMap { categoryTokens[it].orEmpty() }.distinct()
        val sourceHints = terms.mapNotNull(sourceTokens::get).distinct()
        val contextTerms = terms.filter(conversationalScaffolding::contains)
        val strongTerms = terms.filterNot { term ->
            term in conversationalScaffolding || term in categoryWords || term in sourceWords
        }
        val expandedTerms = strongTerms.flatMap { expandTermVariants(it) }.distinct()
        return ProcessedQuery(
            normalizedPhrase = normalizedPhrase,
            strongTerms = strongTerms,
            contextTerms = contextTerms,
            entities = entities,
            categoryHints = categoryHints,
            sourceHints = sourceHints,
            dateClues = entities.filter { it.startsWith("DATE:") || it.startsWith("TIME:") },
            amountClues = entities.filter { it.startsWith("MONEY:") },
            expandedTerms = expandedTerms
        )
    }

    internal fun expandTermVariants(term: String): Set<String> {
        if (term.isBlank()) return emptySet()

        val base = term.lowercase()
        val variants = linkedSetOf(base)

        val collapsedRepeated = base
            .replace(Regex("oo"), "u")
            .replace(Regex("ee"), "i")
            .replace(Regex("aa"), "a")
            .replace(Regex("ll"), "l")
            .replace(Regex("ss"), "s")
            .replace(Regex("([aeiou])\\1+"), "$1")
            .replace(Regex("([a-z])\\1+"), "$1")

        if (collapsedRepeated != base) variants += collapsedRepeated

        val commonVariants = listOfNotNull(
            base.removeSuffix("s").takeIf { it.length >= 2 },
            base.removeSuffix("ed").takeIf { it.length >= 2 },
            base.removeSuffix("ing").takeIf { it.length >= 2 },
            base.trimEnd('s', 'd', 'g', 'n'),
            base.replace("oo", "u"),
            base.replace("ee", "i"),
            base.replace("aa", "a"),
            base.replace("ll", "l"),
            base.replace("ss", "s")
        )
        variants += commonVariants.filter { it.length >= 2 }

        val typoPatterns = listOf(
            "ie" to "ei",
            "ei" to "ie",
            "ia" to "ai",
            "ai" to "ia",
            "oe" to "eo",
            "eo" to "oe",
            "mnt" to "ment",
            "ment" to "mnt",
            "ht" to "th",
            "th" to "ht"
        )
        for ((from, to) in typoPatterns) {
            variants += base.replace(from, to)
            variants += base.replace(to, from)
        }

        val transpositionVariants = mutableSetOf<String>()
        for (index in 0 until base.length - 1) {
            val swapped = buildString {
                append(base.substring(0, index))
                append(base[index + 1])
                append(base[index])
                append(base.substring(index + 2))
            }
            if (swapped != base) transpositionVariants += swapped
        }
        variants += transpositionVariants.filter { it.length >= 2 }

        return variants.filter { it.length >= 2 }.toSet()
    }
}