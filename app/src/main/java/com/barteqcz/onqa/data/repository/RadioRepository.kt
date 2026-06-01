package com.barteqcz.onqa.data.repository

import android.content.Context
import android.location.Location
import com.barteqcz.onqa.R
import com.barteqcz.onqa.data.model.LocationInfo
import com.barteqcz.onqa.data.model.RadioStation
import com.barteqcz.onqa.data.remote.LocationRequest
import com.barteqcz.onqa.data.remote.RadioApiService
import com.barteqcz.onqa.data.util.NetworkResult
import com.barteqcz.onqa.di.ApplicationScope
import com.barteqcz.onqa.location.LocationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioRepository @Inject constructor(
    private val apiService: RadioApiService,
    private val locationManager: LocationManager,
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _stations = MutableStateFlow<NetworkResult<List<RadioStation>>>(NetworkResult.Loading)
    val stations: StateFlow<NetworkResult<List<RadioStation>>> = _stations.asStateFlow()

    val currentLocation: StateFlow<Location?> = locationManager.currentLocation
    val locationInfo: StateFlow<LocationInfo> = locationManager.locationInfo

    private var isFetching = false
    private var observationJob: Job? = null

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
                .collect { location ->
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
        if (isFetching) {
            Timber.d("Already fetching stations, skipping.")
            return
        }
        isFetching = true

        try {
            Timber.d("Fetching nearby stations for: ${location.latitude}, ${location.longitude}")
            val request = LocationRequest(location.latitude, location.longitude)
            val result = apiService.getNearbyStations(request)
            _stations.value = NetworkResult.Success(result)
            Timber.i("Successfully fetched ${result.size} stations.")
        } catch (e: IOException) {
            Timber.e(e, "IO error fetching stations")
            val isServerError = e !is UnknownHostException
            val message = e.message ?: context.getString(R.string.error_io)
            _stations.value = NetworkResult.Error(message, e, isServerError = isServerError)
        } catch (e: HttpException) {
            Timber.e(e, "HTTP error fetching stations (code: ${e.code()})")
            _stations.value = NetworkResult.Error(context.getString(R.string.error_server_with_code, e.code()), e, isServerError = true)
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error fetching stations")
            val message = e.message ?: context.getString(R.string.error_unknown)
            _stations.value = NetworkResult.Error(message, e, isServerError = true)
        } finally {
            isFetching = false
        }
    }
}
