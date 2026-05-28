package com.barteqcz.loqa.di

import android.content.Context
import com.barteqcz.loqa.location.DefaultLocationClient
import com.barteqcz.loqa.location.LocationClient
import com.barteqcz.loqa.util.ConnectivityObserver
import com.barteqcz.loqa.util.NetworkConnectivityObserver
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
@Suppress("unused")
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindConnectivityObserver(
        observer: NetworkConnectivityObserver
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
