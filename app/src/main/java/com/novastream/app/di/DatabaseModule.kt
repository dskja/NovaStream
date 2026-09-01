package com.novastream.app.di

import android.content.Context
import com.novastream.app.data.db.NovaStreamDatabase
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
}
