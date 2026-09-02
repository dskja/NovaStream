package com.novastream.app.di

import android.content.Context
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.prefs.AppSettings
import com.novastream.app.sync.BackupRestoreManager
import com.novastream.app.sync.CloudSyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideBackupRestoreManager(
        @ApplicationContext context: Context,
        db: NovaStreamDatabase
    ): BackupRestoreManager = BackupRestoreManager(context, db)

    @Provides
    @Singleton
    fun provideCloudSyncManager(
        @ApplicationContext context: Context,
        backupRestoreManager: BackupRestoreManager,
        appSettings: AppSettings
    ): CloudSyncManager = CloudSyncManager(context, backupRestoreManager, appSettings)
}
