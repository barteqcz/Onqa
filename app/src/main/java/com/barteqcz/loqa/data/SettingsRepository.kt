package com.barteqcz.loqa.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
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
        val MATERIAL_YOU = booleanPreferencesKey("material_you")
        val ACCENT_COLOR = intPreferencesKey("accent_color")
        val LAST_CITY = stringPreferencesKey("last_city")
        val LAST_COUNTRY_CODE = stringPreferencesKey("last_country_code")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val FAVORITE_STATIONS = stringSetPreferencesKey("favorite_stations")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .map { preferences ->
            AppSettings(
                isMaterialYouEnabled = preferences[PreferencesKeys.MATERIAL_YOU] ?: false,
                accentColor = Color(preferences[PreferencesKeys.ACCENT_COLOR] ?: 0xFF8DE19C.toInt()),
                lastCity = preferences[PreferencesKeys.LAST_CITY],
                lastCountryCode = preferences[PreferencesKeys.LAST_COUNTRY_CODE],
                isOnboardingCompleted = preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false,
                favoriteStations = preferences[PreferencesKeys.FAVORITE_STATIONS] ?: emptySet(),
                isInitialValue = false
            )
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

    suspend fun updateAccentColor(color: Color) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCENT_COLOR] = color.toArgb()
        }
    }

    suspend fun updateLastLocation(city: String?, code: String?) {
        context.dataStore.edit { preferences ->
            city?.let { preferences[PreferencesKeys.LAST_CITY] = it }
            code?.let { preferences[PreferencesKeys.LAST_COUNTRY_CODE] = it }
        }
    }

    suspend fun toggleFavorite(stationId: String) {
        context.dataStore.edit { preferences ->
            val currentFavorites = preferences[PreferencesKeys.FAVORITE_STATIONS] ?: emptySet()
            val newFavorites = if (currentFavorites.contains(stationId)) {
                currentFavorites - stationId
            } else {
                currentFavorites + stationId
            }
            preferences[PreferencesKeys.FAVORITE_STATIONS] = newFavorites
        }
    }
}

data class AppSettings(
    val isMaterialYouEnabled: Boolean,
    val accentColor: Color,
    val lastCity: String? = null,
    val lastCountryCode: String? = null,
    val isOnboardingCompleted: Boolean = false,
    val favoriteStations: Set<String> = emptySet(),
    val isInitialValue: Boolean = true,
)
