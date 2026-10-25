package com.barteqcz.onqa.data.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.barteqcz.onqa.R
import com.barteqcz.onqa.ui.theme.OnqaGreen
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.serialization.Serializable

@Serializable
data class FavoritesBackup(
    val favoriteStations: List<String>,
)

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
    val showLocationHeader: Boolean = true,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val isAmoledModeEnabled: Boolean = false,
    val locationSource: LocationSource = LocationSource.GPS,
    val manualLatitude: Double? = null,
    val manualLongitude: Double? = null,
    val manualCity: String? = null,
    val manualCountryCode: String? = null,
    val catalogUpdateFrequency: CatalogUpdateFrequency = CatalogUpdateFrequency.WEEKLY,
    val lastCatalogUpdate: Long = 0L,
    val isInitialValue: Boolean = true,
)

enum class CatalogUpdateFrequency(val days: Int, @StringRes val labelRes: Int) {
    MANUAL(0, R.string.update_freq_manual),
    DAILY(1, R.string.update_freq_daily),
    WEEKLY(7, R.string.update_freq_weekly),
    MONTHLY(30, R.string.update_freq_monthly),
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class LocationSource {
    GPS, MANUAL
}

enum class DistanceUnit {
    KILOMETERS, MILES
}

enum class AppLanguage(val code: String, @StringRes val labelRes: Int) {
    SYSTEM("", R.string.language_system),
    BELARUSIAN("be", R.string.language_belarusian),
    BOSNIAN("bs", R.string.language_bosnian),
    BULGARIAN("bg", R.string.language_bulgarian),
    CZECH("cs", R.string.language_czech),
    DANISH("da", R.string.language_danish),
    GERMAN("de", R.string.language_german),
    ESTONIAN("et", R.string.language_estonian),
    GREEK("el", R.string.language_greek),
    ENGLISH("en", R.string.language_english),
    SPANISH("es", R.string.language_spanish),
    FRENCH("fr", R.string.language_french),
    IRISH("ga", R.string.language_irish),
    CROATIAN("hr", R.string.language_croatian),
    ICELANDIC("is", R.string.language_icelandic),
    ITALIAN("it", R.string.language_italian),
    LATVIAN("lv", R.string.language_latvian),
    LUXEMBOURGISH("lb", R.string.language_luxembourgish),
    LITHUANIAN("lt", R.string.language_lithuanian),
    HUNGARIAN("hu", R.string.language_hungarian),
    MACEDONIAN("mk", R.string.language_macedonian),
    MALTESE("mt", R.string.language_maltese),
    DUTCH("nl", R.string.language_dutch),
    NORWEGIAN("nb", R.string.language_norwegian),
    POLISH("pl", R.string.language_polish),
    PORTUGUESE("pt", R.string.language_portuguese),
    ROMANIAN("ro", R.string.language_romanian),
    ROMANSH("rm", R.string.language_romansh),
    ALBANIAN("sq", R.string.language_albanian),
    SLOVAK("sk", R.string.language_slovak),
    SLOVENIAN("sl", R.string.language_slovenian),
    SERBIAN("sr", R.string.language_serbian),
    FINNISH("fi", R.string.language_finnish),
    SWEDISH("sv", R.string.language_swedish),
    TURKISH("tr", R.string.language_turkish),
    UKRAINIAN("uk", R.string.language_ukrainian),
}
