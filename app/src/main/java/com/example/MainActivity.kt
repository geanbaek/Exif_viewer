package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.ExifInspectorScreen
import com.example.ui.theme.ExifLensTheme
import com.example.viewmodel.ExifViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ExifViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        setContent {
            val darkThemeOverride by viewModel.darkThemeOverride.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = darkThemeOverride ?: systemDark

            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
            val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
            val filteredTags by viewModel.filteredTags.collectAsStateWithLifecycle()
            val recentReports by viewModel.recentReports.collectAsStateWithLifecycle()

            val photoPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia()
            ) { uri: Uri? ->
                uri?.let { viewModel.loadPhoto(it) }
            }

            val documentPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                uri?.let { viewModel.loadPhoto(it) }
            }

            ExifLensTheme(darkTheme = isDarkTheme) {
                ExifInspectorScreen(
                    uiState = uiState,
                    selectedTab = selectedTab,
                    searchQuery = searchQuery,
                    selectedCategory = selectedCategory,
                    filteredTags = filteredTags,
                    recentReports = recentReports,
                    isDarkTheme = isDarkTheme,
                    onPickFromGallery = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickFromDocument = {
                        documentPickerLauncher.launch(arrayOf("image/*"))
                    },
                    onSelectTab = viewModel::selectTab,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onSelectCategory = viewModel::selectCategory,
                    onSelectHistoryReport = viewModel::selectFromHistory,
                    onToggleTheme = { viewModel.toggleTheme(isDarkTheme) },
                    onClearSelection = viewModel::clearSelection,
                    buildSummaryText = viewModel::buildSummaryText
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val incomingUri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }
        incomingUri?.let { viewModel.loadPhoto(it) }
    }
}
