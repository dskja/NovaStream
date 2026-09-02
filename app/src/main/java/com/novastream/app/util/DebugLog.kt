package com.novastream.app.util

import android.util.Log
import com.novastream.app.BuildConfig

/** Debug-only logging helper for previously silent catch blocks. */
object DebugLog {
    fun w(tag: String, message: String, error: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (error != null) Log.w(tag, message, error) else Log.w(tag, message)
    }
}
