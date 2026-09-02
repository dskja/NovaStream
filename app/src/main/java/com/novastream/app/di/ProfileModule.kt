package com.novastream.app.di

import android.content.Context
import com.novastream.app.download.DownloadManagerHelper
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.profile.ProfileManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProfileModule {

    @Provides
    @Singleton
    fun provideProfileManager(
        @ApplicationContext context: Context,
        db: NovaStreamDatabase,
        downloadHelper: DownloadManagerHelper
    ): ProfileManager = ProfileManager(context, db, downloadHelper)
}
