package com.barteqcz.onqa.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.barteqcz.onqa.R
import com.barteqcz.onqa.data.util.NetworkResult
import com.barteqcz.onqa.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun getAddressesFromLocation(
        location: Location,
        maxResults: Int = 10
    ): NetworkResult<List<Address>> = withContext(ioDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val geocoder = Geocoder(context, Locale.getDefault())

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(location.latitude, location.longitude, maxResults) { addresses ->
                        if (continuation.isActive) {
                            continuation.resume(NetworkResult.Success(addresses))
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, maxResults)
                    if (continuation.isActive) {
                        continuation.resume(NetworkResult.Success(addresses ?: emptyList()))
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get addresses from location")
                if (continuation.isActive) {
                    continuation.resume(NetworkResult.Error(context.getString(R.string.error_resolve_address), e))
                }
            }
        }
    }

    suspend fun getCityLocation(
        cityName: String,
        proximity: Location? = null
    ): NetworkResult<Location?> = withContext(ioDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val geocoder = Geocoder(context, Locale.getDefault())
            try {
                val maxResults = 1
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val listener = Geocoder.GeocodeListener { addresses ->
                        if (continuation.isActive) {
                            val address = addresses.firstOrNull()
                            if (address != null) {
                                val loc = Location("geocoder").apply {
                                    latitude = address.latitude
                                    longitude = address.longitude
                                }
                                continuation.resume(NetworkResult.Success(loc))
                            } else {
                                continuation.resume(NetworkResult.Success(null))
                            }
                        }
                    }
                    if (proximity != null) {
                        geocoder.getFromLocationName(
                            cityName, maxResults,
                            proximity.latitude - 0.5, proximity.longitude - 0.5,
                            proximity.latitude + 0.5, proximity.longitude + 0.5,
                            listener
                        )
                    } else {
                        geocoder.getFromLocationName(cityName, maxResults, listener)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = if (proximity != null) {
                        geocoder.getFromLocationName(
                            cityName, maxResults,
                            proximity.latitude - 0.5, proximity.longitude - 0.5,
                            proximity.latitude + 0.5, proximity.longitude + 0.5
                        )
                    } else {
                        geocoder.getFromLocationName(cityName, maxResults)
                    }

                    if (continuation.isActive) {
                        val address = addresses?.firstOrNull()
                        if (address != null) {
                            val loc = Location("geocoder").apply {
                                latitude = address.latitude
                                longitude = address.longitude
                            }
                            continuation.resume(NetworkResult.Success(loc))
                        } else {
                            continuation.resume(NetworkResult.Success(null))
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get city location for $cityName")
                if (continuation.isActive) {
                    continuation.resume(NetworkResult.Error(context.getString(R.string.error_find_coordinates), e))
                }
            }
        }
    }
}
