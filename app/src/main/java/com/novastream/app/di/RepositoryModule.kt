package com.novastream.app.di

import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.repository.NovaStreamRepository
import com.novastream.app.data.repository.WatchRepository
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
    fun provideNovaStreamRepository(db: NovaStreamDatabase): NovaStreamRepository =
        NovaStreamRepository.forCache(db.catalogCacheDao())

    @Provides
    @Singleton
    fun provideWatchRepository(db: NovaStreamDatabase): WatchRepository = WatchRepository(db)
}
