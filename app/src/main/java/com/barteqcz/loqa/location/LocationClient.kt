package com.barteqcz.loqa.location

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface LocationClient {
    fun getLocationUpdates(
        interval: Long,
        minDistance: Float = 0f,
        priority: Int = Priority.PRIORITY_HIGH_ACCURACY,
    ): Flow<Location>

    suspend fun getLastLocation(): Location?
}

class DefaultLocationClient(
    private val client: FusedLocationProviderClient,
): LocationClient {

    @SuppressLint("MissingPermission")
    override suspend fun getLastLocation(): Location? = suspendCancellableCoroutine { continuation ->
        client.lastLocation.addOnSuccessListener { location ->
            continuation.resume(location)
        }.addOnFailureListener {
            continuation.resume(null)
        }
    }

    @SuppressLint("MissingPermission")
    override fun getLocationUpdates(
        interval: Long,
        minDistance: Float,
        priority: Int,
    ): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(priority, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .setMinUpdateDistanceMeters(minDistance)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                result.locations.lastOrNull()?.let { location ->
                    launch { send(location) }
                }
            }
        }

        client.requestLocationUpdates(
            request,
            callback,
            Looper.getMainLooper()
        ).addOnFailureListener { e ->
            close(e)
        }

        awaitClose {
            client.removeLocationUpdates(callback)
        }
    }
}
