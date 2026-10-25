package com.barteqcz.onqa.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming

interface RadioApiService {
    @Streaming
    @GET("downloads/catalog.sqlite")
    suspend fun downloadDatabase(): ResponseBody
}
