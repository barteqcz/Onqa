package com.barteqcz.loqa.di

import android.content.Context
import com.barteqcz.loqa.data.RadioRepository
import com.barteqcz.loqa.data.SettingsRepository
import com.barteqcz.loqa.data.remote.RadioApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
object RepositoryModule {

    @Provides
    @Singleton
    fun provideRadioRepository(
        apiService: RadioApiService,
        settingsRepository: SettingsRepository,
        @ApplicationContext context: Context
    ): RadioRepository {
        return RadioRepository(apiService, settingsRepository, context)
    }
}
