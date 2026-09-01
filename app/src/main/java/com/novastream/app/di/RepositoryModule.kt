package com.novastream.app.di

import android.content.Context
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.repository.NovaStreamRepository
import com.novastream.app.data.repository.WatchRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideNovaStreamRepository(@ApplicationContext context: Context): NovaStreamRepository =
        NovaStreamRepository.get(context)

    @Provides
    @Singleton
    fun provideWatchRepository(db: NovaStreamDatabase): WatchRepository = WatchRepository(db)
}
