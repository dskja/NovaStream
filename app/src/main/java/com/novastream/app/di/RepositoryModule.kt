package com.novastream.app.di

import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.repository.NovaStreamRepository
import com.novastream.app.data.repository.WatchRepository
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.profile.ProfileManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideNovaStreamRepository(
        db: NovaStreamDatabase,
        appSettings: AppSettings
    ): NovaStreamRepository =
        NovaStreamRepository.forCache(db.catalogCacheDao(), appSettings)

    @Provides
    @Singleton
    fun provideWatchRepository(db: NovaStreamDatabase, profileManager: ProfileManager): WatchRepository =
        WatchRepository(db, profileManager)
}
