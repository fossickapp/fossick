package com.fossick.app.ui.screens

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fossick.app.data.local.FossickDatabase
import com.fossick.app.data.local.OcrStatus
import com.fossick.app.data.local.ScreenshotEntity
import com.fossick.app.data.model.MemoryCategory
import com.fossick.app.data.repository.ScreenshotRepository
import com.fossick.app.ocr.ScreenshotOcrProcessor
import com.fossick.app.ocr.UnsupportedContentException
import com.fossick.app.search.FossickSearchEngine
import com.fossick.app.search.StructuredEntityExtractor
import com.fossick.app.search.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val discoveredCount: Int = 0,
    val screenshotCount: Int = 0,
    val cameraCount: Int = 0,
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    val documentCount: Int = 0,
    val pdfCount: Int = 0,
    val presentationCount: Int = 0,
    val spreadsheetCount: Int = 0,
    val indexedCount: Int = 0,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val unsupportedCount: Int = 0,
    val isIndexing: Boolean = false,
    val isSearching: Boolean = false,
    val results: List<ScreenshotEntity> = emptyList(),
    val browseItems: List<ScreenshotEntity> = emptyList(),
    val browseLoading: Boolean = false,
    val browseHasMore: Boolean = false,
    val errorMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = FossickDatabase.get(application)
    private val dao = database.screenshotDao()
    private val mediaStoreRepository = ScreenshotRepository(application)
    private val searchEngine = FossickSearchEngine(dao)
    private val ocrProcessor = ScreenshotOcrProcessor(application.contentResolver)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var indexingJob: Job? = null
    private var searchJob: Job? = null
    private var searchGeneration = 0
    private lateinit var recoveryJob: Job
    private var browseJob: Job? = null
    private var browseOffset = 0
    private var browseCategory = MemoryCategory.ALL

    companion object {
        private const val BROWSE_PAGE_SIZE = 80
        private const val COUNT_REFRESH_INTERVAL = 64
        private const val DIAGNOSTIC_TAG = "FossickDiscovery"
    }

    init {
        recoveryJob = viewModelScope.launch(Dispatchers.IO) {
            dao.resetProcessing()
            refreshCounts()
        }
        loadBrowse(MemoryCategory.ALL, reset = true)
    }

    fun resumeIndexing() {
        if (indexingJob?.isActive == true) return
        indexingJob = viewModelScope.launch(Dispatchers.IO) {
            recoveryJob.join()
            _uiState.value = _uiState.value.copy(isIndexing = true, errorMessage = null)
            try {
                mediaStoreRepository.synchronize(dao)
                refreshCounts()
                processPending()
            } catch (_: SecurityException) {
                _uiState.value = _uiState.value.copy(errorMessage = "PHOTO ACCESS IS NEEDED TO FIND SCREENSHOTS.")
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "MEMORY COULD NOT BE UPDATED.")
            } finally {
                refreshCounts()
                if (getApplication<Application>().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    Log.i(DIAGNOSTIC_TAG, "post-index category/source/status counts=${dao.getIndexAggregateCounts()}")
                }
                _uiState.value = _uiState.value.copy(isIndexing = false)
            }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        val generation = ++searchGeneration
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isSearching = false)
            return
        }
        _uiState.value = _uiState.value.copy(results = emptyList(), isSearching = true)
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val results = searchEngine.search(query)
            if (generation == searchGeneration) {
                _uiState.value = _uiState.value.copy(results = results, isSearching = false)
            }
        }
    }

    fun addFolderLocation(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            mediaStoreRepository.rememberFolder(uri)
            resumeIndexing()
        }
    }

    fun loadBrowse(category: String, reset: Boolean = false) {
        if (!reset && (_uiState.value.browseLoading || !_uiState.value.browseHasMore)) return
        if (reset) {
            browseJob?.cancel()
            browseCategory = category
            browseOffset = 0
            _uiState.value = _uiState.value.copy(
                browseItems = emptyList(),
                browseHasMore = false,
                browseLoading = true
            )
        } else if (category != browseCategory) {
            return
        } else {
            _uiState.value = _uiState.value.copy(browseLoading = true)
        }

        val currentOffset = browseOffset
        browseJob = viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    dao.getPage(browseCategory, BROWSE_PAGE_SIZE, currentOffset)
                }
                if (browseCategory == category) {
                    browseOffset += page.size
                    _uiState.value = _uiState.value.copy(
                        browseItems = if (reset) page else _uiState.value.browseItems + page,
                        browseHasMore = page.size == BROWSE_PAGE_SIZE,
                        browseLoading = false
                    )
                }
            } catch (_: Exception) {
                if (browseCategory == category) {
                    _uiState.value = _uiState.value.copy(browseLoading = false)
                }
            }
        }
    }

    private suspend fun processPending() {
        var processedSinceRefresh = 0
        while (true) {
            val next = dao.getPending(1).firstOrNull() ?: break
            try {
                dao.markProcessing(next.mediaStoreId)
                val text = withContext(Dispatchers.IO) { ocrProcessor.extractText(next) }
                dao.updateOcr(
                    mediaStoreId = next.mediaStoreId,
                    status = OcrStatus.INDEXED,
                    ocrText = text,
                    normalizedText = TextNormalizer.normalize(text),
                    structuredEntities = StructuredEntityExtractor.extract(text),
                    indexedModifiedTime = next.dateModified,
                    indexedAt = System.currentTimeMillis(),
                    errorState = null
                )
            } catch (_: UnsupportedContentException) {
                dao.updateOcr(
                    mediaStoreId = next.mediaStoreId,
                    status = OcrStatus.UNSUPPORTED,
                    ocrText = "",
                    normalizedText = "",
                    structuredEntities = "",
                    indexedModifiedTime = next.dateModified,
                    indexedAt = System.currentTimeMillis(),
                    errorState = "CONTENT_UNSUPPORTED"
                )
            } catch (_: Exception) {
                dao.updateOcr(
                    mediaStoreId = next.mediaStoreId,
                    status = OcrStatus.FAILED,
                    ocrText = "",
                    normalizedText = "",
                    structuredEntities = "",
                    indexedModifiedTime = next.dateModified,
                    indexedAt = System.currentTimeMillis(),
                    errorState = "OCR_FAILED"
                )
            }
            processedSinceRefresh++
            if (processedSinceRefresh >= COUNT_REFRESH_INTERVAL) {
                refreshCounts()
                processedSinceRefresh = 0
            }
        }
    }

    private suspend fun refreshCounts() {
        _uiState.value = _uiState.value.copy(
            discoveredCount = dao.countAll(),
            screenshotCount = dao.countCategory("SCREENSHOT"),
            cameraCount = dao.countCategory("CAMERA"),
            imageCount = dao.countCategory("IMAGE"),
            videoCount = dao.countCategory("VIDEO"),
            documentCount = dao.countCategory("DOCUMENT"),
            pdfCount = dao.countCategory("PDF"),
            presentationCount = dao.countCategory("PRESENTATION"),
            spreadsheetCount = dao.countCategory("SPREADSHEET"),
            indexedCount = dao.countIndexed(),
            pendingCount = dao.countPending(),
            failedCount = dao.countFailed(),
            unsupportedCount = dao.countUnsupported()
        )
    }
}
