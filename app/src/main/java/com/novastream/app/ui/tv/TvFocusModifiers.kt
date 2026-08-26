package com.novastream.app.ui.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp

/**
 * TV-spezifische Modifier Extensions für D-Pad Navigation.
 *
 * Auf Android TV wird die Navigation über D-Pad (Up/Down/Left/Right) gesteuert.
 * Compose's 2D spatial focus engine handled das meiste automatisch, aber wir brauchen:
 * 1. focusable() auf allen interaktiven Elementen
 * 2. focusRestorer() auf LazyRow/LazyColumn für Focus-Wiederherstellung
 * 3. Focus-Scaling für bessere Visibility (10-foot UI)
 */

/**
 * Macht ein Element focusable für D-Pad Navigation.
 * Auf TV wird beim Fokus ein leichter Scale-Effekt angewendet.
 */
fun Modifier.tvFocusable(
    focusRequester: FocusRequester? = null,
    scaleOnFocus: Boolean = true
): Modifier = composed {
    var modifier: Modifier = this

    if (focusRequester != null) {
        modifier = modifier.focusRequester(focusRequester)
    }

    if (scaleOnFocus) {
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()
        val scale = if (isFocused) 1.05f else 1.0f
        modifier = modifier
            .scale(scale)
            .focusable(interactionSource = interactionSource)
    } else {
        modifier = modifier.focusable()
    }

    modifier
}

/**
 * Focus Restorer für LazyRow - stellt sicher dass beim Vertical-Navigieren
 * zwischen Rows der zuletzt fokussierte Item wiederhergestellt wird.
 */
@androidx.compose.ui.ExperimentalComposeUiApi
fun Modifier.tvFocusRestorer(): Modifier = this.focusRestorer()

/**
 * Initial Focus Helper - requestet Focus auf dem ersten Element beim Screen-Eintritt.
 * Sollte in einem LaunchedEffect aufgerufen werden.
 */
@Composable
fun rememberInitialFocusRequester(): FocusRequester {
    return remember { FocusRequester() }
}
