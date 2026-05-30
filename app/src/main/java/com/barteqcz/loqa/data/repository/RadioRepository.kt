package com.barteqcz.loqa.data.repository

import android.content.Context
import android.location.Location
import com.barteqcz.loqa.R
import com.barteqcz.loqa.data.model.LocationInfo
import com.barteqcz.loqa.data.model.RadioStation
import com.barteqcz.loqa.data.remote.LocationRequest
import com.barteqcz.loqa.data.remote.RadioApiService
import com.barteqcz.loqa.data.util.NetworkResult
import com.barteqcz.loqa.location.LocationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioRepository @Inject constructor(
    private val apiService: RadioApiService,
    private val locationManager: LocationManager,
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _stations = MutableStateFlow<NetworkResult<List<RadioStation>>>(NetworkResult.Loading)
    val stations: StateFlow<NetworkResult<List<RadioStation>>> = _stations.asStateFlow()

    val currentLocation: StateFlow<Location?> = locationManager.currentLocation
    val locationInfo: StateFlow<LocationInfo> = locationManager.locationInfo

    private var isFetching = false
    private var observationJob: Job? = null

    fun startLocationTracking() {
        locationManager.startTracking()
        
        if (observationJob != null) return
        observationJob = scope.launch {
            locationManager.currentLocation
                .filterNotNull()
                .distinctUntilChanged { old, new ->
                    old.latitude == new.latitude && old.longitude == new.longitude
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
        if (isFetching) return
        isFetching = true

        try {
            val request = LocationRequest(location.latitude, location.longitude)
            val result = apiService.getNearbyStations(request)
            _stations.value = NetworkResult.Success(result)
        } catch (e: IOException) {
            val isServerError = e !is UnknownHostException
            val message = e.message ?: context.getString(R.string.error_io)
            _stations.value = NetworkResult.Error(message, e, isServerError = isServerError)
        } catch (e: HttpException) {
            _stations.value = NetworkResult.Error(context.getString(R.string.error_server_with_code, e.code()), e, isServerError = true)
        } catch (e: Exception) {
            val message = e.message ?: context.getString(R.string.error_unknown)
            _stations.value = NetworkResult.Error(message, e, isServerError = true)
        } finally {
            isFetching = false
        }
    }
}
