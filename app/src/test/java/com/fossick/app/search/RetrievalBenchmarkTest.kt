package com.fossick.app.search

import com.fossick.app.data.local.ScreenshotDao
import com.fossick.app.data.local.ScreenshotEntity
import com.fossick.app.data.local.ScreenshotIndexAggregate
import com.fossick.app.data.local.ScreenshotSyncMetadata
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrievalBenchmarkTest {
    private val fixtures = buildCorpus()
    private val fixtureByMediaId = fixtures.associateBy { it.entity.mediaStoreId }
    private val dao = BenchmarkDao(fixtures.map { it.entity })

    @Test
    fun regressionCasesStayStrong() = runBlocking {
        val engine = FossickSearchEngine(dao)

        val rahoolResults = engine.search("Rahool")
        assertTrue("Rahool should resolve to Rahul memory", rahoolResults.any { idOf(it) == "payment_rahul_30" })

        val orderIndependent = listOf(
            "Rahul 30",
            "30 Rahul",
            "payment Rahul 30",
            "30 payment Rahul",
            "Rahul payment 30"
        )
        orderIndependent.forEach { query ->
            val matches = engine.search(query)
            assertTrue(
                "Order-independent query should still surface payment_rahul_30: $query",
                matches.take(5).any { idOf(it) == "payment_rahul_30" }
            )
        }

        assertTrue(
            "Money normalization should still resolve INR 30 correctly",
            engine.search("INR 30").take(5).any { idOf(it) == "payment_rahul_30" }
        )
    }

    @Test
    fun retrievalBenchmarkReportsMetricsAndCurrentCapabilities() = runBlocking {
        val engine = FossickSearchEngine(dao)
        val cases = buildBenchmarkCases()

        require(cases.size >= 90) { "Benchmark should include enough cases to be meaningful" }

        val outcomes = mutableListOf<CaseOutcome>()
        for (case in cases) {
            val started = System.nanoTime()
            val results = engine.search(case.query)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
            val actualIds = results.map(::idOf)
            val rank = if (case.expectedId != null) actualIds.indexOf(case.expectedId) + 1 else null
            val top1 = case.expectedId != null && actualIds.firstOrNull() == case.expectedId
            val top3 = case.expectedId != null && actualIds.take(3).contains(case.expectedId)
            val top5 = case.expectedId != null && actualIds.take(5).contains(case.expectedId)
            val passed = when {
                case.expectedId == null -> actualIds.none { id -> case.negativeAllowedIds.any { it == id } }
                case.expectedTopK == 1 -> top1
                case.expectedTopK == 3 -> top3
                case.expectedTopK == 5 -> top5
                else -> top5
            }

            outcomes += CaseOutcome(
                category = case.category,
                query = case.query,
                expectedId = case.expectedId,
                actualTopIds = actualIds.take(5),
                rank = rank,
                top1 = top1,
                top3 = top3,
                top5 = top5,
                passed = passed,
                durationMs = elapsedMs,
                negativeAllowedIds = case.negativeAllowedIds
            )
        }

        val positive = outcomes.filter { it.expectedId != null }
        val totalQueries = outcomes.size
        val positiveCount = positive.size.toDouble()
        val top1Rate = if (positiveCount == 0.0) 0.0 else positive.count { it.top1 }.toDouble() / positiveCount
        val recall3 = if (positiveCount == 0.0) 0.0 else positive.count { it.top3 }.toDouble() / positiveCount
        val recall5 = if (positiveCount == 0.0) 0.0 else positive.count { it.top5 }.toDouble() / positiveCount
        val mrr = if (positiveCount == 0.0) 0.0 else positive.map {
            val rank = it.rank ?: 0
            if (rank <= 0) 0.0 else 1.0 / rank.toDouble()
        }.average().coerceIn(0.0, 1.0)

        assertTrue("Top-1 must be finite and in range", top1Rate.isFinite() && top1Rate in 0.0..1.0)
        assertTrue("Recall@3 must be finite and in range", recall3.isFinite() && recall3 in 0.0..1.0)
        assertTrue("Recall@5 must be finite and in range", recall5.isFinite() && recall5 in 0.0..1.0)
        assertTrue("MRR must be finite and in range", mrr.isFinite() && mrr in 0.0..1.0)

        val categoryScores = buildMap {
            for (category in listOf("EXACT", "ORDER_INDEPENDENCE", "TYPO", "MONEY", "NATURAL", "METADATA", "RANKING", "NEGATIVE")) {
                val categoryResults = outcomes.filter { it.category == category }
                val categoryPositive = categoryResults.filter { it.expectedId != null }
                val score = when {
                    categoryResults.isEmpty() -> 0.0
                    category == "NEGATIVE" -> categoryResults.count { it.passed }.toDouble() / categoryResults.size.toDouble()
                    categoryPositive.isEmpty() -> 0.0
                    else -> categoryPositive.count { it.top5 }.toDouble() / categoryPositive.size.toDouble()
                }
                assertTrue("Category score for $category must be finite and in range", score.isFinite() && score in 0.0..1.0)
                put(category, score)
            }
        }

        val failureSummary = outcomes.filter { !it.passed }
        if (failureSummary.isNotEmpty()) {
            println("\nFOSSICK RETRIEVAL BENCHMARK FAILURES")
            for (failure in failureSummary.take(20)) {
                val actual = failure.actualTopIds.ifEmpty { "<no result>" }
                val rankText = if (failure.rank == null) "not evaluated" else failure.rank.toString()
                println(
                    "FAIL\n" +
                        "Category: ${failure.category}\n" +
                        "Query: ${failure.query}\n" +
                        "Expected: ${failure.expectedId ?: "<no relevant result>"}\n" +
                        "Actual top results: ${actual}\n" +
                        "Expected rank: <=${failure.expectedTopK()}\n" +
                        "Actual rank: $rankText\n"
                )
            }
        }

        println("\nFOSSICK RETRIEVAL BENCHMARK")
        println("Queries: $totalQueries")
        println("Top-1: ${"%.2f".format(top1Rate * 100)}%")
        println("Recall@3: ${"%.2f".format(recall3 * 100)}%")
        println("Recall@5: ${"%.2f".format(recall5 * 100)}%")
        println("MRR: ${"%.2f".format(mrr)}")
        for ((category, score) in categoryScores) {
            println("${category.padEnd(20)} ${"%.2f".format(score * 100)}%")
        }

        assertTrue("Benchmark should run at least 90 cases", totalQueries >= 90)
    }

    private fun idOf(entity: ScreenshotEntity): String =
        fixtureByMediaId[entity.mediaStoreId]?.id
            ?: entity.displayName.substringBeforeLast('.')
            .ifBlank { entity.displayName }

    private fun buildCorpus(): List<BenchmarkFixture> = listOf(
        record("payment_rahul_30", "Screenshot_2026_09_21_payment.png", "Pictures/Screenshots", "SCREENSHOT", "Paid Successfully ₹30 Rahul Shaikh Transaction completed", "paid successfully inr 30 rahul shaikh transaction completed", "MONEY:30:INR|PERSON:Rahul Shaikh|TRANSACTION_REFERENCE:TXN-4001"),
        record("payment_rahul_300", "Screenshot_2026_09_21_payment_300.png", "Pictures/Screenshots", "SCREENSHOT", "Paid Successfully ₹300 Rahul Shaikh Invoice", "paid successfully inr 300 rahul shaikh invoice", "MONEY:300:INR|PERSON:Rahul Shaikh"),
        record("payment_rahul_50", "Screenshot_2026_09_18_payment_50.png", "Pictures/Receipts", "SCREENSHOT", "Sent ₹50 to Rahul for train fare", "sent inr 50 to rahul for train fare", "MONEY:50:INR|PERSON:Rahul"),
        record("payment_sahil_30", "Screenshot_2026_09_22_payment_sahil.png", "Pictures/Receipts", "SCREENSHOT", "Paid ₹30 to Sahil for lunch", "paid inr 30 to sahil for lunch", "MONEY:30:INR|PERSON:Sahil"),
        record("rahul_chat", "rahul_chat.png", "WhatsApp/Chats", "SCREENSHOT", "Rahul: can you send the notes today?", "rahul can you send the notes today", "PERSON:Rahul"),
        record("receipt_30", "receipt_30.png", "Downloads/Receipts", "SCREENSHOT", "Total due ₹30 plus GST", "total due inr 30 plus gst", "MONEY:30:INR"),
        record("apology_message", "apology_message.png", "Pictures/Messages", "SCREENSHOT", "I'm sorry about what happened yesterday", "i m sorry about what happened yesterday", ""),
        record("cpp_assignment", "C++_Assignment_2026.pdf", "Documents/Assignments", "PDF", "C++ assignment on inheritance and polymorphism", "c plus plus assignment on inheritance and polymorphism", "TOPIC:C++ ASSIGNMENT"),
        record("bca_timetable", "BCA_Semester_Timetable.xlsx", "Documents/College", "SPREADSHEET", "Monday 9:00 Maths, 11:00 DBMS, Wednesday 9:00 C++", "monday 9 00 maths 11 00 dbms wednesday 9 00 c plus plus", "DAY:Monday|COURSE:DBMS"),
        record("dbms_normalization_pdf", "dbms_normalization.pdf", "Documents/College/DBMS", "PDF", "Database normalization forms 1NF 2NF 3NF BCNF", "database normalization forms 1nf 2nf 3nf bcnf", "TOPIC:Database Normalization"),
        record("flight_booking", "flight_booking_2026.pdf", "Documents/Travel", "PDF", "Flight booking to Delhi 12:40 PM on 2026-09-25", "flight booking to delhi 12 40 pm on 2026 09 25", "DATE:2026-09-25|TIME:12:40|CITY:Delhi"),
        record("college_fee_receipt", "college_fee_receipt.pdf", "Downloads/CollegeFees", "PDF", "College fee receipt ₹4200 due on 2026-09-20", "college fee receipt inr 4200 due on 2026 09 20", "MONEY:4200:INR|DATE:2026-09-20"),
        record("transaction_reference_ajx", "tx_ref_ajx.txt", "Documents/Transactions", "TXT", "Transaction reference AJX-9912 confirmed", "transaction reference ajx 9912 confirmed", "TRANSACTION_REFERENCE:AJX-9912"),
        record("phone_number_contact", "mentor_contact.txt", "Contacts/Personal", "TXT", "Call +91 98765 43210 or email mentor@example.com", "call 91 98765 43210 or email mentor example com", "PHONE:+919876543210|EMAIL:mentor@example.com"),
        record("email_document", "meeting_email.eml", "Downloads/Emails", "TEXT", "Email from admin@college.edu about admission", "email from admin college edu about admission", "EMAIL:admin@college.edu"),
        record("url_document", "travel_url.txt", "Documents/Links", "TXT", "Visit https://railway.gov.in for booking info", "visit https railway gov in for booking info", "URL:https://railway.gov.in"),
        record("shopping_receipt", "shopping_receipt.jpg", "Pictures/Shopping", "SCREENSHOT", "Bought milk bread and soap for $42 from Market Plaza", "bought milk bread and soap for dollar 42 from market plaza", "MONEY:42:USD|LOCATION:Market Plaza"),
        record("bus_ticket", "bus_ticket.pdf", "Documents/Travel", "PDF", "Bus ticket from Jaipur to Delhi departure 08:30", "bus ticket from jaipur to delhi departure 08 30", "TIME:08:30|CITY:Jaipur|CITY:Delhi"),
        record("landlord_payment", "landlord_payment.png", "Pictures/Payments", "SCREENSHOT", "Paid landlord ₹12000 for September rent", "paid landlord inr 12000 for september rent", "MONEY:12000:INR|PERSON:Landlord"),
        record("movie_ticket", "movie_ticket.png", "Pictures/Travel", "SCREENSHOT", "Movie ticket ₹250 for Friday 7:30 PM", "movie ticket inr 250 for friday 7 30 pm", "MONEY:250:INR|TIME:19:30"),
        record("teacher_notice", "teacher_notice.txt", "Documents/Classroom", "TXT", "Submit assignment on Friday before 5 PM", "submit assignment on friday before 5 pm", "TIME:17:00|DAY:Friday"),
        record("lab_report", "lab_report.docx", "Documents/Lab", "DOCUMENT", "Lab report on capacitor and resistor circuits", "lab report on capacitor and resistor circuits", "TOPIC:Capacitor"),
        record("project_pitch", "project_pitch.pptx", "Documents/Projects", "PRESENTATION", "Project pitch for smart irrigation system", "project pitch for smart irrigation system", "TOPIC:Smart Irrigation"),
        record("tax_invoice", "tax_invoice.png", "Downloads/Bills", "SCREENSHOT", "Tax invoice €49 for office supplies", "tax invoice eur 49 for office supplies", "MONEY:49:EUR"),
        record("hostel_roommate_chat", "hostel_roommate_chat.png", "WhatsApp/Chats", "SCREENSHOT", "Roommate: pay me ₹800 for electricity", "roommate pay me inr 800 for electricity", "MONEY:800:INR|PERSON:Roommate"),
        record("birthday_invite", "birthday_invite.pdf", "Documents/Events", "PDF", "Birthday party at 7 PM on 2026-10-02", "birthday party at 7 pm on 2026 10 02", "TIME:19:00|DATE:2026-10-02"),
        record("internship_offer", "internship_offer.pdf", "Documents/Offers", "PDF", "Internship offer from Acme Labs with stipend ₹18000", "internship offer from acme labs with stipend inr 18000", "MONEY:18000:INR|ORGANIZATION:Acme Labs"),
        record("room_mates_split", "room_mates_split.png", "Pictures/Bills", "SCREENSHOT", "Split rent ₹4500 equally among three roommates", "split rent inr 4500 equally among three roommates", "MONEY:4500:INR"),
        record("salary_slip", "salary_slip.pdf", "Documents/Finance", "PDF", "Salary slip for September with gross ₹34000", "salary slip for september with gross inr 34000", "MONEY:34000:INR|DATE:September"),
        record("tuition_fee", "tuition_fee.png", "Pictures/Fees", "SCREENSHOT", "Tuition fee payment £20 for maths coaching", "tuition fee payment gbp 20 for maths coaching", "MONEY:20:GBP"),
        record("whatsapp_group_chat", "whatsapp_group_chat.png", "WhatsApp/Chats", "SCREENSHOT", "Group: physics notes and lab timings", "group physics notes and lab timings", ""),
        record("python_assignment", "python_assignment.docx", "Documents/Assignments", "DOCUMENT", "Python assignment on dictionaries and loops", "python assignment on dictionaries and loops", "TOPIC:Python"),
        record("react_native_ticket", "react_native_ticket.pdf", "Documents/Travel", "PDF", "React Native workshop ticket for 2026-11-18", "react native workshop ticket for 2026 11 18", "DATE:2026-11-18"),
        record("course_schedule", "course_schedule.xlsx", "Documents/College", "SPREADSHEET", "Course schedule for DBMS, C++, Java, Networks", "course schedule for dbms c plus plus java networks", "COURSE:DBMS|COURSE:C++"),
        record("appointment_reminder", "appointment_reminder.txt", "Downloads/Reminders", "TXT", "Appointment reminder with Dr. Mehta at 3:15 PM", "appointment reminder with dr mehta at 3 15 pm", "TIME:15:15|PERSON:Dr. Mehta"),
        record("refund_status", "refund_status.pdf", "Documents/Bank", "PDF", "Refund of $42 processed to bank account", "refund of dollar 42 processed to bank account", "MONEY:42:USD"),
        record("library_due_notice", "library_due_notice.pdf", "Documents/Library", "PDF", "Library due notice for overdue book and ₹150 fine", "library due notice for overdue book and inr 150 fine", "MONEY:150:INR"),
        record("medicine_bill", "medicine_bill.png", "Pictures/Bills", "SCREENSHOT", "Medicine bill ₹420 from Apollo Pharmacy", "medicine bill inr 420 from apollo pharmacy", "MONEY:420:INR|ORGANIZATION:Apollo Pharmacy"),
        record("travel_itinerary", "travel_itinerary.pdf", "Documents/Travel", "PDF", "Trip itinerary: Jaipur to Pune, flight 19:20", "trip itinerary jaipur to pune flight 19 20", "TIME:19:20|CITY:Jaipur|CITY:Pune"),
        record("bank_transfer", "bank_transfer.pdf", "Documents/Bank", "PDF", "Bank transfer reference TXN-5007 amount ₹2500", "bank transfer reference txn 5007 amount inr 2500", "MONEY:2500:INR|TRANSACTION_REFERENCE:TXN-5007")
    )

    private fun record(
        id: String,
        displayName: String,
        relativePath: String,
        category: String,
        ocrText: String,
        normalizedText: String,
        structuredEntities: String
    ): BenchmarkFixture = BenchmarkFixture(
        id = id,
        entity = ScreenshotEntity(
            mediaStoreId = (1000 + abs(id.hashCode()).toLong() % 1_000_000L),
            uri = "content://bench/$id",
            displayName = displayName,
            relativePath = relativePath,
            mimeType = when {
                displayName.endsWith(".pdf") -> "application/pdf"
                displayName.endsWith(".png") || displayName.endsWith(".jpg") -> "image/png"
                displayName.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                displayName.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                displayName.endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                displayName.endsWith(".txt") || displayName.endsWith(".eml") -> "text/plain"
                else -> "application/octet-stream"
            },
            category = category,
            size = 1024L,
            sourceType = when {
                relativePath.contains("WhatsApp") -> "WHATSAPP"
                relativePath.contains("Downloads") -> "DOWNLOADS"
                relativePath.contains("Documents") -> "FILES"
                else -> "MEDIASTORE"
            },
            dateAdded = 1_700_000_000L,
            dateModified = 1_700_000_000L,
            ocrText = ocrText,
            normalizedText = normalizedText,
            structuredEntities = structuredEntities,
            ocrStatus = "INDEXED",
            contentIndexVersion = 1,
            lastIndexedModifiedTime = 1_700_000_000L,
            lastIndexedAt = 1_700_000_000L,
            errorState = null
        )
    )

    private fun buildBenchmarkCases(): List<RetrievalCase> {
        val exact = listOf(
            RetrievalCase("Rahul", "payment_rahul_30", "EXACT", 5),
            RetrievalCase("₹30", "payment_rahul_30", "EXACT", 5),
            RetrievalCase("C++ Assignment", "cpp_assignment", "EXACT", 5),
            RetrievalCase("DBMS normalization", "dbms_normalization_pdf", "EXACT", 5),
            RetrievalCase("flight booking", "flight_booking", "EXACT", 5),
            RetrievalCase("college fee receipt", "college_fee_receipt", "EXACT", 5),
            RetrievalCase("apology", "apology_message", "EXACT", 5),
            RetrievalCase("transaction reference", "transaction_reference_ajx", "EXACT", 5),
            RetrievalCase("mentor@example.com", "phone_number_contact", "EXACT", 5),
            RetrievalCase("railway.gov.in", "url_document", "EXACT", 5),
            RetrievalCase("salary slip", "salary_slip", "EXACT", 5),
            RetrievalCase("python assignment", "python_assignment", "EXACT", 5),
            RetrievalCase("birthday party", "birthday_invite", "EXACT", 5),
            RetrievalCase("refund", "refund_status", "EXACT", 5),
            RetrievalCase("library due", "library_due_notice", "EXACT", 5)
        )

        val orderIndependent = listOf(
            RetrievalCase("Rahul 30", "payment_rahul_30", "ORDER_INDEPENDENCE", 5),
            RetrievalCase("30 Rahul", "payment_rahul_30", "ORDER_INDEPENDENCE", 5),
            RetrievalCase("payment Rahul 30", "payment_rahul_30", "ORDER_INDEPENDENCE", 5),
            RetrievalCase("30 payment Rahul", "payment_rahul_30", "ORDER_INDEPENDENCE", 5),
            RetrievalCase("Rahul payment 30", "payment_rahul_30", "ORDER_INDEPENDENCE", 5),
            RetrievalCase("payment 30 Rahul", "payment_rahul_30", "ORDER_INDEPENDENCE", 5),
            RetrievalCase("30 Rahul payment", "payment_rahul_30", "ORDER_INDEPENDENCE", 5),
            RetrievalCase("Rahul transfer 30", "payment_rahul_30", "ORDER_INDEPENDENCE", 5)
        )

        val typo = listOf(
            RetrievalCase("Rahool", "payment_rahul_30", "TYPO", 5),
            RetrievalCase("Rahool 30", "payment_rahul_30", "TYPO", 5),
            RetrievalCase("assignmnt", "cpp_assignment", "TYPO", 5),
            RetrievalCase("dbms normalizaton", "dbms_normalization_pdf", "TYPO", 5),
            RetrievalCase("reciept", "payment_rahul_30", "TYPO", 5),
            RetrievalCase("flght booking", "flight_booking", "TYPO", 5),
            RetrievalCase("phne 9876543210", "phone_number_contact", "TYPO", 5),
            RetrievalCase("emial", "email_document", "TYPO", 5),
            RetrievalCase("bday party", "birthday_invite", "TYPO", 5)
        )

        val money = listOf(
            RetrievalCase("Rs 30", "payment_rahul_30", "MONEY", 5),
            RetrievalCase("Rs. 30", "payment_rahul_30", "MONEY", 5),
            RetrievalCase("30 rs", "payment_rahul_30", "MONEY", 5),
            RetrievalCase("30 rupees", "payment_rahul_30", "MONEY", 5),
            RetrievalCase("INR 30", "payment_rahul_30", "MONEY", 5),
            RetrievalCase("30 INR", "payment_rahul_30", "MONEY", 5),
            RetrievalCase("$42", "shopping_receipt", "MONEY", 5),
            RetrievalCase("USD 42", "shopping_receipt", "MONEY", 5),
            RetrievalCase("€49", "tax_invoice", "MONEY", 5),
            RetrievalCase("GBP 20", "tuition_fee", "MONEY", 5),
            RetrievalCase("£20", "tuition_fee", "MONEY", 5)
        )

        val natural = listOf(
            RetrievalCase("money I sent Rahul", "payment_rahul_30", "NATURAL", 5),
            RetrievalCase("payment to Rahul", "payment_rahul_30", "NATURAL", 5),
            RetrievalCase("where I paid Rahul", "payment_rahul_30", "NATURAL", 5),
            RetrievalCase("college schedule", "bca_timetable", "NATURAL", 5),
            RetrievalCase("cpp homework", "cpp_assignment", "NATURAL", 5),
            RetrievalCase("where she apologized", "apology_message", "NATURAL", 5),
            RetrievalCase("train ticket to Delhi", "flight_booking", "NATURAL", 5),
            RetrievalCase("booked flight to Delhi", "flight_booking", "NATURAL", 5),
            RetrievalCase("fee due", "college_fee_receipt", "NATURAL", 5),
            RetrievalCase("where to submit assignment", "teacher_notice", "NATURAL", 5),
            RetrievalCase("phone for mentor", "phone_number_contact", "NATURAL", 5),
            RetrievalCase("bank transfer ref", "bank_transfer", "NATURAL", 5)
        )

        val metadata = listOf(
            RetrievalCase("Screenshot_2026_09_21_payment.png", "payment_rahul_30", "METADATA", 5),
            RetrievalCase("Pictures/Screenshots", "payment_rahul_30", "METADATA", 5),
            RetrievalCase("Downloads/CollegeFees", "college_fee_receipt", "METADATA", 5),
            RetrievalCase("dbms_normalization.pdf", "dbms_normalization_pdf", "METADATA", 5),
            RetrievalCase("WhatsApp/Chats", "rahul_chat", "METADATA", 5),
            RetrievalCase("Documents/Travel", "flight_booking", "METADATA", 5),
            RetrievalCase("2026-09-25", "flight_booking", "METADATA", 5),
            RetrievalCase("12:40 PM", "flight_booking", "METADATA", 5),
            RetrievalCase("college fee", "college_fee_receipt", "METADATA", 5),
            RetrievalCase("receipt_30", "receipt_30", "METADATA", 5)
        )

        val ranking = listOf(
            RetrievalCase("Rahul 300", "payment_rahul_300", "RANKING", 5),
            RetrievalCase("Sahil 30", "payment_sahil_30", "RANKING", 5),
            RetrievalCase("30 receipt", "receipt_30", "RANKING", 5),
            RetrievalCase("payment 50", "payment_rahul_50", "RANKING", 5),
            RetrievalCase("train fare", "payment_rahul_50", "RANKING", 5),
            RetrievalCase("transaction AJX", "transaction_reference_ajx", "RANKING", 5),
            RetrievalCase("bank transfer 2500", "bank_transfer", "RANKING", 5),
            RetrievalCase("refund 42", "refund_status", "RANKING", 5),
            RetrievalCase("library fine 150", "library_due_notice", "RANKING", 5),
            RetrievalCase("medicine bill 420", "medicine_bill", "RANKING", 5)
        )

        val negative = listOf(
            RetrievalCase("lost moon rover", null, "NEGATIVE", 5, setOf()),
            RetrievalCase("space alien data", null, "NEGATIVE", 5, setOf()),
            RetrievalCase("midnight secret password", null, "NEGATIVE", 5, setOf()),
            RetrievalCase("mystery sudoku puzzle", null, "NEGATIVE", 5, setOf()),
            RetrievalCase("frozen asteroid maps", null, "NEGATIVE", 5, setOf()),
            RetrievalCase("unrelated tax explanation", null, "NEGATIVE", 5, setOf()),
            RetrievalCase("alien wedding invitation", null, "NEGATIVE", 5, setOf()),
            RetrievalCase("secret recipe for coffee", null, "NEGATIVE", 5, setOf()),
            RetrievalCase("mars colony permit", null, "NEGATIVE", 5, setOf()),
            RetrievalCase("robotic cooking guide", null, "NEGATIVE", 5, setOf())
        )

        val extraCoverage = listOf(
            RetrievalCase("Rahul Shaikh", "payment_rahul_30", "EXACT", 5),
            RetrievalCase("payment receipt Rahul", "payment_rahul_30", "ORDER_INDEPENDENCE", 5),
            RetrievalCase("30 rupees Rahul", "payment_rahul_30", "ORDER_INDEPENDENCE", 5),
            RetrievalCase("Rahuul", "payment_rahul_30", "TYPO", 5),
            RetrievalCase("₹300", "payment_rahul_300", "MONEY", 5),
            RetrievalCase("USD 42 refund", "refund_status", "MONEY", 5),
            RetrievalCase("where is the apology", "apology_message", "NATURAL", 5),
            RetrievalCase("meeting admin email", "email_document", "NATURAL", 5),
            RetrievalCase("travel site", "url_document", "METADATA", 5),
            RetrievalCase("book due", "library_due_notice", "METADATA", 5),
            RetrievalCase("apollo pharmacy", "medicine_bill", "RANKING", 5),
            RetrievalCase("roommate electricity", "hostel_roommate_chat", "RANKING", 5),
            RetrievalCase("friday 5 pm", "teacher_notice", "RANKING", 5),
            RetrievalCase("last flight to Delhi", "flight_booking", "RANKING", 5),
            RetrievalCase("private museum ticket", null, "NEGATIVE", 5, setOf())
        )

        return exact + orderIndependent + typo + money + natural + metadata + ranking + negative + extraCoverage
    }

    private data class BenchmarkFixture(
        val id: String,
        val entity: ScreenshotEntity
    )

    private data class RetrievalCase(
        val query: String,
        val expectedId: String?,
        val category: String,
        val expectedTopK: Int = 5,
        val negativeAllowedIds: Set<String> = emptySet()
    )

    private data class CaseOutcome(
        val category: String,
        val query: String,
        val expectedId: String?,
        val actualTopIds: List<String>,
        val rank: Int?,
        val top1: Boolean,
        val top3: Boolean,
        val top5: Boolean,
        val passed: Boolean,
        val durationMs: Double,
        val negativeAllowedIds: Set<String>
    ) {
        fun expectedTopK(): Int = when {
            expectedId == null -> 5
            else -> 5
        }
    }

    private class BenchmarkDao(private val records: List<ScreenshotEntity>) : ScreenshotDao {
        override suspend fun getAll(): List<ScreenshotEntity> = records
        override suspend fun getPage(category: String, limit: Int, offset: Int): List<ScreenshotEntity> = records
        override suspend fun getSyncMetadata(ids: List<Long>): List<ScreenshotSyncMetadata> = emptyList()
        override suspend fun getPending(limit: Int): List<ScreenshotEntity> = emptyList()
        override suspend fun markProcessing(mediaStoreId: Long) = Unit
        override suspend fun search(query: String, limit: Int): List<ScreenshotEntity> {
            val normalizedQuery = TextNormalizer.normalize(query)
            return records.filter { record ->
                val haystacks = listOf(
                    record.displayName,
                    record.relativePath.orEmpty(),
                    record.ocrText,
                    record.normalizedText,
                    record.structuredEntities,
                    record.category,
                    record.mimeType
                ).map(TextNormalizer::normalize)
                haystacks.any { it.contains(normalizedQuery, ignoreCase = true) }
            }.take(limit)
        }
        override suspend fun countAll(): Int = records.size
        override suspend fun countCategory(category: String): Int = records.count { it.category == category }
        override suspend fun countIndexed(): Int = records.size
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
}
