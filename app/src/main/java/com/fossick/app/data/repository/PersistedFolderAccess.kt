package com.fossick.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.fossick.app.data.model.MemoryCategory
import com.fossick.app.data.model.ScreenshotItem
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.ArrayDeque

data class FolderDiscoveryResult(
    val items: List<ScreenshotItem>,
    val isComplete: Boolean
)

class PersistedFolderAccess(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun rememberLocation(uri: Uri) {
        val locations = preferences.getStringSet(LOCATION_KEY, emptySet()).orEmpty().toMutableSet()
        locations += uri.toString()
        preferences.edit().putStringSet(LOCATION_KEY, locations).apply()
    }

    fun hasLocations(): Boolean = preferences.getStringSet(LOCATION_KEY, emptySet()).orEmpty().isNotEmpty()

    fun discover(): FolderDiscoveryResult {
        val savedLocations = preferences.getStringSet(LOCATION_KEY, emptySet()).orEmpty()
        val persistedUris = appContext.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .mapTo(mutableSetOf()) { it.uri.toString() }
        val results = mutableListOf<ScreenshotItem>()
        var complete = true

        for (savedUri in savedLocations) {
            if (savedUri !in persistedUris) {
                complete = false
                continue
            }
            try {
                val result = discoverTree(Uri.parse(savedUri))
                results += result.items
                complete = complete && result.isComplete
            } catch (_: Exception) {
                complete = false
            }
        }
        return FolderDiscoveryResult(results.distinctBy { it.uri }, complete)
    }

    private fun discoverTree(treeUri: Uri): FolderDiscoveryResult {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(rootId to "")
        val results = mutableListOf<ScreenshotItem>()
        var complete = true

        while (queue.isNotEmpty() && results.size < MAX_DISCOVERED_ITEMS) {
            val (parentId, parentPath) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val cursor = appContext.contentResolver.query(childrenUri, DOCUMENT_COLUMNS, null, null, null)
            if (cursor == null) {
                complete = false
                continue
            }
            cursor.use {
                val idColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (it.moveToNext() && results.size < MAX_DISCOVERED_ITEMS) {
                    if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0) {
                        complete = false
                        break
                    }
                    val documentId = it.getString(idColumn) ?: continue
                    val name = it.getString(nameColumn).orEmpty()
                    val mime = it.getString(mimeColumn).orEmpty()
                    val path = if (parentPath.isBlank()) name else "$parentPath/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        queue.add(documentId to path)
                        continue
                    }
                    val category = MemoryCategory.fromMedia(mime, name, path) ?: continue
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val modifiedMillis = if (modifiedColumn >= 0 && !it.isNull(modifiedColumn)) it.getLong(modifiedColumn) else 0L
                    results += ScreenshotItem(
                        id = stableId(documentUri),
                        uri = documentUri,
                        displayName = name,
                        dateAdded = 0L,
                        dateModified = modifiedMillis / 1000L,
                        relativePath = path,
                        mimeType = mime,
                        category = category,
                        size = if (sizeColumn >= 0 && !it.isNull(sizeColumn)) it.getLong(sizeColumn) else 0L,
                        sourceType = "SAF"
                    )
                }
            }
        }
        if (queue.isNotEmpty()) complete = false
        return FolderDiscoveryResult(results, complete)
    }

    private fun stableId(uri: Uri): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray(Charsets.UTF_8))
        val value = ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long
        return Long.MIN_VALUE or (value and Long.MAX_VALUE)
    }

    companion object {
        private const val PREFERENCES_NAME = "fossick_locations"
        private const val LOCATION_KEY = "persisted_tree_uris"
        private const val MAX_DISCOVERED_ITEMS = 50_000
        private val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }
}