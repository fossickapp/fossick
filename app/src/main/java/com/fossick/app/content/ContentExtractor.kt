package com.fossick.app.content

import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.ZipFile

interface ContentExtractor {
    fun supports(file: File): Boolean
    fun extract(file: File): String
}

object ContentExtractorRegistry {
    private val extractors: List<ContentExtractor> = listOf(
        PlainTextExtractor(),
        DocxExtractor(),
        PptxExtractor(),
        XlsxExtractor(),
        PdfExtractor()
    )

    fun extract(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        return extractors.firstOrNull { it.supports(file) }?.extract(file).orEmpty()
    }
}

class PlainTextExtractor : ContentExtractor {
    override fun supports(file: File): Boolean {
        val lower = file.name.lowercase(Locale.ROOT)
        return lower.endsWith(".txt") || lower.endsWith(".csv") || lower.endsWith(".json") || lower.endsWith(".xml")
    }

    override fun extract(file: File): String {
        val text = file.readText(Charset.forName("UTF-8"))
        return text
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}

class DocxExtractor : ContentExtractor {
    override fun supports(file: File): Boolean = file.name.lowercase(Locale.ROOT).endsWith(".docx")

    override fun extract(file: File): String {
        return readZipText(file, "word/document.xml") { xml ->
            val matches = Regex("<w:t[^>]*>(.*?)</w:t>").findAll(xml)
            matches.map { it.groupValues[1] }.joinToString(" ")
        }
    }
}

class PptxExtractor : ContentExtractor {
    override fun supports(file: File): Boolean = file.name.lowercase(Locale.ROOT).endsWith(".pptx")

    override fun extract(file: File): String {
        val parts = mutableListOf<String>()
        ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.startsWith("ppt/slides/") && it.name.endsWith(".xml") }
                .forEach { entry ->
                    val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    val text = Regex("<a:t[^>]*>(.*?)</a:t>").findAll(xml)
                        .joinToString(" ") { match -> match.groupValues[1] }
                    if (text.isNotBlank()) parts += text
                }
        }
        return parts.joinToString(" ")
    }
}

class XlsxExtractor : ContentExtractor {
    override fun supports(file: File): Boolean = file.name.lowercase(Locale.ROOT).endsWith(".xlsx")

    override fun extract(file: File): String {
        val strings = mutableListOf<String>()
        ZipFile(file).use { zip ->
            val sharedStrings = zip.getEntry("xl/sharedStrings.xml")
            if (sharedStrings != null) {
                val xml = zip.getInputStream(sharedStrings).bufferedReader().use { it.readText() }
                Regex("<t[^>]*>(.*?)</t>").findAll(xml).forEach { strings += it.groupValues[1] }
            }
            zip.entries().asSequence()
                .filter { it.name.startsWith("xl/worksheets/") && it.name.endsWith(".xml") }
                .forEach { sheetEntry ->
                    val xml = zip.getInputStream(sheetEntry).bufferedReader().use { it.readText() }
                    val values = Regex("<v[^>]*>(.*?)</v>").findAll(xml)
                        .map { match -> match.groupValues[1] }
                        .joinToString(" ")
                    if (values.isNotBlank()) strings += values
                }
        }
        return strings.joinToString(" ")
    }
}

class PdfExtractor : ContentExtractor {
    override fun supports(file: File): Boolean = file.name.lowercase(Locale.ROOT).endsWith(".pdf")

    override fun extract(file: File): String = ""
}

private fun readZipText(file: File, entryName: String, parser: (String) -> String): String {
    ZipFile(file).use { zip ->
        val entry = zip.getEntry(entryName) ?: return ""
        val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
        return parser(text)
    }
}
