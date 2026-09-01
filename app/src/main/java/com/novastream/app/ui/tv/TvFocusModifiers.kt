package com.novastream.app.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.novastream.app.ui.theme.TvFocusRing

/**
 * TV-spezifische Modifier Extensions für D-Pad Navigation.
 *
 * Auf Android TV wird die Navigation über D-Pad (Up/Down/Left/Right) gesteuert.
 * Compose's 2D spatial focus engine handled das meiste automatisch, aber wir brauchen:
 * 1. focusable() auf allen interaktiven Elementen
 * 2. focusRestorer() auf LazyRow/LazyColumn für Focus-Wiederherstellung
 * 3. Focus-Scaling für bessere Visibility (10-foot UI) - 10% scale für TV
 */

/**
 * Macht ein Element focusable für D-Pad Navigation.
 * Auf TV wird beim Fokus ein Scale-Effekt angewendet (10% für 10-foot UI).
 */
fun Modifier.tvFocusable(
    focusRequester: FocusRequester? = null,
    scaleOnFocus: Boolean = true,
    scaleAmount: Float = 1.1f
): Modifier = composed {
    var modifier: Modifier = this

    if (focusRequester != null) {
        modifier = modifier.focusRequester(focusRequester)
    }

    if (scaleOnFocus) {
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()
        // Animated scale für smooth focus transitions
        val scale by animateFloatAsState(
            targetValue = if (isFocused) scaleAmount else 1.0f,
            animationSpec = tween(200),
            label = "tvFocusScale"
        )
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

/**
 * Goldener Focus-Ring für TV D-Pad Navigation (10-foot UI).
 * Kombiniert mit [tvFocusable] auf interaktiven Elementen.
 */
fun Modifier.tvFocusRing(
    width: Dp = 3.dp,
    color: Color = TvFocusRing,
    cornerRadius: Dp = 12.dp
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { isFocused = it.isFocused }
        .border(
            width = if (isFocused) width else 0.dp,
            color = if (isFocused) color else Color.Transparent,
            shape = RoundedCornerShape(cornerRadius)
        )
}

/** Wendet TV-Fokus-Modifier nur auf TV-Geräten an. */
@Composable
fun Modifier.tvFocusIfNeeded(
    cornerRadius: Dp = 12.dp,
    focusRequester: FocusRequester? = null
): Modifier {
    val context = LocalContext.current
    val isTv = remember { TvUtils.isTvDevice(context) }
    return if (isTv) {
        tvFocusable(focusRequester = focusRequester).tvFocusRing(cornerRadius = cornerRadius)
    } else {
        focusable()
    }
}
