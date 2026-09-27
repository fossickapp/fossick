package com.fossick.app.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun normalizesIndianCurrencyRepresentations() {
        val expected = "30 inr"
        assertEquals(expected, TextNormalizer.normalize("₹30"))
        assertEquals(expected, TextNormalizer.normalize("₹ 30"))
        assertEquals(expected, TextNormalizer.normalize("Rs. 30"))
        assertEquals(expected, TextNormalizer.normalize("INR 30"))
        assertEquals(expected, TextNormalizer.normalize("30 rupees"))
    }

    @Test
    fun normalizesCaseWhitespaceAndUnicode() {
        assertEquals("paid successfully", TextNormalizer.normalize("  PAID   successfully  "))
    }

    @Test
    fun normalizesUnambiguousInternationalCurrenciesWithoutGuessingAmbiguousSymbols() {
        assertEquals("30 inr", TextNormalizer.normalize("Rs. 30"))
        assertEquals("30 eur", TextNormalizer.normalize("€30"))
        assertEquals("30 eur", TextNormalizer.normalize("30 EUR"))
        assertEquals("30 gbp", TextNormalizer.normalize("£30"))
        assertEquals("3000 yen_yuan", TextNormalizer.normalize("¥3000"))
        assertEquals("30000 krw", TextNormalizer.normalize("₩30000"))
        assertEquals("500 php", TextNormalizer.normalize("₱500"))
        assertEquals("40 aud", TextNormalizer.normalize("A$40"))
        assertEquals("40 cad", TextNormalizer.normalize("C$40"))
        assertEquals("30 dollar", TextNormalizer.normalize("$30"))
        assertEquals("30 dollar", TextNormalizer.normalize("30 dollars"))
        assertNotEquals(TextNormalizer.normalize("$30"), TextNormalizer.normalize("30 USD"))
    }

    @Test
    fun normalizesUnambiguousInternationalNumberSeparators() {
        assertEquals("1234.56", TextNormalizer.normalize("1,234.56"))
        assertEquals("1234.56", TextNormalizer.normalize("1.234,56"))
    }
}
