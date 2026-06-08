package com.barteqcz.onqa.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.barteqcz.onqa.ui.theme.OnqaGreen
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf

@Immutable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val isMaterialYouEnabled: Boolean = false,
    val accentColor: Color = OnqaGreen,
    val lastCity: String? = null,
    val lastCountryCode: String? = null,
    val isOnboardingCompleted: Boolean = false,
    val favoriteStations: PersistentSet<String> = persistentSetOf(),
    val useHqStream: Boolean = true,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val isInitialValue: Boolean = true
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}
