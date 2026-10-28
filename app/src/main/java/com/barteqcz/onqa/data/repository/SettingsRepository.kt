/**
 * Application settings repository managing persistent storage and retrieval using Jetpack DataStore.
 */
package com.barteqcz.onqa.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.barteqcz.onqa.data.model.AppLanguage
import com.barteqcz.onqa.data.model.AppSettings
import com.barteqcz.onqa.data.model.CatalogUpdateFrequency
import com.barteqcz.onqa.data.model.DistanceUnit
import com.barteqcz.onqa.data.model.LocationSource
import com.barteqcz.onqa.data.model.ThemeMode
import com.barteqcz.onqa.ui.theme.OnqaGreen
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
open class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val MATERIAL_YOU = booleanPreferencesKey("material_you")
        val ACCENT_COLOR = intPreferencesKey("accent_color")
        val LAST_CITY = stringPreferencesKey("last_city")
        val LAST_COUNTRY_CODE = stringPreferencesKey("last_country_code")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val FAVORITE_STATIONS = stringSetPreferencesKey("favorite_stations")
        val USE_HQ_STREAM = booleanPreferencesKey("use_hq_stream")
        val SHOW_LOCATION_HEADER = booleanPreferencesKey("show_location_header")
        val LAST_LATITUDE = doublePreferencesKey("last_latitude")
        val LAST_LONGITUDE = doublePreferencesKey("last_longitude")
        val AMOLED_MODE = booleanPreferencesKey("amoled_mode")
        val LOCATION_SOURCE = stringPreferencesKey("location_source")
        val MANUAL_LATITUDE = doublePreferencesKey("manual_latitude")
        val MANUAL_LONGITUDE = doublePreferencesKey("manual_longitude")
        val MANUAL_CITY = stringPreferencesKey("manual_city")
        val MANUAL_COUNTRY_CODE = stringPreferencesKey("manual_country_code")
        val LANGUAGE = stringPreferencesKey("language")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val CATALOG_UPDATE_FREQUENCY = stringPreferencesKey("catalog_update_frequency")
        val LAST_CATALOG_UPDATE = longPreferencesKey("last_catalog_update")
    }

    open val settingsFlow: Flow<AppSettings> by lazy {
        context.dataStore.data
            .map { preferences ->
                val savedLanguageStr = preferences[PreferencesKeys.LANGUAGE]
                val language = savedLanguageStr?.let { str ->
                    try {
                        AppLanguage.valueOf(str)
                    } catch (_: IllegalArgumentException) {
                        AppLanguage.entries.find { it.code.equals(str, ignoreCase = true) } ?: AppLanguage.SYSTEM
                    }
                } ?: AppLanguage.SYSTEM

                val savedUnitStr = preferences[PreferencesKeys.DISTANCE_UNIT]
                val distanceUnit = savedUnitStr?.let { str ->
                    try {
                        DistanceUnit.valueOf(str)
                    } catch (_: IllegalArgumentException) {
                        DistanceUnit.KILOMETERS
                    }
                } ?: DistanceUnit.KILOMETERS

                val savedFreqStr = preferences[PreferencesKeys.CATALOG_UPDATE_FREQUENCY]
                val catalogUpdateFrequency = savedFreqStr?.let { str ->
                    try {
                        CatalogUpdateFrequency.valueOf(str)
                    } catch (_: IllegalArgumentException) {
                        CatalogUpdateFrequency.WEEKLY
                    }
                } ?: CatalogUpdateFrequency.WEEKLY

                AppSettings(
                    themeMode = ThemeMode.valueOf(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DARK.name),
                    isMaterialYouEnabled = preferences[PreferencesKeys.MATERIAL_YOU] ?: false,
                    accentColor = Color(preferences[PreferencesKeys.ACCENT_COLOR] ?: OnqaGreen.toArgb()),
                    lastCity = preferences[PreferencesKeys.LAST_CITY],
                    lastCountryCode = preferences[PreferencesKeys.LAST_COUNTRY_CODE],
                    isOnboardingCompleted = preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false,
                    favoriteStations = preferences[PreferencesKeys.FAVORITE_STATIONS]?.toPersistentSet() ?: kotlinx.collections.immutable.persistentSetOf(),
                    useHqStream = preferences[PreferencesKeys.USE_HQ_STREAM] ?: true,
                    showLocationHeader = preferences[PreferencesKeys.SHOW_LOCATION_HEADER] ?: true,
                    lastLatitude = preferences[PreferencesKeys.LAST_LATITUDE],
                    lastLongitude = preferences[PreferencesKeys.LAST_LONGITUDE],
                    language = language,
                    distanceUnit = distanceUnit,
                    isAmoledModeEnabled = preferences[PreferencesKeys.AMOLED_MODE] ?: false,
                    locationSource = LocationSource.valueOf(preferences[PreferencesKeys.LOCATION_SOURCE] ?: LocationSource.GPS.name),
                    manualLatitude = preferences[PreferencesKeys.MANUAL_LATITUDE],
                    manualLongitude = preferences[PreferencesKeys.MANUAL_LONGITUDE],
                    manualCity = preferences[PreferencesKeys.MANUAL_CITY],
                    manualCountryCode = preferences[PreferencesKeys.MANUAL_COUNTRY_CODE],
                    catalogUpdateFrequency = catalogUpdateFrequency,
                    lastCatalogUpdate = preferences[PreferencesKeys.LAST_CATALOG_UPDATE] ?: 0L,
                    isInitialValue = false
                )
            }
    }

    suspend fun updateCatalogUpdateFrequency(frequency: CatalogUpdateFrequency) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CATALOG_UPDATE_FREQUENCY] = frequency.name
        }
    }

    suspend fun updateLastCatalogUpdate(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_CATALOG_UPDATE] = timestamp
        }
    }

    suspend fun updateUseHqStream(useHq: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_HQ_STREAM] = useHq
        }
    }

    suspend fun updateShowLocationHeader(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_LOCATION_HEADER] = enabled
        }
    }

    suspend fun updateOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun updateMaterialYou(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MATERIAL_YOU] = enabled
        }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun updateLanguage(language: AppLanguage) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE] = language.name
        }
    }

    suspend fun updateDistanceUnit(unit: DistanceUnit) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISTANCE_UNIT] = unit.name
        }
    }

    suspend fun updateAccentColor(color: Color) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCENT_COLOR] = color.toArgb()
        }
    }

    suspend fun updateAmoledMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AMOLED_MODE] = enabled
        }
    }

    suspend fun updateLocationSource(source: LocationSource) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOCATION_SOURCE] = source.name
        }
    }

    suspend fun updateManualLocation(city: String?, code: String?, latitude: Double, longitude: Double) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MANUAL_CITY] = city ?: ""
            preferences[PreferencesKeys.MANUAL_COUNTRY_CODE] = code ?: ""
            preferences[PreferencesKeys.MANUAL_LATITUDE] = latitude
            preferences[PreferencesKeys.MANUAL_LONGITUDE] = longitude
        }
    }

    suspend fun updateLastLocation(city: String?, code: String?, latitude: Double? = null, longitude: Double? = null) {
        context.dataStore.edit { preferences ->
            city?.let { preferences[PreferencesKeys.LAST_CITY] = it }
            code?.let { preferences[PreferencesKeys.LAST_COUNTRY_CODE] = it }
            latitude?.let { preferences[PreferencesKeys.LAST_LATITUDE] = it }
            longitude?.let { preferences[PreferencesKeys.LAST_LONGITUDE] = it }
        }
    }

    suspend fun toggleFavorite(stationId: String) {
        context.dataStore.edit { preferences ->
            val currentFavorites = preferences[PreferencesKeys.FAVORITE_STATIONS] ?: emptySet()
            val newFavorites = currentFavorites.toMutableSet()
            if (stationId in currentFavorites) {
                newFavorites.remove(stationId)
            } else {
                newFavorites.add(stationId)
            }
            preferences[PreferencesKeys.FAVORITE_STATIONS] = newFavorites
        }
    }

    suspend fun setFavorites(stations: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FAVORITE_STATIONS] = stations
        }
    }
}
