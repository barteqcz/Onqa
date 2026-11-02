/**
 * Combined presentation state for the main radio screen.
 */
package com.barteqcz.onqa.ui.main

import androidx.compose.runtime.Immutable
import com.barteqcz.onqa.data.model.AppLanguage
import com.barteqcz.onqa.data.model.AppSettings
import com.barteqcz.onqa.data.model.LocationInfo
import com.barteqcz.onqa.data.model.RadioStation
import com.barteqcz.onqa.data.model.ThemeMode
import com.barteqcz.onqa.ui.theme.OnqaGreen

@Immutable
data class RadioViewState(
    val uiState: RadioUiState = RadioUiState.Loading,
    val selectedUrl: String? = null,
    val currentStation: RadioStation? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackError: Boolean = false,
    val locationInfo: LocationInfo = LocationInfo(),
    val settings: AppSettings = AppSettings(
        themeMode = ThemeMode.DARK,
        isMaterialYouEnabled = false,
        accentColor = OnqaGreen,
        useHqStream = true,
        favoriteStations = kotlinx.collections.immutable.persistentSetOf(),
        language = AppLanguage.SYSTEM,
    ),
    val isNetworkAvailable: Boolean = true,
    val isScrollable: Boolean = false,
    val metadata: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val displayStation: RadioStation? = null,
    val isMiniPlayerActive: Boolean = false,
    val hasPendingCatalogUpdate: Boolean = false,
)
