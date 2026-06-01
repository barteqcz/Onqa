package com.barteqcz.onqa.data.model

import androidx.compose.ui.graphics.Color
import com.barteqcz.onqa.ui.theme.OnqaGreen

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isMaterialYouEnabled: Boolean = false,
    val accentColor: Color = OnqaGreen,
    val lastCity: String? = null,
    val lastCountryCode: String? = null,
    val isOnboardingCompleted: Boolean = false,
    val favoriteStations: Set<String> = emptySet(),
    val useHqStream: Boolean = false,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val isInitialValue: Boolean = true
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}
