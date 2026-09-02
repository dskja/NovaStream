package com.novastream.app.ui.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

/**
 * Koordiniert PlayerScreen-Registrierung, MediaSession, Foreground-Service und ExoPlayer-Stop.
 */
@UnstableApi
object PlayerPlaybackController {

    private val lock = Any()

    @Volatile
    private var registeredScreens = 0

    @Volatile
    private var activePlayer: ExoPlayer? = null

    @Volatile
    private var mediaSession: MediaSession? = null

    /** Invoked from [android.app.Activity.onUserLeaveHint] to enter PiP when playback is active. */
    @Volatile
    var pipEnterHandler: (() -> Boolean)? = null

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

    fun attach(context: Context, player: ExoPlayer) {
        activePlayer = player
        releaseMediaSession()
        mediaSession = MediaSession.Builder(context.applicationContext, player).build()
    }

    fun detach(player: ExoPlayer) {
        if (activePlayer === player) {
            activePlayer = null
            releaseMediaSession()
            pipEnterHandler = null
        }
    }

    fun mediaSession(): MediaSession? = mediaSession

    fun requestPictureInPicture(): Boolean = pipEnterHandler?.invoke() ?: false

    fun stopPlayback() {
        val player = activePlayer ?: return
        try {
            player.pause()
            player.stop()
            player.clearMediaItems()
        } catch (_: Exception) {
        }
    }

    private fun releaseMediaSession() {
        mediaSession?.release()
        mediaSession = null
    }
}
