package com.barteqcz.loqa.data.remote

import com.barteqcz.loqa.data.model.RadioStation
import retrofit2.http.GET
import retrofit2.http.Query

interface RadioApiService {
    @GET("stations/nearby")
    suspend fun getNearbyStations(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
    ): List<RadioStation>
}
