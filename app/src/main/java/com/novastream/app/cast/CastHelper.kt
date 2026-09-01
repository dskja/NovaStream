package com.novastream.app.cast

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.gms.cast.framework.CastContext

/**
 * Google Cast helper via Media3 cast extension (v12).
 * Gracefully no-ops when Play Services / Cast SDK unavailable.
 */
class CastHelper(context: Context) {

    private val castContext: CastContext? = try {
        CastContext.getSharedInstance(context.applicationContext)
    } catch (_: Exception) {
        null
    }

    val isAvailable: Boolean get() = castContext != null

    fun createCastPlayer(): CastPlayer? = try {
        castContext?.let { CastPlayer(it) }
    } catch (_: Exception) {
        null
    }

    fun isCastSessionActive(): Boolean =
        castContext?.sessionManager?.currentCastSession?.isConnected == true

    fun loadOnCast(player: CastPlayer, url: String, title: String) {
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    companion object {
        @Volatile
        private var instance: CastHelper? = null

        fun get(context: Context): CastHelper =
            instance ?: synchronized(this) {
                instance ?: CastHelper(context.applicationContext).also { instance = it }
            }
    }
}
