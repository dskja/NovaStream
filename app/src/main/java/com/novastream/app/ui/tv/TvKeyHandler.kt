package com.novastream.app.ui.tv

import androidx.compose.foundation.focusable
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
 * Unterstützt BOTH Android TV und Amazon Fire TV:
 *
 * Android TV Remote:
 * - DPad Up/Down/Left/Right: Navigation
 * - DPad Center/Enter: Select/Play-Pause
 * - MediaPlay/MediaPause/MediaPlayPause: Play/Pause
 * - MediaFastForward/MediaRewind: Seek
 *
 * Amazon Fire TV Remote (zusätzlich):
 * - KEYCODE_MEDIA_PLAY_PAUSE (179): Play/Pause Toggle (Hauptbutton auf Fire Remote)
 * - KEYCODE_MEDIA_REWIND (227): Seek backward
 * - KEYCODE_MEDIA_FAST_FORWARD (228): Seek forward
 * - KEYCODE_MENU: Context Menu / Settings
 * - KEYCODE_MEDIA_NEXT: Next episode
 * - KEYCODE_MEDIA_PREVIOUS: Previous episode
 * - DPad Left/Right: Auch Seek im Player (Amazon Guideline)
 *
 * Fire TV Remote hat oft nur Play/Pause (kein separater Play/Pause Button).
 * Play/Pause, Rewind, FF sind Toggle-Buttons - nicht alle Remotes haben sie.
 * Daher D-Pad als Fallback für Seek verwenden.
 */

/**
 * Modifier der D-Pad Key Events abfängt und an Callbacks weiterleitet.
 * Für Player-Specific Controls (Seek, Play/Pause, etc.)
 *
 * Unterstützt Fire TV + Android TV Remote Keys.
 */
fun Modifier.tvPlayerKeyHandler(
    onPlayPause: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onSelect: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onMenu: () -> Unit = {},
    onBack: () -> Unit = {}
): Modifier = this.onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

    when (event.key) {
        // Select / Play-Pause (Fire TV D-Pad Center = Select)
        Key.DirectionCenter, Key.Enter -> {
            onSelect()
            onPlayPause()
            true
        }
        // Fire TV: Play/Pause Button (KEYCODE_MEDIA_PLAY_PAUSE = 179)
        Key.MediaPlay, Key.MediaPause, Key.MediaPlayPause -> {
            onPlayPause()
            true
        }
        // Fire TV: Fast Forward Button (KEYCODE_MEDIA_FAST_FORWARD = 228)
        Key.MediaFastForward -> {
            onSeekForward()
            true
        }
        // Fire TV: Rewind Button (KEYCODE_MEDIA_REWIND = 227)
        Key.MediaRewind -> {
            onSeekBackward()
            true
        }
        // Fire TV: Next/Previous (für Episode Navigation)
        Key.MediaNext -> {
            onNext()
            true
        }
        Key.MediaPrevious -> {
            onPrevious()
            true
        }
        // Fire TV: D-Pad Left/Right = Seek im Player (Amazon Guideline)
        Key.DirectionRight -> {
            onSeekForward()
            false // Lass Compose auch normal navigieren
        }
        Key.DirectionLeft -> {
            onSeekBackward()
            false
        }
        // Fire TV: Menu Button (KEYCODE_MENU)
        Key.Menu -> {
            onMenu()
            true
        }
        // Fire TV: Media Stop = Exit Player
        Key.MediaStop -> {
            onBack()
            true
        }
        // Back / Escape
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
 *
 * Unterstützt Fire TV + Android TV D-Pad Center und Enter.
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
