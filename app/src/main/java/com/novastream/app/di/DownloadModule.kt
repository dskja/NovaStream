package com.novastream.app.di

import com.novastream.app.download.DownloadManagerHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadEntryPoint {
    fun downloadManagerHelper(): DownloadManagerHelper
}
