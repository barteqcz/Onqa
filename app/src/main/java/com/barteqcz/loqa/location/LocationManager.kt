package com.barteqcz.loqa.location

import android.content.Context
import android.location.Location
import com.barteqcz.loqa.R
import com.barteqcz.loqa.data.repository.SettingsRepository
import com.barteqcz.loqa.data.model.LocationInfo
import com.barteqcz.loqa.data.util.NetworkResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class LocationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val locationClient: LocationClient,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _locationInfo = MutableStateFlow(LocationInfo())
    val locationInfo: StateFlow<LocationInfo> = _locationInfo.asStateFlow()

    private var trackingJob: Job? = null

    fun startTracking() {
        if (trackingJob != null) return
        
        trackingJob = scope.launch {
            loadSavedLocation()

            locationClient.getLastLocation()?.let { location ->
                updateLocation(location)
            }

            locationClient.getLocationUpdates(
                interval = 30000L,
                minDistance = 1000f,
                priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY
            ).collect { location ->
                updateLocation(location)
            }
        }
    }

    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    private suspend fun loadSavedLocation() {
        val settings = settingsRepository.settingsFlow.first()
        if (settings.lastLatitude != null && settings.lastLongitude != null) {
            val savedLoc = Location("saved").apply {
                latitude = settings.lastLatitude
                longitude = settings.lastLongitude
            }
            _currentLocation.value = savedLoc
            
            if (settings.lastCity != null) {
                _locationInfo.value = LocationInfo(
                    city = settings.lastCity,
                    countryCode = settings.lastCountryCode
                )
            }
        }
    }

    private fun updateLocation(location: Location) {
        _currentLocation.value = location
        scope.launch {
            settingsRepository.updateLastLocation(
                city = _locationInfo.value.city,
                code = _locationInfo.value.countryCode,
                latitude = location.latitude,
                longitude = location.longitude
            )
        }
        handleGeocoding(location)
    }

    private fun handleGeocoding(location: Location) {
        scope.launch {
            val result = locationRepository.getAddressesFromLocation(location)
            val addresses = (result as? NetworkResult.Success)?.data
            val firstAddress = addresses?.firstOrNull()

            val bestCandidate = addresses?.let { AddressRefiner.findBestCityCandidate(it) }
            val newCity = AddressRefiner.cleanCityName(bestCandidate)
            
            // Fallback for when we're in the middle of nowhere (like the sea)
            if (newCity == null && firstAddress?.countryName == null) {
                val unknownInfo = LocationInfo(
                    city = context.getString(R.string.unknown_location),
                    distanceKm = null
                )
                if (_locationInfo.value != unknownInfo) {
                    _locationInfo.value = unknownInfo
                }
                return@launch
            }

            val citySearchQuery = firstAddress?.let { buildCitySearchQuery(newCity, it) }
            
            val distKm = if (newCity != null && citySearchQuery != null) {
                calculateCityDistance(location, citySearchQuery)
            } else {
                null
            }

            if (newCity != _locationInfo.value.city || distKm != _locationInfo.value.distanceKm) {
                val newInfo = LocationInfo(
                    city = newCity ?: _locationInfo.value.city,
                    country = firstAddress?.countryName ?: _locationInfo.value.country,
                    countryCode = firstAddress?.countryCode ?: _locationInfo.value.countryCode,
                    distanceKm = distKm
                )
                _locationInfo.value = newInfo
                
                settingsRepository.updateLastLocation(
                    city = newInfo.city,
                    code = newInfo.countryCode,
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            }
        }
    }

    private fun buildCitySearchQuery(city: String?, address: android.location.Address): String? {
        val terms = listOfNotNull(city, address.subAdminArea, address.adminArea, address.countryName)
            .distinct()
        return if (terms.isNotEmpty()) terms.joinToString(", ") else null
    }

    private suspend fun calculateCityDistance(currentLocation: Location, cityName: String): Int? {
        val result = locationRepository.getCityLocation(cityName, proximity = currentLocation)
        val cityLoc = (result as? NetworkResult.Success)?.data ?: return _locationInfo.value.distanceKm
        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude, currentLocation.longitude,
            cityLoc.latitude, cityLoc.longitude,
            results,
        )
        return (results[0] / 1000).roundToInt()
    }
}
