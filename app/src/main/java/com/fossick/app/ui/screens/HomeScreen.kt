package com.fossick.app.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fossick.app.data.local.ScreenshotEntity
import com.fossick.app.data.model.MemoryCategory
import com.fossick.app.data.repository.StorageAccess
import com.fossick.app.ui.components.FossickResult
import com.fossick.app.ui.components.FossickSearch
import com.fossick.app.ui.components.FossickWordmark
import com.fossick.app.ui.components.MemoryThumbnail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onStartIndexing: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val navStack = remember { androidx.compose.runtime.mutableStateListOf("SEARCH") }
    val selectedDestination = navStack.last()
    var aboutPage by rememberSaveable { mutableStateOf(AboutPage.HOME) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var setupStep by rememberSaveable {
        mutableIntStateOf(if (context.getSharedPreferences("fossick_access", 0).getBoolean("setup_complete", false)) 2 else 0)
    }

    fun hasContentAccess(): Boolean {
        return StorageAccess.hasAllFilesAccess() || StorageAccess.hasMediaAccess(context)
    }

    fun hasVideoAccess(): Boolean = StorageAccess.hasAllFilesAccess() || StorageAccess.hasMediaAccess(context)

    fun finishSetup(message: String? = null) {
        context.getSharedPreferences("fossick_access", 0).edit().putBoolean("setup_complete", true).apply()
        setupStep = 2
        permissionMessage = message
        viewModel.resumeIndexing()
    }

    fun openItem(item: ScreenshotEntity) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(android.net.Uri.parse(item.uri), item.mimeType.ifBlank { "*/*" })
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        } catch (_: ActivityNotFoundException) {
            permissionMessage = "No compatible app is available to open this item."
        } catch (_: SecurityException) {
            permissionMessage = "This item is no longer available to Fossick."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        finishSetup(if (results.values.any { it } || hasContentAccess()) null else "Limited access is active. Add a folder location or change storage access in About.")
    }
    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (StorageAccess.hasAllFilesAccess()) {
            finishSetup("Broad shared-storage access is enabled. Fossick is discovering supported files locally.")
        } else {
            finishSetup("All Files Access was not enabled. Fossick continues in limited-access mode.")
        }
    }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                viewModel.addFolderLocation(uri)
                permissionMessage = null
            } catch (_: SecurityException) {
                permissionMessage = "Android could not persist access to that folder."
            }
        }
    }

    BackHandler(enabled = navStack.size > 1) {
        navStack.removeAt(navStack.lastIndex)
        if (selectedCategory != null) selectedCategory = null
    }

    BackHandler(enabled = navStack.size == 1 && selectedCategory != null) {
        selectedCategory = null
    }

    BackHandler(enabled = navStack.size == 1 && aboutPage != AboutPage.HOME) {
        aboutPage = AboutPage.HOME
    }

    LaunchedEffect(Unit) {
        if (hasContentAccess()) viewModel.resumeIndexing()
    }

    if (setupStep < 2) {
        AccessSetupScreen(
            isWelcome = setupStep == 0,
            allFilesGranted = StorageAccess.hasAllFilesAccess(),
            message = permissionMessage,
            onContinue = { setupStep = 1 },
            onRequestAccess = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        allFilesSettingsLauncher.launch(StorageAccess.allFilesSettingsIntent(context))
                    } catch (_: ActivityNotFoundException) {
                        try {
                            allFilesSettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        } catch (_: ActivityNotFoundException) {
                            permissionMessage = "Android could not open All Files Access settings. You can continue with limited access."
                        }
                    }
                } else {
                    permissionLauncher.launch(StorageAccess.mediaPermissions())
                }
            },
            onLimitedAccess = { permissionLauncher.launch(StorageAccess.mediaPermissions()) },
            onContinueLimited = { finishSetup() }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        when (selectedDestination) {
            "SEARCH" -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        FossickWordmark(modifier = Modifier.width(138.dp).height(34.dp))
                    }
                }
                item {
                    Text(
                        text = "Find what you remember.",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 31.sp,
                        lineHeight = 37.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    FossickSearch(
                        query = query,
                        onQueryChange = {
                            query = it
                            if (submittedQuery.isNotBlank()) submittedQuery = ""
                        },
                        onSubmit = {
                            submittedQuery = query.trim()
                            viewModel.search(submittedQuery)
                        }
                    )
                }
                if (submittedQuery.isBlank()) {
                    item {
                        Text(
                            "Search names, folders and content understood locally.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when {
                                    uiState.isSearching -> "Searching"
                                    uiState.results.isEmpty() -> "Nothing found"
                                    else -> "${uiState.results.size} found"
                                },
                                color = when {
                                    uiState.isSearching -> MaterialTheme.colorScheme.onSurfaceVariant
                                    uiState.results.isEmpty() -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.tertiary
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("  ·  $submittedQuery", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                    if (uiState.isSearching) {
                        item { Text("Searching the local index…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) }
                    } else if (uiState.results.isEmpty()) {
                        item { Text("No indexed items match this description.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) }
                    } else {
                        items(uiState.results, key = { "result-${it.mediaStoreId}" }) { item ->
                            FossickResult(
                                type = categoryLabel(item.category),
                                title = item.displayName,
                                metadata = item.relativePath.orEmpty(),
                                date = formatDate(item.dateModified.takeIf { it > 0 } ?: item.dateAdded),
                                snippet = matchingSnippet(item, submittedQuery),
                                onClick = { openItem(item) }
                            )
                        }
                    }
                }
                permissionMessage?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                }
                uiState.errorMessage?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                }
            }

            "BROWSE" -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (selectedCategory == null) {
                    item {
                        Text("Browse", color = MaterialTheme.colorScheme.onSurface, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    }
                    items(BROWSE_CATEGORIES.chunked(2), key = { it.joinToString("-") }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            row.forEach { category ->
                                CategoryDestination(
                                    category = category,
                                    count = categoryCount(category, uiState),
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selectedCategory = category
                                        viewModel.loadBrowse(category, reset = true)
                                    }
                                )
                            }
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    if (!hasContentAccess() && uiState.discoveredCount == 0) {
                        item {
                            Text(
                                "Grant media access or add a folder in About to browse available files.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "←",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 23.sp,
                                modifier = Modifier.size(48.dp).clickable { selectedCategory = null }
                                    .padding(10.dp).semantics { contentDescription = "Back to Browse categories"; role = Role.Button }
                            )
                            Text(
                                categoryLabel(selectedCategory!!),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(categoryCount(selectedCategory!!, uiState).toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                    val photoGrid = selectedCategory in setOf(
                        MemoryCategory.SCREENSHOT,
                        MemoryCategory.CAMERA,
                        "IMAGES",
                        MemoryCategory.VIDEO
                    )
                    if (uiState.browseItems.isEmpty() && !uiState.browseLoading) {
                        when {
                            selectedCategory == MemoryCategory.VIDEO && !hasVideoAccess() -> {
                                item {
                                    Text("Video access is not granted.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                    Text(
                                        "Grant video access",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable {
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                        Manifest.permission.READ_MEDIA_VIDEO
                                                    } else {
                                                        Manifest.permission.READ_EXTERNAL_STORAGE
                                                    }
                                                )
                                            )
                                        }.padding(vertical = 12.dp)
                                    )
                                }
                            }
                            selectedCategory in setOf(MemoryCategory.PDF, "DOCUMENTS", MemoryCategory.PRESENTATION, MemoryCategory.SPREADSHEET) -> {
                                item {
                                    Text("No matching files are currently exposed by MediaStore or selected folders.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                    Text(
                                        "Add a folder location",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { folderPicker.launch(null) }.padding(vertical = 12.dp)
                                    )
                                }
                            }
                            !hasContentAccess() && !uiState.isIndexing -> {
                                item { Text("No accessible items. Check media access or add a folder in About.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) }
                            }
                            else -> {
                                item { Text("No ${categoryLabel(selectedCategory!!).lowercase(Locale.getDefault())} available.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) }
                            }
                        }
                    } else if (photoGrid) {
                        items(uiState.browseItems.chunked(3), key = { row -> row.joinToString { it.mediaStoreId.toString() } }) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                row.forEach { item ->
                                    BrowseThumbnail(item, Modifier.weight(1f), ::openItem)
                                }
                                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f).aspectRatio(1f)) }
                            }
                        }
                    } else {
                        items(uiState.browseItems, key = { "browse-${it.mediaStoreId}" }) { item ->
                            FossickResult(
                                type = categoryLabel(item.category),
                                title = item.displayName,
                                metadata = item.relativePath.orEmpty(),
                                date = formatDate(item.dateModified.takeIf { it > 0 } ?: item.dateAdded),
                                onClick = { openItem(item) }
                            )
                        }
                    }
                    if (uiState.browseLoading) {
                        item { Text("Loading local index…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
                    } else if (uiState.browseHasMore) {
                        item {
                            Text(
                                "Load more",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { viewModel.loadBrowse(selectedCategory!!) }.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }

            else -> AboutScreen(
                modifier = Modifier.weight(1f),
                page = aboutPage,
                onPageChange = { aboutPage = it },
                discoveredCount = uiState.discoveredCount,
                understoodCount = uiState.indexedCount,
                pendingCount = uiState.pendingCount,
                failedCount = uiState.failedCount,
                unsupportedCount = uiState.unsupportedCount,
                isIndexing = uiState.isIndexing,
                onAddLocation = { folderPicker.launch(null) },
                onManageAccess = { setupStep = 1 },
                onUpdateIndex = {
                    onStartIndexing()
                    if (hasContentAccess()) viewModel.resumeIndexing() else setupStep = 1
                }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("SEARCH", "BROWSE", "ABOUT").forEach { destination ->
                NavigationDestination(
                    destination = destination,
                    isSelected = selectedDestination == destination,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (destination == selectedDestination) return@NavigationDestination
                        if (navStack.lastOrNull() != destination) navStack.add(destination)
                        if (destination == "BROWSE") selectedCategory = null
                        if (destination == "ABOUT") aboutPage = AboutPage.HOME
                    }
                )
            }
        }
    }
}

@Composable
private fun AccessSetupScreen(
    isWelcome: Boolean,
    allFilesGranted: Boolean,
    message: String?,
    onContinue: () -> Unit,
    onRequestAccess: () -> Unit,
    onLimitedAccess: () -> Unit,
    onContinueLimited: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        FossickWordmark(modifier = Modifier.width(138.dp).height(34.dp))
        Spacer(modifier = Modifier.height(34.dp))
        if (isWelcome) {
            Text("Welcome to Fossick", color = MaterialTheme.colorScheme.onSurface, fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Find what you remember,\nnot where you saved it.", color = MaterialTheme.colorScheme.onSurface, fontSize = 23.sp, lineHeight = 30.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Fossick builds a private search index from supported files accessible on this device. Everything is processed locally.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, lineHeight = 23.sp)
            Spacer(modifier = Modifier.height(30.dp))
            SetupAction("CONTINUE", onContinue)
        } else {
            Text("Set up device access", color = MaterialTheme.colorScheme.onSurface, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    "Fossick searches supported files in shared device storage to build your private local memory index. On Android 11 and newer, broad access must be deliberately enabled in Settings > Special app access > All files access. This is not an ordinary runtime permission. Android still protects app-private locations, and Fossick does not bypass those protections."
                } else {
                    "Android 8-10 use the storage access model available on that version. Fossick can discover supported shared files where Android permits, plus media exposed by MediaStore and folders you select."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Your files, extracted text, searches, and index stay on this device. Fossick does not upload or transmit them.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 21.sp)
            message?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(26.dp))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                SetupAction(if (allFilesGranted) "CONTINUE WITH ACCESS" else "OPEN ALL FILES ACCESS SETTINGS", if (allFilesGranted) onContinueLimited else onRequestAccess)
                Spacer(modifier = Modifier.height(12.dp))
                SetupAction("USE LIMITED MEDIA ACCESS", onLimitedAccess, secondary = true)
            } else {
                SetupAction("ALLOW SHARED-STORAGE ACCESS", onRequestAccess)
            }
            Spacer(modifier = Modifier.height(12.dp))
            SetupAction("CONTINUE WITHOUT ACCESS", onContinueLimited, secondary = true)
        }
    }
}

@Composable
private fun SetupAction(label: String, onClick: () -> Unit, secondary: Boolean = false) {
    Text(
        label,
        color = if (secondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp)
    )
}

@Composable
private fun CategoryDestination(category: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier = modifier.clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(categoryLabel(category), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun BrowseThumbnail(item: ScreenshotEntity, modifier: Modifier, onClick: (ScreenshotEntity) -> Unit) {
    Column(modifier = modifier.clickable { onClick(item) }) {
        MemoryThumbnail(item)
        Spacer(modifier = Modifier.height(4.dp))
        Text(item.displayName, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatDate(item.dateModified.takeIf { it > 0 } ?: item.dateAdded), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun NavigationDestination(destination: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val symbol = when (destination) {
        "SEARCH" -> "⌕"
        "BROWSE" -> "▦"
        else -> "ⓘ"
    }
    Column(
        modifier = modifier
            .height(54.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = destination.lowercase(Locale.ROOT)
                role = Role.Tab
                selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(symbol, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, lineHeight = 20.sp)
        Text(
            destination,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private val BROWSE_CATEGORIES = listOf(
    MemoryCategory.ALL,
    MemoryCategory.SCREENSHOT,
    MemoryCategory.CAMERA,
    "IMAGES",
    MemoryCategory.VIDEO,
    MemoryCategory.PDF,
    "DOCUMENTS",
    MemoryCategory.PRESENTATION,
    MemoryCategory.SPREADSHEET
)

private fun categoryCount(category: String, state: HomeUiState): Int = when (category) {
    MemoryCategory.ALL -> state.discoveredCount
    MemoryCategory.SCREENSHOT -> state.screenshotCount
    MemoryCategory.CAMERA -> state.cameraCount
    "IMAGES" -> state.imageCount
    MemoryCategory.VIDEO -> state.videoCount
    MemoryCategory.PDF -> state.pdfCount
    "DOCUMENTS" -> state.documentCount
    MemoryCategory.PRESENTATION -> state.presentationCount
    MemoryCategory.SPREADSHEET -> state.spreadsheetCount
    else -> 0
}

private fun categoryLabel(category: String): String = when (category) {
    MemoryCategory.ALL -> "All"
    MemoryCategory.SCREENSHOT -> "Screenshots"
    MemoryCategory.CAMERA -> "Camera"
    "IMAGES" -> "Images"
    MemoryCategory.IMAGE -> "Image"
    MemoryCategory.VIDEO -> "Videos"
    MemoryCategory.PDF -> "PDFs"
    "DOCUMENTS" -> "Documents"
    MemoryCategory.DOCUMENT -> "Document"
    MemoryCategory.PRESENTATION -> "Presentations"
    MemoryCategory.SPREADSHEET -> "Spreadsheets"
    else -> category
}

private fun matchingSnippet(item: ScreenshotEntity, query: String): String {
    val normalizedQuery = query.split(Regex("\\s+")).filter { it.length > 2 }
    val text = item.ocrText.replace(Regex("\\s+"), " ").trim()
    if (text.isBlank()) return ""
    val lower = text.lowercase(Locale.ROOT)
    val match = normalizedQuery.firstNotNullOfOrNull { term -> lower.indexOf(term.lowercase(Locale.ROOT)).takeIf { it >= 0 } }
        ?: return text.take(150)
    val start = (match - 45).coerceAtLeast(0)
    val end = (match + 110).coerceAtMost(text.length)
    return buildString {
        if (start > 0) append("…")
        append(text.substring(start, end))
        if (end < text.length) append("…")
    }
}

private fun formatDate(seconds: Long): String {
    if (seconds <= 0L) return ""
    return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(seconds * 1000L))
}
