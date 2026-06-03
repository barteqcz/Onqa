package com.barteqcz.onqa.ui.main

import android.location.Location
import androidx.compose.runtime.Immutable
import com.barteqcz.onqa.data.model.RadioStation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
sealed interface RadioUiState {
    data object Loading : RadioUiState
    data class Success(
        val stations: ImmutableList<RadioStation>,
        val allStations: ImmutableList<RadioStation> = persistentListOf(),
        val currentLocation: Location,
        val cityName: String? = null,
        val countryName: String? = null,
        val countryCode: String? = null,
    ) : RadioUiState
    data class Error(val message: String, val isServerError: Boolean = false) : RadioUiState
}
