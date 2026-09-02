package com.novastream.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastream.app.data.prefs.AppSettings

data class PosterImageDimensions(val width: Int, val height: Int)

@Composable
fun rememberPosterImageDimensions(): PosterImageDimensions {
    val context = LocalContext.current
    val appSettings = remember(context) { AppSettings(context) }
    val dataSaver by appSettings.dataSaverMode.collectAsStateWithLifecycle(initialValue = false)
    val performanceMode by appSettings.performanceMode.collectAsStateWithLifecycle(initialValue = false)
    return remember(dataSaver, performanceMode) {
        when {
            dataSaver -> PosterImageDimensions(240, 360)
            performanceMode -> PosterImageDimensions(320, 480)
            else -> PosterImageDimensions(400, 600)
        }
    }
}
