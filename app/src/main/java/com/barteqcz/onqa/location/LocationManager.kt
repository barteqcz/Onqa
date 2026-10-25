package com.barteqcz.onqa.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.barteqcz.onqa.data.model.LocationInfo
import com.barteqcz.onqa.data.model.LocationSource
import com.barteqcz.onqa.data.repository.SettingsRepository
import com.barteqcz.onqa.data.util.NetworkResult
import com.barteqcz.onqa.di.ApplicationScope
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationManager @Inject constructor(
    private val locationClient: LocationClient,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _locationInfo = MutableStateFlow(LocationInfo())
    val locationInfo: StateFlow<LocationInfo> = _locationInfo.asStateFlow()

    private val _settings = settingsRepository.settingsFlow
        .stateIn(scope, SharingStarted.Eagerly, null)

    private var currentLocationSource: LocationSource = LocationSource.GPS

    init {
        scope.launch {
            _settings.filterNotNull().collect { settings ->
                val oldSource = currentLocationSource
                currentLocationSource = settings.locationSource
                
                if (settings.locationSource == LocationSource.MANUAL) {
                    stopTracking()
                    if ((settings.manualLatitude != null) && (settings.manualLongitude != null)) {
                        val manualLoc = Location("manual").apply {
                            latitude = settings.manualLatitude
                            longitude = settings.manualLongitude
                        }
                        _currentLocation.value = manualLoc
                        _locationInfo.value = LocationInfo(
                            city = settings.manualCity,
                            countryCode = settings.manualCountryCode,
                        )
                        Timber.d("Using manual location: ${settings.manualCity} (${settings.manualLatitude}, ${settings.manualLongitude})")
                    }
                } else {
                    // GPS Mode
                    if ((_currentLocation.value == null) || (oldSource == LocationSource.MANUAL)) {
                        if ((settings.lastLatitude != null) && (settings.lastLongitude != null)) {
                            val savedLoc = Location("saved").apply {
                                latitude = settings.lastLatitude
                                longitude = settings.lastLongitude
                            }
                            _currentLocation.value = savedLoc
                            _locationInfo.value = LocationInfo(
                                city = settings.lastCity,
                                countryCode = settings.lastCountryCode,
                            )
                        }
                    }
                    
                    if (oldSource == LocationSource.MANUAL) {
                        startTracking()
                    }
                }
            }
        }
    }

    private var trackingJob: Job? = null
    private var geocodingJob: Job? = null
    private var isAppInForeground: Boolean = false

    companion object {
        private const val UPDATE_INTERVAL = 30000L
        private const val MIN_DISTANCE = 1000f
    }

    fun startTracking() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.w("Location permission not granted, cannot start tracking.")
            return
        }

        scope.launch {
            // Wait for settings to be loaded to know which source to use
            val settings = _settings.filterNotNull().first()
            
            if (settings.locationSource == LocationSource.MANUAL) {
                Timber.d("Manual location source enabled, skipping startTracking.")
                return@launch
            }
            
            if (trackingJob != null) {
                Timber.d("Tracking already in progress, skipping start.")
                return@launch
            }

            Timber.i("Starting location tracking...")
            trackingJob = launch {
                try {
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
                } catch (e: SecurityException) {
                    Timber.e(e, "Location permission not granted")
                }
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

    private fun updateLocation(location: Location) {
        if (currentLocationSource == LocationSource.MANUAL) return
        
        _currentLocation.value = location
        scope.launch {
            settingsRepository.updateLastLocation(
                city = _locationInfo.value.city,
                code = _locationInfo.value.countryCode,
                latitude = location.latitude,
                longitude = location.longitude,
            )
        }
        handleGeocoding(location)
    }

    fun setAppForeground(foreground: Boolean) {
        if (isAppInForeground != foreground) {
            isAppInForeground = foreground
            if (foreground) {
                _currentLocation.value?.let { handleGeocoding(it) }
            }
        }
    }

    private fun handleGeocoding(location: Location) {
        if (!isAppInForeground || currentLocationSource == LocationSource.MANUAL) {
            return
        }
        geocodingJob?.cancel()
        geocodingJob = scope.launch {
            val result = locationRepository.getAddressesFromLocation(location)

            (result as? NetworkResult.Error)?.let {
                Timber.w("Geocoding failed: ${it.message}")
            }

            val addresses = (result as? NetworkResult.Success)?.data
            val refinedInfo = AddressRefiner.refineLocation(addresses, location.latitude, location.longitude)

            val newCity = refinedInfo.city

            if (newCity == null && refinedInfo.country == null) {
                updateToUnknownLocation()
                return@launch
            }

            if (newCity != _locationInfo.value.city ||
                refinedInfo.countryCode != _locationInfo.value.countryCode) {

                val newInfo = refinedInfo.copy(
                    city = newCity ?: _locationInfo.value.city
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
            city = null,
            countryCode = null
        )
        if (_locationInfo.value != unknownInfo) {
            _locationInfo.value = unknownInfo
        }
    }
}
