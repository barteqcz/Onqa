package com.barteqcz.loqa.data

import android.content.Context
import android.location.Location
import com.barteqcz.loqa.R
import com.barteqcz.loqa.data.model.RadioStation
import com.barteqcz.loqa.data.remote.RadioApiService
import com.barteqcz.loqa.data.util.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException
import java.io.IOException
import java.net.UnknownHostException

class RadioRepository(
    private val apiService: RadioApiService,
    private val context: Context,
) {
    private val _stations = MutableStateFlow<NetworkResult<List<RadioStation>>>(NetworkResult.Loading)
    val stations: StateFlow<NetworkResult<List<RadioStation>>> = _stations.asStateFlow()

    private var lastFetchedLocation: Location? = null
    private var isFetching = false

    suspend fun updateNearbyStations(location: Location, force: Boolean = false) {
        val distance = lastFetchedLocation?.distanceTo(location) ?: Float.MAX_VALUE
        
        if (!force && distance < MIN_REFRESH_DISTANCE_METERS && _stations.value is NetworkResult.Success) {
            return
        }

        if (isFetching) return
        isFetching = true

        try {
            val result = apiService.getNearbyStations(location.latitude, location.longitude)
            lastFetchedLocation = location
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

    companion object {
        private const val MIN_REFRESH_DISTANCE_METERS = 100f
    }
}
