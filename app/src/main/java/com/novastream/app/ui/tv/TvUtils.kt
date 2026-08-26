package com.novastream.app.ui.tv

import android.content.Context
import android.content.pm.PackageManager

/**
 * Utility zur Erkennung ob die App auf einem Android TV / Google TV läuft.
 */
object TvUtils {

    fun isTvDevice(context: Context): Boolean {
        val pm = context.packageManager
        // uiMode = UI_MODE_TYPE_TELEVISION
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        if (uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        // Leanback feature check
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    /** D-Pad Navigation: Prüft ob Touchscreen verfügbar ist. */
    fun hasTouchscreen(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }
}
