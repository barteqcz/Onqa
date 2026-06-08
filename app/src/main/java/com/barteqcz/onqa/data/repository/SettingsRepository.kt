package com.barteqcz.onqa.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.barteqcz.onqa.data.model.AppSettings
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
class SettingsRepository @Inject constructor(
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
        val LAST_LATITUDE = doublePreferencesKey("last_latitude")
        val LAST_LONGITUDE = doublePreferencesKey("last_longitude")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .map { preferences ->
            AppSettings(
                themeMode = ThemeMode.valueOf(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name),
                isMaterialYouEnabled = preferences[PreferencesKeys.MATERIAL_YOU] ?: false,
                accentColor = Color(preferences[PreferencesKeys.ACCENT_COLOR] ?: OnqaGreen.toArgb()),
                lastCity = preferences[PreferencesKeys.LAST_CITY],
                lastCountryCode = preferences[PreferencesKeys.LAST_COUNTRY_CODE],
                isOnboardingCompleted = preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false,
                favoriteStations = preferences[PreferencesKeys.FAVORITE_STATIONS]?.toPersistentSet() ?: kotlinx.collections.immutable.persistentSetOf(),
                useHqStream = preferences[PreferencesKeys.USE_HQ_STREAM] ?: false,
                lastLatitude = preferences[PreferencesKeys.LAST_LATITUDE],
                lastLongitude = preferences[PreferencesKeys.LAST_LONGITUDE],
                isInitialValue = false
            )
        }

    suspend fun updateUseHqStream(useHq: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_HQ_STREAM] = useHq
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

    suspend fun updateAccentColor(color: Color) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCENT_COLOR] = color.toArgb()
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
}
