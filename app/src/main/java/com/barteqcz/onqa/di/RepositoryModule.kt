package com.barteqcz.onqa.di

import android.content.Context
import com.barteqcz.onqa.data.db.CatalogDatabaseManager
import com.barteqcz.onqa.data.repository.RadioRepository
import com.barteqcz.onqa.data.repository.SettingsRepository
import com.barteqcz.onqa.location.LocationManager
import com.barteqcz.onqa.util.ConnectivityObserver
import com.barteqcz.onqa.util.NetworkConnectivityObserver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideConnectivityObserver(
        networkConnectivityObserver: NetworkConnectivityObserver
    ): ConnectivityObserver {
        return networkConnectivityObserver
    }

    @Provides
    @Singleton
    fun provideRadioRepository(
        catalogDatabaseManager: CatalogDatabaseManager,
        locationManager: LocationManager,
        settingsRepository: SettingsRepository,
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): RadioRepository {
        return RadioRepository(catalogDatabaseManager, locationManager, settingsRepository, context, scope)
    }
}
