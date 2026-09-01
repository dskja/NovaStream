package com.novastream.app.di

import android.content.Context
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.data.provider.ProviderRegistry
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

    /**
     * Hilt factory entry point: initializes [ProviderRegistry] with @ApplicationContext so
     * dynamic-URL providers (Wiflix, FrenchStream, Cuevana3) can use [ProviderDomainManager].
     */
    @Provides
    @Singleton
    fun provideStreamingProviders(@ApplicationContext context: Context): List<StreamingProvider> {
        ProviderRegistry.initialize(context)
        return ProviderRegistry.providers
    }

    // ProviderController: @Singleton with @Inject constructor (CatalogCacheDao via DatabaseModule)
}
