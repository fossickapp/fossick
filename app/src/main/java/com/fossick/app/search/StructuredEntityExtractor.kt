package com.fossick.app.search

object StructuredEntityExtractor {
    fun extract(text: String): String {
        val entities = mutableListOf<String>()
        moneyPattern.findAll(text).forEach { match ->
            val normalized = TextNormalizer.normalize(match.value).split(' ')
            val amount = normalized.firstOrNull { it.firstOrNull()?.isDigit() == true } ?: return@forEach
            val currency = normalized.firstOrNull { it in currencies } ?: return@forEach
            entities += "MONEY:$amount:${currency.uppercase()}"
        }
        Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
            .findAll(text)
            .forEach { entities += "EMAIL:${it.value.lowercase()}" }
        Regex("https?://\\S+", RegexOption.IGNORE_CASE)
            .findAll(text)
            .forEach { entities += "URL:${it.value.trimEnd('.', ',', ';', ')', ']')}" }
        val dates = datePattern.findAll(text).map { it.value }.toSet()
        dates.forEach { entities += "DATE:${it.lowercase()}" }
        timePattern.findAll(text).forEach { entities += "TIME:${it.value.lowercase()}" }
        phonePattern.findAll(text)
            .filterNot { it.value in dates }
            .forEach { match ->
                val digits = match.value.filter(Char::isDigit)
                if (digits.length in 8..15 && (match.value.startsWith('+') || match.value.any { it in " ().-" })) {
                    entities += "PHONE:${if (match.value.trimStart().startsWith('+')) "+" else ""}$digits"
                }
            }
        transactionPattern.findAll(text).forEach { entities += "TRANSACTION_REFERENCE:${it.groupValues[1]}" }
        return entities.distinct().joinToString("|")
    }

    private val moneyPattern = Regex(
        "(?:a\\$|c\\$|₹|€|£|₩|₱|\\$|¥|\\b(?:inr|usd|eur|gbp|jpy|cny|krw|php|aud|cad|rs\\.?)\\b)\\s*\\d+(?:[.,]\\d+)*|\\b\\d+(?:[.,]\\d+)*\\s*(?:inr|usd|eur|gbp|jpy|cny|krw|php|aud|cad|rupees?|dollars?|euros?|yen|yuan)\\b",
        RegexOption.IGNORE_CASE
    )
    private val datePattern = Regex(
        "\\b\\d{4}-\\d{2}-\\d{2}\\b|\\b\\d{1,2}\\s+(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{4}\\b",
        RegexOption.IGNORE_CASE
    )
    private val timePattern = Regex("\\b(?:[01]?\\d|2[0-3]):[0-5]\\d(?:\\s?[ap]m)?\\b", RegexOption.IGNORE_CASE)
    private val phonePattern = Regex("(?<![\\p{L}\\p{N}])\\+?\\d[\\d(). -]{6,}\\d(?![\\p{L}\\p{N}])")
    private val transactionPattern = Regex(
        "(?:transaction\\s*(?:id|reference)|txn\\s*id|reference\\s*(?:no|number|#)?|ref\\s*(?:no|number|#)?)\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9-]{4,})",
        RegexOption.IGNORE_CASE
    )
    private val currencies = setOf(
        "inr", "usd", "eur", "gbp", "jpy", "cny", "krw", "php", "aud", "cad",
        "dollar", "yen_yuan"
    )
}
