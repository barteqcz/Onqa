package com.barteqcz.onqa.ui.main

import android.location.Location
import com.barteqcz.onqa.data.model.RadioStation

sealed interface RadioUiState {
    data object Loading : RadioUiState
    data class Success(
        val stations: List<RadioStation>,
        val allStations: List<RadioStation> = emptyList(),
        val currentLocation: Location,
        val cityName: String? = null,
        val countryName: String? = null,
        val countryCode: String? = null,
    ) : RadioUiState
    data class Error(val message: String, val isServerError: Boolean = false) : RadioUiState
}
