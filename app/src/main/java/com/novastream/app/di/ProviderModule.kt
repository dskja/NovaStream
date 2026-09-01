package com.novastream.app.di

import android.content.Context
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.data.provider.ProviderManager
import com.novastream.app.data.provider.StreamingProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {

    @Provides
    @Singleton
    fun provideAppSettings(@ApplicationContext context: Context): AppSettings = AppSettings(context)

    @Provides
    @Singleton
    fun provideStreamingProviders(): List<StreamingProvider> = ProviderManager.providers

    // ProviderController: @Singleton with @Inject constructor (CatalogCacheDao via DatabaseModule)
}
