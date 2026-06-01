package com.barteqcz.loqa.location

import android.content.Context
import android.location.Location
import com.barteqcz.loqa.R
import com.barteqcz.loqa.data.repository.SettingsRepository
import com.barteqcz.loqa.data.model.LocationInfo
import com.barteqcz.loqa.data.util.NetworkResult
import com.barteqcz.loqa.di.ApplicationScope
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class LocationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val locationClient: LocationClient,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _locationInfo = MutableStateFlow(LocationInfo())
    val locationInfo: StateFlow<LocationInfo> = _locationInfo.asStateFlow()

    private var trackingJob: Job? = null
    private var geocodingJob: Job? = null

    companion object {
        private const val UPDATE_INTERVAL = 30000L
        private const val MIN_DISTANCE = 1000f
    }

    fun startTracking() {
        if (trackingJob != null) {
            Timber.d("Tracking already in progress, skipping start.")
            return
        }
        
        Timber.i("Starting location tracking...")
        trackingJob = scope.launch {
            loadSavedLocation()

            locationClient.getLastLocation()?.let { location ->
                updateLocation(location)
            }

            locationClient.getLocationUpdates(
                interval = UPDATE_INTERVAL,
                minDistance = MIN_DISTANCE,
                priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            ).collect { location ->
                updateLocation(location)
            }
        }
    }

    fun stopTracking() {
        Timber.i("Stopping location tracking.")
        trackingJob?.cancel()
        trackingJob = null
        geocodingJob?.cancel()
        geocodingJob = null
    }

    private suspend fun loadSavedLocation() {
        try {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.lastLatitude != null && settings.lastLongitude != null) {
                val savedLoc = Location("saved").apply {
                    latitude = settings.lastLatitude
                    longitude = settings.lastLongitude
                }
                _currentLocation.value = savedLoc
                
                settings.lastCity?.let { lastCity ->
                    _locationInfo.value = LocationInfo(
                        city = lastCity,
                        countryCode = settings.lastCountryCode
                    )
                }
                Timber.d("Loaded saved location: ${settings.lastCity} (${settings.lastLatitude}, ${settings.lastLongitude})")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load saved location from settings")
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
        geocodingJob?.cancel()
        geocodingJob = scope.launch {
            Timber.d("Starting geocoding for location: ${location.latitude}, ${location.longitude}")
            val result = locationRepository.getAddressesFromLocation(location)
            
            if (result is NetworkResult.Error) {
                Timber.w("Geocoding failed: ${result.message}")
            }

            val addresses = (result as? NetworkResult.Success)?.data
            val refinedInfo = AddressRefiner.refineLocation(addresses)
            
            val newCity = refinedInfo.city
            
            // Fallback for when we're in the middle of nowhere (like the sea)
            if (newCity == null && refinedInfo.country == null) {
                updateToUnknownLocation()
                return@launch
            }

            val citySearchQuery = buildCitySearchQuery(newCity, addresses?.firstOrNull())
            val distKm = if (newCity != null && citySearchQuery != null) {
                calculateCityDistance(location, citySearchQuery)
            } else {
                null
            }

            if (newCity != _locationInfo.value.city || distKm != _locationInfo.value.distanceKm) {
                val newInfo = refinedInfo.copy(
                    city = newCity ?: _locationInfo.value.city,
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

    private fun updateToUnknownLocation() {
        val unknownInfo = LocationInfo(
            city = context.getString(R.string.unknown_location),
            distanceKm = null
        )
        if (_locationInfo.value != unknownInfo) {
            _locationInfo.value = unknownInfo
        }
    }

    private fun buildCitySearchQuery(city: String?, address: android.location.Address?): String? {
        if (address == null) return city
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
