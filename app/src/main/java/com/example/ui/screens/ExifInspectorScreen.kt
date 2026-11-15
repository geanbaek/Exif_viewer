package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.model.ExifCategory
import com.example.model.ExifPhotoReport
import com.example.model.ExifTagEntry
import com.example.model.ExifUiState
import com.example.model.InspectorTab
import com.example.ui.components.ExifSectionCard
import com.example.ui.components.ExposureTelemetryHud
import com.example.ui.components.LocationGpsCard
import com.example.ui.components.NoExifNoticeBanner
import com.example.ui.components.RecentPhotosStrip
import com.example.ui.components.ViewfinderPhotoCard
import com.example.ui.theme.ExifMonoValueStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExifInspectorScreen(
    uiState: ExifUiState,
    selectedTab: InspectorTab,
    searchQuery: String,
    selectedCategory: ExifCategory?,
    filteredTags: List<ExifTagEntry>,
    recentReports: List<ExifPhotoReport>,
    isDarkTheme: Boolean,
    onPickFromGallery: () -> Unit,
    onPickFromDocument: () -> Unit,
    onSelectTab: (InspectorTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (ExifCategory?) -> Unit,
    onSelectHistoryReport: (ExifPhotoReport) -> Unit,
    onToggleTheme: () -> Unit,
    onClearSelection: () -> Unit,
    buildSummaryText: (ExifPhotoReport) -> String
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var fullscreenPhoto by remember { mutableStateOf<ExifPhotoReport?>(null) }

    // Support system back gesture from inspected photo/error back to Empty landing screen
    BackHandler(enabled = uiState is ExifUiState.Success || uiState is ExifUiState.Error) {
        onClearSelection()
    }

    val copyToClipboard: (String, String) -> Unit = { label, text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        scope.launch {
            snackbarHostState.showSnackbar("$label 복사됨")
        }
    }

    val shareSummary: (ExifPhotoReport) -> Unit = { report ->
        val text = buildSummaryText(report)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(sendIntent, "EXIF 정보 공유"))
    }

    val openInMaps: (Double, Double, String) -> Unit = { lat, lon, label ->
        val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
        try {
            context.startActivity(mapIntent)
        } catch (_: ActivityNotFoundException) {
            copyToClipboard("GPS 좌표", "$lat, $lon")
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.map_not_available))
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    if (uiState is ExifUiState.Success || uiState is ExifUiState.Error) {
                        IconButton(
                            onClick = onClearSelection,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("back_to_empty_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "처음 화면으로"
                            )
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = stringResource(R.string.cd_app_logo),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                            Text(
                                text = stringResource(R.string.tagline),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    if (uiState is ExifUiState.Success) {
                        IconButton(
                            onClick = {
                                copyToClipboard("EXIF 요약", buildSummaryText(uiState.report))
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("copy_summary_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.btn_copy_summary)
                            )
                        }
                        IconButton(
                            onClick = { shareSummary(uiState.report) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("share_summary_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.btn_share_summary)
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = stringResource(R.string.cd_toggle_theme)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            when (uiState) {
                is ExifUiState.Empty -> {
                    EmptyGalleryScreen(
                        onPickFromGallery = onPickFromGallery,
                        onPickFromDocument = onPickFromDocument
                    )
                }

                is ExifUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "EXIF 메타데이터 분석 중...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is ExifUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "사진을 불러오지 못했습니다",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onPickFromGallery,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(R.string.btn_pick_gallery))
                                }
                            }
                        }
                    }
                }

                is ExifUiState.Success -> {
                    val report = uiState.report
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isExpanded = maxWidth >= 840.dp
                        if (isExpanded) {
                            ExpandedTwoPaneInspector(
                                report = report,
                                selectedTab = selectedTab,
                                searchQuery = searchQuery,
                                selectedCategory = selectedCategory,
                                filteredTags = filteredTags,
                                recentReports = recentReports,
                                onZoomClick = { fullscreenPhoto = report },
                                onPickFromGallery = onPickFromGallery,
                                onPickFromDocument = onPickFromDocument,
                                onSelectTab = onSelectTab,
                                onSearchQueryChange = onSearchQueryChange,
                                onSelectCategory = onSelectCategory,
                                onSelectHistoryReport = onSelectHistoryReport,
                                onCopyValue = copyToClipboard,
                                onOpenMap = {
                                    report.locationSummary?.let { loc ->
                                        openInMaps(loc.latitude, loc.longitude, report.fileName)
                                    }
                                }
                            )
                        } else {
                            CompactSinglePaneInspector(
                                report = report,
                                selectedTab = selectedTab,
                                searchQuery = searchQuery,
                                selectedCategory = selectedCategory,
                                filteredTags = filteredTags,
                                recentReports = recentReports,
                                onZoomClick = { fullscreenPhoto = report },
                                onPickFromGallery = onPickFromGallery,
                                onPickFromDocument = onPickFromDocument,
                                onSelectTab = onSelectTab,
                                onSearchQueryChange = onSearchQueryChange,
                                onSelectCategory = onSelectCategory,
                                onSelectHistoryReport = onSelectHistoryReport,
                                onCopyValue = copyToClipboard,
                                onOpenMap = {
                                    report.locationSummary?.let { loc ->
                                        openInMaps(loc.latitude, loc.longitude, report.fileName)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    fullscreenPhoto?.let { report ->
        FullscreenPhotoDialog(
            report = report,
            onDismiss = { fullscreenPhoto = null }
        )
    }
}

@Composable
private fun CompactSinglePaneInspector(
    report: ExifPhotoReport,
    selectedTab: InspectorTab,
    searchQuery: String,
    selectedCategory: ExifCategory?,
    filteredTags: List<ExifTagEntry>,
    recentReports: List<ExifPhotoReport>,
    onZoomClick: () -> Unit,
    onPickFromGallery: () -> Unit,
    onPickFromDocument: () -> Unit,
    onSelectTab: (InspectorTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (ExifCategory?) -> Unit,
    onSelectHistoryReport: (ExifPhotoReport) -> Unit,
    onCopyValue: (String, String) -> Unit,
    onOpenMap: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ViewfinderPhotoCard(
                report = report,
                onZoomClick = onZoomClick,
                onPickGalleryClick = onPickFromGallery,
                onPickDocumentClick = onPickFromDocument
            )
        }

        item {
            ExposureTelemetryHud(report = report)
        }

        if (recentReports.size > 1) {
            item {
                RecentPhotosStrip(
                    recentReports = recentReports,
                    currentUriString = report.uriString,
                    onSelectReport = onSelectHistoryReport
                )
            }
        }

        item {
            InspectorTabSelector(
                selectedTab = selectedTab,
                rawTagCount = report.rawTags.size,
                onSelectTab = onSelectTab
            )
        }

        if (selectedTab == InspectorTab.OVERVIEW) {
            if (!report.hasExifData) {
                item {
                    NoExifNoticeBanner(onOpenDocumentClick = onPickFromDocument)
                }
            }
            item {
                OverviewCardsColumn(
                    report = report,
                    onPickFromDocument = onPickFromDocument,
                    onCopyValue = onCopyValue,
                    onOpenMap = onOpenMap
                )
            }
        } else {
            item {
                TagFilterControls(
                    searchQuery = searchQuery,
                    selectedCategory = selectedCategory,
                    onSearchQueryChange = onSearchQueryChange,
                    onSelectCategory = onSelectCategory
                )
            }
            if (filteredTags.isEmpty()) {
                item {
                    EmptyTagSearchState(hasAnyTags = report.rawTags.isNotEmpty())
                }
            } else {
                items(filteredTags, key = { it.tagKey }) { tag ->
                    RawTagRowCard(
                        entry = tag,
                        onCopy = { onCopyValue(tag.displayName, tag.formattedValue) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedTwoPaneInspector(
    report: ExifPhotoReport,
    selectedTab: InspectorTab,
    searchQuery: String,
    selectedCategory: ExifCategory?,
    filteredTags: List<ExifTagEntry>,
    recentReports: List<ExifPhotoReport>,
    onZoomClick: () -> Unit,
    onPickFromGallery: () -> Unit,
    onPickFromDocument: () -> Unit,
    onSelectTab: (InspectorTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (ExifCategory?) -> Unit,
    onSelectHistoryReport: (ExifPhotoReport) -> Unit,
    onCopyValue: (String, String) -> Unit,
    onOpenMap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Left pane: Photo + Telemetry HUD + History
        LazyColumn(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                ViewfinderPhotoCard(
                    report = report,
                    onZoomClick = onZoomClick,
                    onPickGalleryClick = onPickFromGallery,
                    onPickDocumentClick = onPickFromDocument
                )
            }
            item {
                ExposureTelemetryHud(report = report)
            }
            if (recentReports.size > 1) {
                item {
                    RecentPhotosStrip(
                        recentReports = recentReports,
                        currentUriString = report.uriString,
                        onSelectReport = onSelectHistoryReport
                    )
                }
            }
        }

        // Right pane: Tabbed EXIF details
        LazyColumn(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                InspectorTabSelector(
                    selectedTab = selectedTab,
                    rawTagCount = report.rawTags.size,
                    onSelectTab = onSelectTab
                )
            }
            if (selectedTab == InspectorTab.OVERVIEW) {
                if (!report.hasExifData) {
                    item {
                        NoExifNoticeBanner(onOpenDocumentClick = onPickFromDocument)
                    }
                }
                item {
                    OverviewCardsColumn(
                        report = report,
                        onPickFromDocument = onPickFromDocument,
                        onCopyValue = onCopyValue,
                        onOpenMap = onOpenMap
                    )
                }
            } else {
                item {
                    TagFilterControls(
                        searchQuery = searchQuery,
                        selectedCategory = selectedCategory,
                        onSearchQueryChange = onSearchQueryChange,
                        onSelectCategory = onSelectCategory
                    )
                }
                if (filteredTags.isEmpty()) {
                    item {
                        EmptyTagSearchState(hasAnyTags = report.rawTags.isNotEmpty())
                    }
                } else {
                    items(filteredTags, key = { it.tagKey }) { tag ->
                        RawTagRowCard(
                            entry = tag,
                            onCopy = { onCopyValue(tag.displayName, tag.formattedValue) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorTabSelector(
    selectedTab: InspectorTab,
    rawTagCount: Int,
    onSelectTab: (InspectorTab) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {}
        ) {
            InspectorTab.entries.forEach { tab ->
                val title = if (tab == InspectorTab.ALL_TAGS) {
                    "${tab.labelKo} ($rawTagCount)"
                } else {
                    tab.labelKo
                }
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.testTag(
                        if (tab == InspectorTab.OVERVIEW) "tab_overview" else "tab_all_tags"
                    ),
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun OverviewCardsColumn(
    report: ExifPhotoReport,
    onPickFromDocument: () -> Unit,
    onCopyValue: (String, String) -> Unit,
    onOpenMap: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Camera & Lens
        val cam = report.cameraSummary
        val cameraRows = buildList {
            cam.displayDeviceTitle?.let { add("카메라" to it) }
            cam.displayLensTitle?.let { add("렌즈" to it) }
            cam.software?.let { add("소프트웨어" to it) }
            cam.artistOrOwner?.let { add("촬영자" to it) }
            cam.copyright?.let { add("저작권" to it) }
        }
        ExifSectionCard(
            title = "카메라 · 렌즈",
            icon = Icons.Outlined.CameraAlt,
            rows = cameraRows,
            onCopyValue = onCopyValue,
            accentColor = MaterialTheme.colorScheme.primary
        )

        // 2. Exposure & Optics Details
        val exp = report.exposureSummary
        val exposureRows = buildList {
            exp.focalLengthDisplay?.let { add("초점 거리" to it) }
            exp.focalLength35mmDisplay?.let { add("35mm 환산" to it) }
            exp.apertureDisplay?.let { add("조리개" to it) }
            exp.shutterSpeedDisplay?.let { add("셔터 속도" to it) }
            exp.isoDisplay?.let { add("ISO 감도" to it) }
            exp.exposureBiasDisplay?.let { add("노출 보정" to it) }
            exp.exposureProgram?.let { add("노출 프로그램" to it) }
            exp.exposureMode?.let { add("노출 모드" to it) }
            exp.meteringMode?.let { add("측광 방식" to it) }
            exp.whiteBalance?.let { add("화이트 밸런스" to it) }
            exp.flash?.let { add("플래시" to it) }
            exp.lightSource?.let { add("광원" to it) }
            exp.sceneCaptureType?.let { add("장면 모드" to it) }
        }
        ExifSectionCard(
            title = "노출 · 광학 상세",
            icon = Icons.Outlined.Tune,
            rows = exposureRows,
            onCopyValue = onCopyValue,
            accentColor = MaterialTheme.colorScheme.secondary
        )

        // 3. Date & Time
        val dateRows = buildList {
            report.capturedDateDisplay?.let { add("촬영 일시 (원본)" to it) }
            report.digitizedDateDisplay?.let { add("디지털화 일시" to it) }
            report.modifiedDateDisplay?.let { add("파일 수정 일시" to it) }
        }
        ExifSectionCard(
            title = "촬영 일시",
            icon = Icons.Outlined.Event,
            rows = dateRows,
            onCopyValue = onCopyValue,
            accentColor = MaterialTheme.colorScheme.primary
        )

        // 4. Location GPS
        report.locationSummary?.let { loc ->
            LocationGpsCard(
                location = loc,
                onCopyCoords = { onCopyValue("GPS 좌표", loc.decimalDisplay) },
                onOpenMap = onOpenMap
            )
        }

        // 5. Image & File Technicals (Always present!)
        val tech = report.technicalSummary
        val fileRows = buildList {
            add("파일명" to report.fileName)
            add("포맷" to report.formatLabel)
            report.dimensionsDisplay?.let { add("해상도" to it) }
            report.megapixelsDisplay?.let { add("화소 수" to it) }
            report.aspectRatioDisplay?.let { add("화면 비율" to it) }
            report.fileSizeDisplay?.let { add("파일 크기" to it) }
            tech.orientationDisplay?.let { add("회전 방향" to it) }
            tech.colorSpaceDisplay?.let { add("색 공간" to it) }
            tech.resolutionDpiDisplay?.let { add("인쇄 해상도" to it) }
            tech.compressionDisplay?.let { add("압축 방식" to it) }
            tech.bitsPerSampleDisplay?.let { add("비트 심도" to it) }
            tech.sensingMethodDisplay?.let { add("센서 유형" to it) }
            tech.customRenderedDisplay?.let { add("렌더링 처리" to it) }
        }
        ExifSectionCard(
            title = "이미지 · 파일 정보",
            icon = Icons.Outlined.Image,
            rows = fileRows,
            onCopyValue = onCopyValue,
            accentColor = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun TagFilterControls(
    searchQuery: String,
    selectedCategory: ExifCategory?,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (ExifCategory?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tag_search_input"),
            placeholder = {
                Text(
                    text = stringResource(R.string.search_tags_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = "검색어 지우기")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("전체") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
            items(ExifCategory.entries) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = {
                        onSelectCategory(if (selectedCategory == category) null else category)
                    },
                    label = { Text(category.labelKo) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun RawTagRowCard(
    entry: ExifTagEntry,
    onCopy: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onCopy),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(0.48f)) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.tagKey,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                modifier = Modifier.weight(0.52f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.formattedValue,
                    style = ExifMonoValueStyle,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "${entry.displayName} 복사",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyTagSearchState(hasAnyTags: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (hasAnyTags) "일치하는 EXIF 태그가 없습니다" else "표시할 EXIF 태그가 없습니다",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hasAnyTags) "다른 검색어나 카테고리를 선택해 보세요." else "메타데이터가 제거된 이미지일 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FullscreenPhotoDialog(
    report: ExifPhotoReport,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = nextScale
                        offset = if (nextScale > 1f) {
                            offset + pan
                        } else {
                            Offset.Zero
                        }
                    }
                }
        ) {
            AsyncImage(
                model = Uri.parse(report.uriString),
                contentDescription = stringResource(R.string.cd_selected_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )

            // Top close bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = report.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.72f)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.cd_close_fullscreen),
                            tint = Color.White
                        )
                    }
                }
            }

            // Bottom telemetry overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                color = Color.Black.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                val exp = report.exposureSummary
                val hudParts = listOfNotNull(
                    report.cameraSummary.displayDeviceTitle,
                    exp.focalLength35mmDisplay ?: exp.focalLengthDisplay,
                    exp.apertureDisplay,
                    exp.shutterSpeedDisplay,
                    exp.isoDisplay
                )
                Text(
                    text = if (hudParts.isNotEmpty()) {
                        hudParts.joinToString("  ·  ")
                    } else {
                        report.dimensionsDisplay ?: "확대/드래그하여 자세히 보기"
                    },
                    style = ExifMonoValueStyle,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }
}
