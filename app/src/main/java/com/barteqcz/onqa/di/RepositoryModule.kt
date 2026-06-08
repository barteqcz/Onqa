package com.barteqcz.onqa.di

import android.content.Context
import com.barteqcz.onqa.data.repository.RadioRepository
import com.barteqcz.onqa.data.repository.SettingsRepository
import com.barteqcz.onqa.data.remote.RadioApiService
import com.barteqcz.onqa.location.LocationManager
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
        settingsRepository: SettingsRepository,
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope
    ): RadioRepository {
        return RadioRepository(apiService, locationManager, settingsRepository, context, scope)
    }
}
