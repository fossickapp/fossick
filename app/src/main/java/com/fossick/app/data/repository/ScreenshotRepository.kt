package com.fossick.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.fossick.app.data.local.ScreenshotIndexAggregate
import com.fossick.app.data.local.OcrStatus
import com.fossick.app.data.local.ScreenshotDao
import com.fossick.app.data.local.ScreenshotEntity
import com.fossick.app.data.model.MemoryCategory
import com.fossick.app.data.model.IndexingPolicy
import com.fossick.app.data.model.ScreenshotItem
import java.io.File
import java.security.MessageDigest
import java.nio.ByteBuffer
import android.webkit.MimeTypeMap

private const val SYNC_BATCH_SIZE = 400
private const val CURRENT_CONTENT_INDEX_VERSION = 1

private data class DiscoverySnapshot(
    val items: List<ScreenshotItem>,
    val isComplete: Boolean,
    val mediaStoreComplete: Boolean,
    val filesystemComplete: Boolean,
    val folderComplete: Boolean,
    val sourceDiagnostics: Map<String, String>,
    val failedSources: Map<String, String>
)

private data class SourceDiscovery(
    val items: List<ScreenshotItem>,
    val queriedRows: Int,
    val permissionGranted: Boolean? = null,
    val complete: Boolean = true
)

class ScreenshotRepository(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val folderAccess = PersistedFolderAccess(appContext)

    fun rememberFolder(uri: android.net.Uri) = folderAccess.rememberLocation(uri)

    fun findScreenshots(): List<ScreenshotItem> {
        val columns = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            columns += MediaStore.Images.Media.RELATIVE_PATH
        }

        return try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                columns.toTypedArray(),
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val addedColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val modifiedColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val pathColumn = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                val results = mutableListOf<ScreenshotItem>()

                while (cursor.moveToNext()) {
                    if (idColumn < 0) continue
                    val id = cursor.getLong(idColumn)
                    val displayName = nameColumn.takeIf { it >= 0 }?.let(cursor::getString).orEmpty()
                    val relativePath = pathColumn.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString)
                    val isScreenshot = relativePath?.contains("screenshots", ignoreCase = true) == true ||
                        displayName.contains("screenshot", ignoreCase = true)

                    if (isScreenshot) {
                        results += ScreenshotItem(
                            id = id,
                            uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                            displayName = displayName,
                            dateAdded = addedColumn.takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L,
                            dateModified = modifiedColumn.takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L,
                            relativePath = relativePath
                        )
                    }
                }
                results
            }.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    suspend fun synchronize(dao: ScreenshotDao): Int {
        val snapshot = discoverAvailableItems()
        val discovered = snapshot.items
        val currentIds = discovered.mapTo(mutableSetOf()) { it.id }
        for (batch in discovered.chunked(SYNC_BATCH_SIZE)) {
            val existing = dao.getSyncMetadata(batch.map { it.id }).associateBy { it.mediaStoreId }
            val recordsToIndex = mutableListOf<ScreenshotEntity>()
            for (item in batch) {
                val previous = existing[item.id]
                if (!IndexingPolicy.contentChanged(
                        previous = previous,
                        dateModified = item.dateModified,
                        size = item.size,
                        mimeType = item.mimeType,
                        contentIndexVersion = CURRENT_CONTENT_INDEX_VERSION
                    )
                ) {
                    val unchanged = checkNotNull(previous)
                    val uri = item.uri.toString()
                    if (unchanged.uri != uri || unchanged.displayName != item.displayName ||
                        unchanged.relativePath != item.relativePath || unchanged.mimeType != item.mimeType ||
                        unchanged.category != item.category || unchanged.size != item.size ||
                        unchanged.sourceType != item.sourceType || unchanged.dateAdded != item.dateAdded ||
                        unchanged.dateModified != item.dateModified
                    ) {
                        dao.updateMetadata(
                            mediaStoreId = item.id,
                            uri = uri,
                            displayName = item.displayName,
                            relativePath = item.relativePath,
                            mimeType = item.mimeType,
                            category = item.category,
                            size = item.size,
                            sourceType = item.sourceType,
                            dateAdded = item.dateAdded,
                            dateModified = item.dateModified
                        )
                    }
                    continue
                }

                recordsToIndex += ScreenshotEntity(
                    mediaStoreId = item.id,
                    uri = item.uri.toString(),
                    displayName = item.displayName,
                    relativePath = item.relativePath,
                    mimeType = item.mimeType,
                    category = item.category,
                    size = item.size,
                    sourceType = item.sourceType,
                    dateAdded = item.dateAdded,
                    dateModified = item.dateModified,
                    ocrText = "",
                    normalizedText = "",
                    structuredEntities = "",
                    ocrStatus = if (item.category == MemoryCategory.VIDEO || item.category == MemoryCategory.PDF) OcrStatus.UNSUPPORTED else OcrStatus.PENDING,
                    contentIndexVersion = 1,
                    lastIndexedModifiedTime = item.dateModified,
                    lastIndexedAt = 0L,
                    errorState = if (item.category == MemoryCategory.VIDEO || item.category == MemoryCategory.PDF) "CONTENT_UNSUPPORTED" else null
                )
            }
            if (recordsToIndex.isNotEmpty()) dao.upsertAll(recordsToIndex)
        }
        if (snapshot.mediaStoreComplete && hasCompleteMediaAccess()) {
            pruneMissing(dao, "MEDIASTORE", currentIds)
        }
        if (snapshot.filesystemComplete && StorageAccess.hasAllFilesAccess()) {
            pruneMissing(dao, "FILESYSTEM", currentIds)
        }
        if (folderAccess.hasLocations() && snapshot.folderComplete) {
            pruneMissing(dao, "SAF", currentIds)
        }
        logDiagnostics(snapshot, dao.getIndexAggregateCounts())
        return discovered.size
    }

    fun findDiscoverableItems(): List<ScreenshotItem> {
        return discoverAvailableItems().items
    }

    private fun discoverAvailableItems(): DiscoverySnapshot {
        val sources = listOf(
            "MediaStore.Images" to ::findImageItems,
            "MediaStore.Video" to ::findVideoItems,
            "MediaStore.Files" to ::findDocumentItems,
            "MediaStore.Downloads" to ::findDownloadItems,
            "Filesystem" to ::findSharedFileItems
        )
        val discovered = mutableListOf<ScreenshotItem>()
        val sourceDiagnostics = linkedMapOf<String, String>()
        val failedSources = linkedMapOf<String, String>()
        var mediaStoreComplete = true
        var filesystemComplete = true
        for ((name, source) in sources) {
            try {
                val result = source()
                discovered += result.items
                if (result.permissionGranted == false) mediaStoreComplete = false
                if (name == "Filesystem" && (result.permissionGranted == false || !result.complete)) filesystemComplete = false
                sourceDiagnostics[name] = "rows=${result.queriedRows}, supported=${result.items.size}, permission=${result.permissionGranted ?: "not-required"}"
            } catch (exception: Exception) {
                if (name == "Filesystem") filesystemComplete = false else mediaStoreComplete = false
                failedSources[name] = exception.javaClass.simpleName
            }
        }
        val folderSnapshot = folderAccess.discover()
        discovered += folderSnapshot.items
        sourceDiagnostics["SAF"] = "supported=${folderSnapshot.items.size}, configured=${folderAccess.hasLocations()}, complete=${folderSnapshot.isComplete}"
        return DiscoverySnapshot(
            items = discovered.distinctBy(::discoveryIdentity),
            isComplete = mediaStoreComplete && filesystemComplete && folderSnapshot.isComplete,
            mediaStoreComplete = mediaStoreComplete,
            filesystemComplete = filesystemComplete,
            folderComplete = folderSnapshot.isComplete,
            sourceDiagnostics = sourceDiagnostics,
            failedSources = failedSources
        )
    }

    private suspend fun pruneMissing(dao: ScreenshotDao, sourceType: String, currentIds: Set<Long>) {
        var afterId = Long.MIN_VALUE
        while (true) {
            val storedIds = dao.getIdsAfter(sourceType, afterId, SYNC_BATCH_SIZE)
            if (storedIds.isEmpty()) break
            afterId = storedIds.last()
            val missingIds = storedIds.filterNot { it in currentIds }
            if (missingIds.isNotEmpty()) dao.deleteByIds(missingIds)
        }
    }

    private fun findImageItems(): SourceDiscovery {
        val hasAccess = canReadImages()
        if (!hasAccess) return SourceDiscovery(emptyList(), 0, permissionGranted = false)
        val columns = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) columns += MediaStore.Images.Media.RELATIVE_PATH
        val result = queryMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns.toTypedArray()) { cursor, index ->
            val name = cursor.string(index, MediaStore.Images.Media.DISPLAY_NAME).orEmpty()
            MemoryCategory.fromMedia(
                cursor.string(index, MediaStore.Images.Media.MIME_TYPE).orEmpty(),
                name,
                cursor.string(index, MediaStore.Images.Media.RELATIVE_PATH)
            )?.takeIf { it == MemoryCategory.SCREENSHOT || it == MemoryCategory.CAMERA || it == MemoryCategory.IMAGE }
        }
        return SourceDiscovery(result.items, result.queriedRows, permissionGranted = true)
    }

    private fun findVideoItems(): SourceDiscovery {
        val hasAccess = canReadVideos()
        if (!hasAccess) return SourceDiscovery(emptyList(), 0, permissionGranted = false)
        val columns = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) columns += MediaStore.Video.Media.RELATIVE_PATH
        val result = queryMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, columns.toTypedArray()) { _, _ -> "VIDEO" }
        return SourceDiscovery(result.items, result.queriedRows, permissionGranted = true)
    }

    private fun findDocumentItems(): SourceDiscovery {
        val columns = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) columns += MediaStore.Files.FileColumns.RELATIVE_PATH
        val uri = MediaStore.Files.getContentUri("external")
        val result = queryMediaStore(uri, columns.toTypedArray()) { cursor, index ->
            val mediaType = cursor.int(index, MediaStore.Files.FileColumns.MEDIA_TYPE)
            if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ||
                mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            ) return@queryMediaStore null
            MemoryCategory.fromMedia(
                cursor.string(index, MediaStore.Files.FileColumns.MIME_TYPE).orEmpty(),
                cursor.string(index, MediaStore.Files.FileColumns.DISPLAY_NAME).orEmpty(),
                cursor.string(index, MediaStore.Files.FileColumns.RELATIVE_PATH)
            )?.takeIf { it !in setOf(MemoryCategory.IMAGE, MemoryCategory.SCREENSHOT, MemoryCategory.VIDEO) }
        }
        return SourceDiscovery(result.items, result.queriedRows)
    }

    private fun findDownloadItems(): SourceDiscovery {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return SourceDiscovery(emptyList(), 0)
        val columns = mutableListOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.MIME_TYPE,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_ADDED,
            MediaStore.Downloads.DATE_MODIFIED,
            MediaStore.Downloads.RELATIVE_PATH
        )
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val result = queryMediaStore(collection, columns.toTypedArray()) { cursor, index ->
            MemoryCategory.fromMedia(
                cursor.string(index, MediaStore.Downloads.MIME_TYPE).orEmpty(),
                cursor.string(index, MediaStore.Downloads.DISPLAY_NAME).orEmpty(),
                cursor.string(index, MediaStore.Downloads.RELATIVE_PATH)
            )?.takeIf { it !in setOf(MemoryCategory.IMAGE, MemoryCategory.SCREENSHOT, MemoryCategory.CAMERA, MemoryCategory.VIDEO) }
        }
        return SourceDiscovery(result.items, result.queriedRows)
    }

    private fun findSharedFileItems(): SourceDiscovery {
        if (!StorageAccess.canScanSharedFiles(appContext)) {
            return SourceDiscovery(emptyList(), 0, permissionGranted = false, complete = false)
        }

        val root = Environment.getExternalStorageDirectory().canonicalFile
        val pendingDirectories = ArrayDeque<File>()
        val visitedDirectories = mutableSetOf<String>()
        val results = mutableListOf<ScreenshotItem>()
        var complete = true
        pendingDirectories.add(root)

        while (pendingDirectories.isNotEmpty()) {
            if (results.size >= MAX_FILESYSTEM_ITEMS) {
                complete = false
                break
            }
            val directory = pendingDirectories.removeFirst()
            val canonicalDirectory = runCatching { directory.canonicalPath }.getOrNull() ?: run {
                complete = false
                continue
            }
            if (!visitedDirectories.add(canonicalDirectory)) continue

            val children = try {
                directory.listFiles()
            } catch (_: SecurityException) {
                complete = false
                null
            }
            if (children == null) {
                complete = false
                continue
            }

            for (child in children) {
                val relative = runCatching { root.toPath().relativize(child.canonicalFile.toPath()).toString().replace('\\', '/') }
                    .getOrNull() ?: continue
                if (relative.startsWith("Android/data/", ignoreCase = true) ||
                    relative.startsWith("Android/obb/", ignoreCase = true) ||
                    relative.equals("Android/data", ignoreCase = true) ||
                    relative.equals("Android/obb", ignoreCase = true)
                ) continue

                if (child.isDirectory) {
                    pendingDirectories.add(child)
                    continue
                }
                if (!child.isFile) continue

                val name = child.name
                val parentPath = relative.substringBeforeLast('/', "").let { if (it.isBlank()) "" else "$it/" }
                val mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                    ?: "application/octet-stream"
                val category = MemoryCategory.fromMedia(mimeType, name, parentPath) ?: continue
                val identity = sharedPathIdentity(parentPath, name)
                val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", child)
                results += ScreenshotItem(
                    id = stableId(identity),
                    uri = uri,
                    displayName = name,
                    dateAdded = 0L,
                    dateModified = child.lastModified() / 1000L,
                    relativePath = parentPath,
                    mimeType = mimeType,
                    category = category,
                    size = child.length(),
                    sourceType = "FILESYSTEM"
                )
                if (results.size >= MAX_FILESYSTEM_ITEMS) {
                    complete = false
                    break
                }
            }
        }
        return SourceDiscovery(results, results.size, permissionGranted = true, complete = complete)
    }

    private fun discoveryIdentity(item: ScreenshotItem): String {
        if (item.sourceType == "SAF") {
            val documentId = runCatching { android.provider.DocumentsContract.getDocumentId(item.uri) }.getOrNull()
            val separator = documentId?.indexOf(':') ?: -1
            if (separator > 0 && documentId!!.substring(0, separator).equals("primary", ignoreCase = true)) {
                return sharedPathIdentity("", documentId.substring(separator + 1))
            }
        }
        val relativePath = item.relativePath
        return if (relativePath != null) sharedPathIdentity(relativePath, item.displayName) else item.uri.normalizeScheme().toString()
    }

    private fun sharedPathIdentity(relativePath: String, displayName: String): String =
        "shared:${(relativePath + displayName).replace('\\', '/').replace(Regex("/+$"), "")}".lowercase()

    private fun stableId(identity: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return Long.MIN_VALUE or (ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long and Long.MAX_VALUE)
    }

    private data class QueryResult(val items: List<ScreenshotItem>, val queriedRows: Int)

    private fun queryMediaStore(
        collection: android.net.Uri,
        columns: Array<String>,
        categoryFor: (android.database.Cursor, Map<String, Int>) -> String?
    ): QueryResult {
        var queriedRows = 0
        val items = contentResolver.query(collection, columns, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                val indexes = columns.associateWith { cursor.getColumnIndex(it) }
                val results = mutableListOf<ScreenshotItem>()
                while (cursor.moveToNext()) {
                    queriedRows++
                    val id = cursor.long(indexes, MediaStore.MediaColumns._ID) ?: continue
                    val category = categoryFor(cursor, indexes) ?: continue
                    results += ScreenshotItem(
                        id = namespacedId(collection, id),
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = cursor.string(indexes, MediaStore.MediaColumns.DISPLAY_NAME).orEmpty(),
                        dateAdded = cursor.long(indexes, MediaStore.MediaColumns.DATE_ADDED) ?: 0L,
                        dateModified = cursor.long(indexes, MediaStore.MediaColumns.DATE_MODIFIED) ?: 0L,
                        relativePath = cursor.string(indexes, MediaStore.MediaColumns.RELATIVE_PATH),
                        mimeType = cursor.string(indexes, MediaStore.MediaColumns.MIME_TYPE) ?: "application/octet-stream",
                        category = category,
                        size = cursor.long(indexes, MediaStore.MediaColumns.SIZE) ?: 0L
                    )
                }
                results
        } ?: throw IllegalStateException("MediaStore query returned no cursor")
            return QueryResult(items, queriedRows)
    }

    private fun namespacedId(collection: android.net.Uri, id: Long): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            collection == MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
        ) return -(id * 2L + 2L)
        return when (collection) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI -> -(id * 2L + 1L)
            MediaStore.Files.getContentUri("external") -> -(id * 2L + 2L)
            else -> id
        }
    }

    private suspend fun logDiagnostics(snapshot: DiscoverySnapshot, aggregates: List<ScreenshotIndexAggregate>) {
        if (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        Log.i(DIAGNOSTIC_TAG, "sources=${snapshot.sourceDiagnostics}; failed=${snapshot.failedSources}; uniqueDiscovered=${snapshot.items.size}; complete=${snapshot.isComplete}")
        Log.i(DIAGNOSTIC_TAG, "room category/source/status counts=$aggregates")
    }

    private fun canReadImages(): Boolean = StorageAccess.hasAllFilesAccess() || when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> hasPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            hasPermission(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        else -> hasPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun canReadVideos(): Boolean = StorageAccess.hasAllFilesAccess() || if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasPermission(android.Manifest.permission.READ_MEDIA_VIDEO) ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            hasPermission(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    } else {
        hasPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasCompleteMediaAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        StorageAccess.hasAllFilesAccess() || hasPermission(android.Manifest.permission.READ_MEDIA_IMAGES) &&
            hasPermission(android.Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        hasPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasPermission(permission: String): Boolean =
        appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun android.database.Cursor.string(indexes: Map<String, Int>, column: String): String? {
        val index = indexes[column] ?: -1
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun android.database.Cursor.long(indexes: Map<String, Int>, column: String): Long? {
        val index = indexes[column] ?: -1
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private fun android.database.Cursor.int(indexes: Map<String, Int>, column: String): Int {
        val index = indexes[column] ?: -1
        return if (index >= 0 && !isNull(index)) getInt(index) else -1
    }

    companion object {
        private const val DIAGNOSTIC_TAG = "FossickDiscovery"
        private const val MAX_FILESYSTEM_ITEMS = 200_000
    }

}