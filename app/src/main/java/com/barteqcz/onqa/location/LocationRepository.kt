/**
 * Repository handling reverse geocoding and city search queries using the Android system Geocoder.
 */
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
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

@Singleton
class LocationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private companion object {
        val GEOCODER_TIMEOUT = 10.seconds
    }

    suspend fun getAddressesFromLocation(
        location: Location,
        maxResults: Int = 25
    ): NetworkResult<List<Address>> = withContext(ioDispatcher) {
        val result = withTimeoutOrNull(GEOCODER_TIMEOUT) {
            suspendCancellableCoroutine { continuation ->
                val geocoder = Geocoder(context, Locale.getDefault())

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(
                            location.latitude,
                            location.longitude,
                            maxResults,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (continuation.isActive) {
                                        continuation.resume(NetworkResult.Success(addresses))
                                    }
                                }

                                override fun onError(errorMessage: String?) {
                                    if (continuation.isActive) {
                                        continuation.resume(
                                            NetworkResult.Error(
                                                errorMessage?.takeIf(String::isNotBlank)
                                                    ?: context.getString(R.string.error_resolve_address)
                                            )
                                        )
                                    }
                                }
                            },
                        )
                    } else {
                        val addresses = geocoder.getFromLocation(
                            location.latitude,
                            location.longitude,
                            maxResults,
                        )
                        if (continuation.isActive) {
                            continuation.resume(NetworkResult.Success(addresses.orEmpty()))
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get addresses from location")
                    if (continuation.isActive) {
                        continuation.resume(NetworkResult.Error(context.getString(R.string.error_resolve_address)))
                    }
                }
            }
        }
        result ?: NetworkResult.Error(context.getString(R.string.error_resolve_address))
    }

    suspend fun searchCities(
        cityName: String,
        maxResults: Int = 5
    ): NetworkResult<List<Address>> = withContext(ioDispatcher) {
        val result = withTimeoutOrNull(GEOCODER_TIMEOUT) {
            suspendCancellableCoroutine { continuation ->
                val geocoder = Geocoder(context, Locale.getDefault())
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocationName(
                            cityName,
                            maxResults,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (continuation.isActive) {
                                        continuation.resume(NetworkResult.Success(addresses))
                                    }
                                }

                                override fun onError(errorMessage: String?) {
                                    if (continuation.isActive) {
                                        continuation.resume(
                                            NetworkResult.Error(
                                                errorMessage?.takeIf(String::isNotBlank)
                                                    ?: context.getString(R.string.error_find_coordinates)
                                            )
                                        )
                                    }
                                }
                            },
                        )
                    } else {
                        val addresses = geocoder.getFromLocationName(cityName, maxResults)
                        if (continuation.isActive) {
                            continuation.resume(NetworkResult.Success(addresses.orEmpty()))
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to search cities for $cityName")
                    if (continuation.isActive) {
                        continuation.resume(NetworkResult.Error(context.getString(R.string.error_find_coordinates)))
                    }
                }
            }
        }
        result ?: NetworkResult.Error(context.getString(R.string.error_find_coordinates))
    }
}
