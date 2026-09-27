package com.fossick.app.data.model

import java.util.Locale

object MemoryCategory {
    const val ALL = "ALL"
    const val SCREENSHOT = "SCREENSHOT"
    const val CAMERA = "CAMERA"
    const val IMAGE = "IMAGE"
    const val VIDEO = "VIDEO"
    const val PDF = "PDF"
    const val DOCUMENT = "DOCUMENT"
    const val PRESENTATION = "PRESENTATION"
    const val SPREADSHEET = "SPREADSHEET"

    fun matchesFilter(filter: String, category: String): Boolean = when (filter) {
        ALL -> true
        "IMAGES" -> category == IMAGE
        "DOCUMENTS" -> category == DOCUMENT
        else -> filter == category
    }

    fun fromMedia(mimeType: String, displayName: String, relativePath: String?): String? {
        val mime = mimeType.lowercase(Locale.ROOT)
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val image = mime.startsWith("image/") || extension in imageExtensions

        if (image && (relativePath.orEmpty().contains("screenshot", ignoreCase = true) ||
                displayName.contains("screenshot", ignoreCase = true))
        ) return SCREENSHOT
        if (image && relativePath.orEmpty().replace('\\', '/').contains("dcim/camera", ignoreCase = true)) {
            return CAMERA
        }

        return when {
            mime == "application/pdf" || extension == "pdf" -> PDF
            mime.startsWith("video/") || extension in videoExtensions -> VIDEO
            mime in presentationMimeTypes || extension in presentationExtensions -> PRESENTATION
            mime in spreadsheetMimeTypes || extension in spreadsheetExtensions -> SPREADSHEET
            mime.startsWith("image/") || extension in imageExtensions -> IMAGE
            mime.startsWith("text/") || mime in otherDocumentMimeTypes || extension in documentExtensions -> DOCUMENT
            else -> null
        }
    }

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")
    private val videoExtensions = setOf("mp4", "m4v", "mov", "3gp", "mkv", "webm", "avi")
    private val documentExtensions = setOf("txt", "json", "xml", "doc", "docx")
    private val presentationExtensions = setOf("ppt", "pptx")
    private val spreadsheetExtensions = setOf("csv", "xls", "xlsx")
    private val presentationMimeTypes = setOf(
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    )
    private val spreadsheetMimeTypes = setOf(
        "text/csv",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    private val otherDocumentMimeTypes = setOf("application/json", "application/xml", "text/xml", "application/msword")
}