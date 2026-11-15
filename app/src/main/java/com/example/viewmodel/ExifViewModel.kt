package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ExifExtractor
import com.example.model.ExifCategory
import com.example.model.ExifPhotoReport
import com.example.model.ExifTagEntry
import com.example.model.ExifUiState
import com.example.model.InspectorTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExifViewModel(application: Application) : AndroidViewModel(application) {

    private val extractor = ExifExtractor(application.applicationContext)

    private val _uiState = MutableStateFlow<ExifUiState>(ExifUiState.Empty)
    val uiState: StateFlow<ExifUiState> = _uiState.asStateFlow()

    private val _selectedTab = MutableStateFlow(InspectorTab.OVERVIEW)
    val selectedTab: StateFlow<InspectorTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ExifCategory?>(null)
    val selectedCategory: StateFlow<ExifCategory?> = _selectedCategory.asStateFlow()

    private val _recentReports = MutableStateFlow<List<ExifPhotoReport>>(emptyList())
    val recentReports: StateFlow<List<ExifPhotoReport>> = _recentReports.asStateFlow()

    private val _darkThemeOverride = MutableStateFlow<Boolean?>(true)
    val darkThemeOverride: StateFlow<Boolean?> = _darkThemeOverride.asStateFlow()

    val filteredTags: StateFlow<List<ExifTagEntry>> = combine(
        _uiState,
        _searchQuery,
        _selectedCategory
    ) { state, query, category ->
        val report = (state as? ExifUiState.Success)?.report ?: return@combine emptyList()
        val trimmed = query.trim()
        report.rawTags.filter { entry ->
            val matchesCategory = category == null || entry.category == category
            val matchesQuery = trimmed.isEmpty() ||
                entry.displayName.contains(trimmed, ignoreCase = true) ||
                entry.tagKey.contains(trimmed, ignoreCase = true) ||
                entry.formattedValue.contains(trimmed, ignoreCase = true) ||
                entry.rawValue.contains(trimmed, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadPhoto(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ExifUiState.Loading
            extractor.extract(uri)
                .onSuccess { report ->
                    _uiState.value = ExifUiState.Success(report)
                    _searchQuery.value = ""
                    _selectedCategory.value = null
                    addToRecent(report)
                }
                .onFailure { throwable ->
                    _uiState.value = ExifUiState.Error(
                        throwable.localizedMessage ?: "사진 메타데이터를 읽지 못했습니다."
                    )
                }
        }
    }

    fun selectFromHistory(report: ExifPhotoReport) {
        _uiState.value = ExifUiState.Success(report)
        _searchQuery.value = ""
        _selectedCategory.value = null
    }

    fun selectTab(tab: InspectorTab) {
        _selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: ExifCategory?) {
        _selectedCategory.value = category
    }

    fun toggleTheme(currentlyDark: Boolean) {
        _darkThemeOverride.value = !currentlyDark
    }

    fun clearSelection() {
        _uiState.value = ExifUiState.Empty
        _searchQuery.value = ""
        _selectedCategory.value = null
        _selectedTab.value = InspectorTab.OVERVIEW
    }

    fun buildSummaryText(report: ExifPhotoReport): String = buildString {
        appendLine("[ExifLens] ${report.fileName}")
        report.cameraSummary.displayDeviceTitle?.let { appendLine("카메라: $it") }
        report.cameraSummary.displayLensTitle?.let { appendLine("렌즈: $it") }
        val exposureParts = listOfNotNull(
            report.exposureSummary.focalLengthDisplay,
            report.exposureSummary.apertureDisplay,
            report.exposureSummary.shutterSpeedDisplay,
            report.exposureSummary.isoDisplay
        )
        if (exposureParts.isNotEmpty()) {
            appendLine("노출: ${exposureParts.joinToString(" · ")}")
        }
        report.capturedDateDisplay?.let { appendLine("촬영 일시: $it") }
        val sizeParts = listOfNotNull(
            report.dimensionsDisplay,
            report.megapixelsDisplay,
            report.fileSizeDisplay
        )
        if (sizeParts.isNotEmpty()) {
            appendLine("이미지: ${sizeParts.joinToString(" · ")}")
        }
        report.locationSummary?.let { loc ->
            appendLine("위치(GPS): ${loc.decimalDisplay}")
        }
    }.trim()

    private fun addToRecent(report: ExifPhotoReport) {
        val current = _recentReports.value.filterNot { it.uriString == report.uriString }
        _recentReports.value = (listOf(report) + current).take(8)
    }
}
