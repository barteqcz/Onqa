package com.barteqcz.loqa.di

import android.content.Context
import com.barteqcz.loqa.data.repository.RadioRepository
import com.barteqcz.loqa.data.remote.RadioApiService
import com.barteqcz.loqa.location.LocationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
object RepositoryModule {

    @Provides
    @Singleton
    fun provideRadioRepository(
        apiService: RadioApiService,
        locationManager: LocationManager,
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope
    ): RadioRepository {
        return RadioRepository(apiService, locationManager, context, scope)
    }
}
