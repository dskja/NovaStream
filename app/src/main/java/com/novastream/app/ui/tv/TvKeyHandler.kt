package com.novastream.app.ui.tv

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * D-Pad Key Event Handler für TV Remote Controls.
 *
 * Auf Android TV werden folgende Keys gesendet:
 * - DPad Up/Down/Left/Right: Navigation
 * - DPad Center/Enter: Select/Play-Pause
 * - MediaPlay: Play
 * - MediaPause: Pause
 * - MediaStop: Stop/Exit
 * - MediaRewind: Seek backward
 * - MediaFastForward: Seek forward
 *
 * Diese Helper machen die Player Controls D-Pad-kompatibel.
 */

/**
 * Modifier der D-Pad Key Events abfängt und an Callbacks weiterleitet.
 * Für Player-Specific Controls (Seek, Play/Pause, etc.)
 */
fun Modifier.tvPlayerKeyHandler(
    onPlayPause: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onSelect: () -> Unit = {},
    onBack: () -> Unit = {}
): Modifier = this.onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

    when (event.key) {
        Key.DirectionCenter, Key.Enter -> {
            onSelect()
            onPlayPause()
            true
        }
        Key.MediaPlay -> {
            onPlayPause()
            true
        }
        Key.MediaPause -> {
            onPlayPause()
            true
        }
        Key.MediaPlayPause -> {
            onPlayPause()
            true
        }
        Key.MediaFastForward -> {
            onSeekForward()
            true
        }
        Key.MediaRewind -> {
            onSeekBackward()
            true
        }
        Key.DirectionRight -> {
            onSeekForward()
            false // Lass Compose auch normal navigieren
        }
        Key.DirectionLeft -> {
            onSeekBackward()
            false
        }
        Key.Back, Key.Escape -> {
            onBack()
            false // Lass System Back Handler laufen
        }
        else -> false
    }
}

/**
 * Modifier der ein Element focusable macht und D-Pad Select Events abfängt.
 * Für generische UI Elemente (Cards, Buttons, etc.)
 */
fun Modifier.tvSelectable(
    onSelect: () -> Unit = {},
    focusRequester: FocusRequester? = null
): Modifier = this.then(
    if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }
).onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
        onSelect()
        true
    } else {
        false
    }
}.focusable()
