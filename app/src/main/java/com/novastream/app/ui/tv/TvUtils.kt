package com.novastream.app.ui.tv

import android.content.Context
import android.content.pm.PackageManager

/**
 * Utility zur Erkennung ob die App auf einem Android TV / Google TV / Fire TV läuft.
 *
 * Fire TV Besonderheiten:
 * - Fire OS basiert auf AOSP (Android Open Source Project)
 * - Kein Google Play Services (Amazon Appstore statt Google Play)
 * - Fire OS 5 = API 22, Fire OS 6 = API 25, Fire OS 7 = API 28, Fire OS 8 = API 30
 * - Fire TV Remote: D-Pad + Play/Pause + Rewind + Fast Forward
 * - amazon.firetv feature verfügbar
 */
object TvUtils {

    @Volatile
    private var cachedIsTv: Boolean? = null

    @Volatile
    private var cachedIsFireTv: Boolean? = null

    /** True wenn auf einem TV Gerät (Android TV, Google TV, Fire TV). Ergebnis wird gecacht. */
    fun isTvDevice(context: Context): Boolean {
        cachedIsTv?.let { return it }
        val result = checkIsTvDevice(context)
        cachedIsTv = result
        return result
    }

    private fun checkIsTvDevice(context: Context): Boolean {
        val pm = context.packageManager

        // 1. UI Mode Type Television (Android TV + Fire TV) - safe cast
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE)
        if (uiModeManager is android.app.UiModeManager) {
            if (uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) {
                return true
            }
        }

        // 2. Leanback Feature (Android TV)
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true

        // 3. Fire TV: Amazon Feature Check
        if (isFireTv(context)) return true

        return false
    }

    /** True wenn auf einem Amazon Fire TV Gerät. Ergebnis wird gecacht. */
    fun isFireTv(context: Context): Boolean {
        cachedIsFireTv?.let { return it }
        val result = checkIsFireTv(context)
        cachedIsFireTv = result
        return result
    }

    private fun checkIsFireTv(context: Context): Boolean {
        val pm = context.packageManager

        // Fire TV hat das Feature "amazon.firetv" oder "amazon.hardware.fire_tv"
        if (pm.hasSystemFeature("amazon.firetv")) return true
        if (pm.hasSystemFeature("amazon.hardware.fire_tv")) return true

        // Fallback: Build.MANUFACTURER == "Amazon"
        if (android.os.Build.MANUFACTURER?.equals("Amazon", ignoreCase = true) == true) return true

        // Fallback: Build.MODEL enthält "FireTV" oder "AFT" (Fire TV Modelle: AFTT, AFTS, AFTM, AFTR, etc.)
        val model = android.os.Build.MODEL ?: ""
        if (model.startsWith("AFT", ignoreCase = true) || model.contains("FireTV", ignoreCase = true)) {
            return true
        }

        return false
    }

    /** True wenn auf einem Google TV / Android TV (nicht Fire TV). */
    fun isAndroidTv(context: Context): Boolean {
        return isTvDevice(context) && !isFireTv(context)
    }

    /** D-Pad Navigation: Prüft ob Touchscreen verfügbar ist. */
    fun hasTouchscreen(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    /** Fire OS Version aus Build.VERSION.SDK_INT ableiten. */
    fun getFireOsVersion(): String? {
        if (android.os.Build.MANUFACTURER?.equals("Amazon", ignoreCase = true) == true ||
            android.os.Build.MODEL?.startsWith("AFT") == true) {
            return when (android.os.Build.VERSION.SDK_INT) {
                22 -> "Fire OS 5"
                25 -> "Fire OS 6"
                28 -> "Fire OS 7"
                29, 30 -> "Fire OS 7"
                31, 32 -> "Fire OS 8"
                33, 34 -> "Fire OS 8"
                35, 36 -> "Fire OS 14"
                else -> "Fire OS (API ${android.os.Build.VERSION.SDK_INT})"
            }
        }
        return null
    }

    /** Setzt den Cache zurück (für Tests). */
    fun resetCache() {
        cachedIsTv = null
        cachedIsFireTv = null
    }
}
