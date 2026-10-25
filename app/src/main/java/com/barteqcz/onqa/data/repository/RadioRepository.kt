package com.barteqcz.onqa.data.repository

import android.Manifest
import android.content.Context
import android.location.Location
import androidx.annotation.RequiresPermission
import com.barteqcz.onqa.R
import com.barteqcz.onqa.data.db.CatalogDatabaseManager
import com.barteqcz.onqa.data.model.LocationInfo
import com.barteqcz.onqa.data.model.RadioStation
import com.barteqcz.onqa.data.util.NetworkResult
import com.barteqcz.onqa.di.ApplicationScope
import com.barteqcz.onqa.location.LocationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioRepository @Inject constructor(
    private val catalogDatabaseManager: CatalogDatabaseManager,
    private val locationManager: LocationManager,
    private val settingsRepository: SettingsRepository,
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _stations = MutableStateFlow<NetworkResult<List<RadioStation>>>(NetworkResult.Loading)
    val stations: StateFlow<NetworkResult<List<RadioStation>>> = _stations.asStateFlow()

    val currentLocation: StateFlow<Location?> = locationManager.currentLocation
    val locationInfo: StateFlow<LocationInfo> = locationManager.locationInfo

    private var observationJob: Job? = null

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun startLocationTracking() {
        Timber.i("Starting radio station location tracking...")
        locationManager.startTracking()
        
        if (observationJob != null) {
            Timber.d("Observation job already running.")
            return
        }
        observationJob = scope.launch {
            locationManager.currentLocation
                .filterNotNull()
                .distinctUntilChanged { old, new ->
                    (old.latitude == new.latitude) && (old.longitude == new.longitude)
                }
                .collectLatest { location ->
                    updateNearbyStations(location)
                }
        }
    }

    fun stopLocationTracking() {
        locationManager.stopTracking()
        observationJob?.cancel()
        observationJob = null
    }

    suspend fun updateNearbyStations(location: Location) {
        if (_stations.value !is NetworkResult.Success) {
            _stations.value = NetworkResult.Loading
        }
        try {
            val settings = settingsRepository.settingsFlow.first()
            catalogDatabaseManager.checkAutoUpdate(settings.catalogUpdateFrequency, settings.lastCatalogUpdate)

            val result = catalogDatabaseManager.getNearbyStations(location.latitude, location.longitude)
            if (result.isNotEmpty() || catalogDatabaseManager.isDatabasePresent()) {
                _stations.value = NetworkResult.Success(result)
            } else {
                val message = context.getString(R.string.no_stations_message)
                _stations.value = NetworkResult.Error(message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error updating nearby stations from catalog database")
            if (_stations.value !is NetworkResult.Success) {
                val message = e.message ?: context.getString(R.string.error_unknown)
                _stations.value = NetworkResult.Error(message)
            }
        }
    }

    suspend fun updateCatalogManually(): Boolean {
        val success = catalogDatabaseManager.downloadCatalog()
        if (success) {
            currentLocation.value?.let { loc ->
                updateNearbyStations(loc)
            }
        }
        return success
    }
}
