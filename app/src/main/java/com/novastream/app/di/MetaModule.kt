package com.novastream.app.di

import com.novastream.app.data.meta.FreeMetaGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MetaModule {

    @Provides
    @Singleton
    fun provideFreeMetaGraph(): FreeMetaGraph = FreeMetaGraph()
}
