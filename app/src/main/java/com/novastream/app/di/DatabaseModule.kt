package com.novastream.app.di

import android.content.Context
import com.novastream.app.data.db.CatalogCacheDao
import com.novastream.app.data.db.ContentDao
import com.novastream.app.data.db.DownloadDao
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.db.ProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideNovaStreamDatabase(@ApplicationContext context: Context): NovaStreamDatabase =
        NovaStreamDatabase.get(context)

    @Provides
    fun provideCatalogCacheDao(db: NovaStreamDatabase): CatalogCacheDao = db.catalogCacheDao()

    @Provides
    fun provideContentDao(db: NovaStreamDatabase): ContentDao = db.contentDao()

    @Provides
    fun provideDownloadDao(db: NovaStreamDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideProfileDao(db: NovaStreamDatabase): ProfileDao = db.profileDao()
}
