package com.novastream.app.ui.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

/**
 * Koordiniert PlayerScreen-Registrierung, Foreground-Service und ExoPlayer-Stop.
 */
object PlayerPlaybackController {

    private val lock = Any()

    @Volatile
    private var registeredScreens = 0

    @Volatile
    private var activePlayer: ExoPlayer? = null

    fun registerPlayerScreen() {
        synchronized(lock) { registeredScreens++ }
    }

    fun unregisterPlayerScreen(context: Context) {
        synchronized(lock) {
            registeredScreens = (registeredScreens - 1).coerceAtLeast(0)
            if (registeredScreens == 0) {
                PlaybackForegroundService.stop(context)
            }
        }
    }

    fun startForeground(context: Context, title: String) {
        PlaybackForegroundService.start(context, title)
    }

    fun stop(context: Context) {
        synchronized(lock) { registeredScreens = 0 }
        PlaybackForegroundService.stop(context)
    }

    fun attach(player: ExoPlayer) {
        activePlayer = player
    }

    fun detach(player: ExoPlayer) {
        if (activePlayer === player) {
            activePlayer = null
        }
    }

    fun stopPlayback() {
        val player = activePlayer ?: return
        try {
            player.pause()
            player.stop()
            player.clearMediaItems()
        } catch (_: Exception) {
        }
    }
}
