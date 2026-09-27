package com.fossick.app.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.fossick.app.data.local.ScreenshotEntity
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class UnsupportedContentException : Exception()

class ScreenshotOcrProcessor(private val contentResolver: ContentResolver) {
    fun extractText(item: ScreenshotEntity): String {
        return when {
            item.category == "SCREENSHOT" || item.category == "IMAGE" -> extractImageText(Uri.parse(item.uri))
            item.category in setOf("DOCUMENT", "SPREADSHEET") && isPlainText(item.mimeType, item.displayName) -> readText(Uri.parse(item.uri))
            item.category in setOf("DOCUMENT", "PRESENTATION", "SPREADSHEET") && isOfficeDocument(item.mimeType, item.displayName) -> readOfficeXml(Uri.parse(item.uri))
            else -> throw UnsupportedContentException()
        }
    }

    private fun extractImageText(uri: Uri): String {
        val bitmap = loadBitmap(uri) ?: throw IllegalStateException("Image could not be decoded")
        return try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val result = Tasks.await(
                    recognizer.process(InputImage.fromBitmap(bitmap, 0)),
                    60,
                    TimeUnit.SECONDS
                )
                result.text.trim().replace(Regex("\\s+"), " ")
            } finally {
                recognizer.close()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun readText(uri: Uri): String {
        val builder = StringBuilder()
        contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).useLines { lines ->
                for (line in lines) {
                    if (builder.length >= MAX_TEXT_BYTES) break
                    builder.append(line).append('\n')
                }
            }
        } ?: throw IllegalStateException("Document could not be read")
        return builder.toString().take(MAX_TEXT_BYTES)
    }

    private fun readOfficeXml(uri: Uri): String {
        val builder = StringBuilder()
        contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (builder.length < MAX_TEXT_BYTES) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.endsWith(".xml", ignoreCase = true)) {
                        val bytes = zip.readBytesLimited(MAX_TEXT_BYTES - builder.length)
                        builder.append(String(bytes, Charsets.UTF_8).replace(Regex("<[^>]+>"), " ")).append(' ')
                    }
                    zip.closeEntry()
                }
            }
        } ?: throw IllegalStateException("Office document could not be read")
        return builder.toString().take(MAX_TEXT_BYTES)
    }

    private fun java.util.zip.ZipInputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var remaining = limit
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        return output.toByteArray()
    }

    private fun isPlainText(mimeType: String, name: String): Boolean =
        mimeType.startsWith("text/") || name.substringAfterLast('.', "").lowercase() in setOf("txt", "csv", "json", "xml")

    private fun isOfficeDocument(mimeType: String, name: String): Boolean =
        mimeType.contains("openxml") || name.substringAfterLast('.', "").lowercase() in setOf("docx", "pptx", "xlsx")

    private fun loadBitmap(uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val maxDimension = maxOf(info.size.width, info.size.height)
                if (maxDimension > 2048) {
                    val scale = 2048f / maxDimension
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options().apply { inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight) }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width / sample, height / sample) > 2048) sample *= 2
        return sample
    }

    companion object {
        private const val MAX_TEXT_BYTES = 1_000_000
    }
}
