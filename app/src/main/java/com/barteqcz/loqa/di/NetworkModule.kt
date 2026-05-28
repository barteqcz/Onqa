package com.barteqcz.loqa.di

import com.barteqcz.loqa.data.remote.RadioApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
object NetworkModule {

    private const val BASE_URL = "https://loqa-api.barteq.cz/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideRetrofit(json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideRadioApiService(retrofit: Retrofit): RadioApiService {
        return retrofit.create(RadioApiService::class.java)
    }
}
