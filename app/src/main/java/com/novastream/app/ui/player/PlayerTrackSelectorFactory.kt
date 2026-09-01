@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.novastream.app.ui.player

import android.content.Context
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

internal fun createPlayerTrackSelector(context: Context, dataSaverMode: Boolean): DefaultTrackSelector =
    DefaultTrackSelector(context).apply {
        if (dataSaverMode) {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(1280, 720)
                    .setMaxVideoBitrate(1_500_000)
            )
        }
    }
