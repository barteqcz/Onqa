package com.barteqcz.onqa.data.remote

import com.barteqcz.onqa.data.model.GitHubRelease
import retrofit2.http.GET

interface GitHubApiService {
    @GET("repos/barteqcz/Onqa/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease
}
