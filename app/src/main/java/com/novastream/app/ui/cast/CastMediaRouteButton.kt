package com.novastream.app.ui.cast

import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.R as MediaRouterR
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
            try {
                val themed = ContextThemeWrapper(context, MediaRouterR.style.Theme_MediaRouter)
                MediaRouteButton(themed).apply {
                    CastButtonFactory.setUpMediaRouteButton(context, this)
                }
            } catch (_: Exception) {
                android.view.View(context)
            }
        }
    )
}
