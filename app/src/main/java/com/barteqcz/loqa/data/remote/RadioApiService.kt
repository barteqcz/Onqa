package com.barteqcz.loqa.data.remote

import com.barteqcz.loqa.data.model.RadioStation
import retrofit2.http.Body
import retrofit2.http.POST

interface RadioApiService {
    @POST("stations/nearby")
    suspend fun getNearbyStations(
        @Body locationRequest: LocationRequest,
    ): List<RadioStation>
}
