package com.barteqcz.onqa.ui.main

import androidx.compose.runtime.Immutable
import com.barteqcz.onqa.data.model.RadioStation
import com.barteqcz.onqa.data.model.StableLocation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
sealed interface RadioUiState {
    data object Loading : RadioUiState
    data class Success(
        val stations: ImmutableList<RadioStation>,
        val allStations: ImmutableList<RadioStation> = persistentListOf(),
        val currentLocation: StableLocation,
        val cityName: String? = null,
        val countryName: String? = null,
        val countryCode: String? = null,
    ) : RadioUiState
    data class Error(val message: String, val isServerError: Boolean = false) : RadioUiState
}
