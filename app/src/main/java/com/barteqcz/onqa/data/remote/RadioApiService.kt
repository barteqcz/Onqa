package com.barteqcz.onqa.data.remote

import com.barteqcz.onqa.data.model.RadioStation
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface RadioApiService {
    @GET("stations")
    suspend fun getAllStations(): List<RadioStation>

    @POST("stations/nearby")
    suspend fun getNearbyStations(
        @Body locationRequest: LocationRequest,
    ): List<RadioStation>
}
