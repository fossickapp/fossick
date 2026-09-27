package com.fossick.app.content

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ContentExtractorTest {
    @Test
    fun plainTextExtractionReadsStructuredText() {
        val file = createTempFile(prefix = "memory", suffix = ".json")
        file.writeText("{\"title\":\"C++ Assignment\",\"notes\":\"Paid ₹30 to Rahul\"}")

        val extracted = ContentExtractorRegistry.extract(file)

        assertTrue(extracted.contains("C++ Assignment"))
        assertTrue(extracted.contains("₹30"))
        assertTrue(extracted.contains("Rahul"))
    }

    @Test
    fun docxExtractionReadsWordXml() {
        val file = createTempFile(prefix = "doc", suffix = ".docx")
        val zip = ZipOutputStream(file.outputStream())
        zip.putNextEntry(ZipEntry("word/document.xml"))
        zip.write("<w:p><w:t>College timetable 2026</w:t></w:p>".toByteArray())
        zip.closeEntry()
        zip.close()

        val extracted = ContentExtractorRegistry.extract(file)

        assertTrue(extracted.contains("College timetable"))
        assertTrue(extracted.contains("2026"))
    }
}
