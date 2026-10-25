package com.barteqcz.onqa.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.location.Address
import com.barteqcz.onqa.data.util.NetworkResult
import com.barteqcz.onqa.location.AddressRefiner
import com.barteqcz.onqa.location.LocationRepository
import com.barteqcz.onqa.ui.main.RadioViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import timber.log.Timber
import javax.inject.Inject

data class MapPickerState(
    val selectedLocation: GeoPoint? = null,
    val isGeocoding: Boolean = false,
    val city: String? = null,
    val countryCode: String? = null,
    val error: String? = null,
    val searchQuery: String = "",
    val searchResults: List<Address> = emptyList(),
    val mapCenterTrigger: Long = 0L,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MapPickerState())
    val state: StateFlow<MapPickerState> = _state.asStateFlow()

    fun onLocationSelected(point: GeoPoint) {
        _state.value = _state.value.copy(
            selectedLocation = point,
            isGeocoding = true,
            searchResults = emptyList(),
            error = null
        )
        
        viewModelScope.launch {
            val androidLoc = android.location.Location("map").apply {
                latitude = point.latitude
                longitude = point.longitude
            }
            
            when (val result = locationRepository.getAddressesFromLocation(androidLoc, 10)) {
                is NetworkResult.Success -> {
                    val refined = AddressRefiner.refineLocation(
                        result.data, 
                        point.latitude, 
                        point.longitude,
                    )
                    _state.value = _state.value.copy(
                        city = refined.city,
                        countryCode = refined.countryCode,
                        isGeocoding = false
                    )
                }
                is NetworkResult.Error -> {
                    Timber.w("Failed to geocode manual location: ${result.message}")
                    _state.value = _state.value.copy(
                        isGeocoding = false,
                        error = result.message
                    )
                }
                else -> {}
            }
        }
    }
    
    fun onSearchQueryChanged(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun searchLocation() {
        val query = _state.value.searchQuery
        if (query.isBlank()) return

        _state.value = _state.value.copy(isGeocoding = true, error = null, searchResults = emptyList())

        viewModelScope.launch {
            when (val result = locationRepository.searchCities(query)) {
                is NetworkResult.Success -> {
                    val addresses = result.data
                    if (addresses.isNotEmpty()) {
                        if (addresses.size == 1) {
                            onSearchResultSelected(addresses.first())
                        } else {
                            _state.value = _state.value.copy(
                                isGeocoding = false,
                                searchResults = addresses
                            )
                        }
                    } else {
                        _state.value = _state.value.copy(
                            isGeocoding = false,
                            error = "Location not found"
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _state.value = _state.value.copy(
                        isGeocoding = false,
                        error = result.message
                    )
                }
                else -> {
                    _state.value = _state.value.copy(isGeocoding = false)
                }
            }
        }
    }

    fun onSearchResultSelected(address: Address) {
        val point = GeoPoint(address.latitude, address.longitude)
        _state.value = _state.value.copy(
            selectedLocation = point,
            searchResults = emptyList(),
            mapCenterTrigger = System.currentTimeMillis()
        )
        // Trigger reverse geocoding to get city name
        onLocationSelected(point)
    }

    fun confirmLocation(radioViewModel: RadioViewModel, onConfirmed: () -> Unit) {
        val currentState = _state.value
        val loc = currentState.selectedLocation ?: return
        
        radioViewModel.updateManualLocation(
            city = currentState.city,
            code = currentState.countryCode,
            latitude = loc.latitude,
            longitude = loc.longitude
        )
        onConfirmed()
    }
}
