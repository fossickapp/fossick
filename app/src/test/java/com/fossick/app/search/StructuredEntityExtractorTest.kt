package com.fossick.app.search

import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredEntityExtractorTest {
    @Test
    fun extractsObviousMoneyAndContactEntities() {
        val entities = StructuredEntityExtractor.extract("Paid ₹30 to rahul@example.com https://example.com")
        assertTrue(entities.contains("MONEY:30:INR"))
        assertTrue(entities.contains("EMAIL:rahul@example.com"))
        assertTrue(entities.contains("URL:https://example.com"))
    }

    @Test
    fun extractsInternationalMoneyAndExplicitLocalEntities() {
        val entities = StructuredEntityExtractor.extract(
            "Paid €49 on 2026-09-14 at 18:30. Contact +1 (415) 555-2671 or alex@example.com. " +
                "https://example.com, Transaction ID ABC12345."
        )

        assertTrue(entities.contains("MONEY:49:EUR"))
        assertTrue(entities.contains("DATE:2026-09-14"))
        assertTrue(entities.contains("TIME:18:30"))
        assertTrue(entities.contains("PHONE:+14155552671"))
        assertTrue(entities.contains("EMAIL:alex@example.com"))
        assertTrue(entities.contains("URL:https://example.com"))
        assertTrue(entities.contains("TRANSACTION_REFERENCE:ABC12345"))
    }
}
