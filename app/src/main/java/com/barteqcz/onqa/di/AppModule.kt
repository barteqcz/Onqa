package com.barteqcz.onqa.di

import android.content.Context
import com.barteqcz.onqa.location.DefaultLocationClient
import com.barteqcz.onqa.location.LocationClient
import com.barteqcz.onqa.util.ConnectivityObserver
import com.barteqcz.onqa.util.NetworkConnectivityObserver
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    @Suppress("unused")
    abstract fun bindConnectivityObserver(
        observer: NetworkConnectivityObserver,
    ): ConnectivityObserver

    companion object {
        @Provides
        @Singleton
        fun provideLocationClient(@ApplicationContext context: Context): LocationClient {
            return DefaultLocationClient(
                LocationServices.getFusedLocationProviderClient(context),
            )
        }
    }
}
