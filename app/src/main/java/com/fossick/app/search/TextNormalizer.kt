package com.fossick.app.search

import java.text.Normalizer
import java.util.Locale

object TextNormalizer {
    fun normalize(value: String): String {
        var text = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        text = text.replace(Regex("(\\d{1,3})(,\\d{3})+\\.(\\d+)")) { match ->
            match.value.replace(",", "")
        }
        text = text.replace(Regex("(\\d{1,3})(\\.\\d{3})+,(\\d+)")) { match ->
            match.value.replace(".", "").replace(',', '.')
        }
        text = text.replace(symbolAmount) { match ->
            val currency = when (match.groupValues[1]) {
                "₹" -> "inr"
                "€" -> "eur"
                "£" -> "gbp"
                "₩" -> "krw"
                "₱" -> "php"
                "a$" -> "aud"
                "c$" -> "cad"
                "$" -> "dollar"
                "¥" -> "yen_yuan"
                else -> return@replace match.value
            }
            "${match.groupValues[2]} $currency"
        }
        text = text.replace(rupeePrefix) { "${it.groupValues[1]} inr" }
        text = text.replace(codeBeforeAmount) { "${it.groupValues[2]} ${it.groupValues[1]}" }
        text = text.replace(codeAfterAmount) { "${it.groupValues[1]} ${it.groupValues[2]}" }
        text = text.replace(spokenCurrencyAfterAmount) { match ->
            val currency = when (match.groupValues[2]) {
                "rupee", "rupees", "rs" -> "inr"
                "dollar", "dollars" -> "dollar"
                "euro", "euros" -> "eur"
                "yen" -> "jpy"
                "yuan" -> "cny"
                else -> return@replace match.value
            }
            "${match.groupValues[1]} $currency"
        }
        text = text.replace(Regex("[^\\p{L}\\p{N}.,_@:/-]+"), " ")
        return text.trim().replace(Regex("\\s+"), " ")
    }

    private val symbolAmount = Regex("(a\\$|c\\$|₹|€|£|₩|₱|\\$|¥)\\s*(\\d+(?:[.,]\\d+)*)")
    private val rupeePrefix = Regex("\\brs\\.?\\s*(\\d+(?:[.,]\\d+)*)\\b")
    private val codeBeforeAmount = Regex("\\b(inr|usd|eur|gbp|jpy|cny|krw|php|aud|cad)\\s*(\\d+(?:[.,]\\d+)*)\\b")
    private val codeAfterAmount = Regex("\\b(\\d+(?:[.,]\\d+)*)\\s*(inr|usd|eur|gbp|jpy|cny|krw|php|aud|cad)\\b")
    private val spokenCurrencyAfterAmount = Regex("\\b(\\d+(?:[.,]\\d+)*)\\s*(rupees?|rs\\.?|dollars?|euros?|yen|yuan)\\b")
}
