package com.novastream.app.ui.cast

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.novastream.app.cast.CastHelper

/**
 * Standard Chromecast device picker wired to the Cast SDK.
 * Replaces custom cast icons that could not open the route chooser.
 */
@Composable
fun CastMediaRouteButton(
    modifier: Modifier = Modifier,
    castHelper: CastHelper
) {
    if (!castHelper.isAvailable) return
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MediaRouteButton(context).apply {
                CastButtonFactory.setUpMediaRouteButton(context, this)
            }
        },
        update = { /* CastButtonFactory setup is one-time */ }
    )
}
