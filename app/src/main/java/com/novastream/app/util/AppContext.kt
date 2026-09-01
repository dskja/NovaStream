package com.novastream.app.util

import android.app.Application
import android.content.Context

/** Application context holder for non-UI layers that need localized resources. */
object AppContext {
    @Volatile
    private var application: Application? = null

    fun init(app: Application) {
        application = app
    }

    fun get(): Context = application ?: error("AppContext not initialized")

    fun getOrNull(): Context? = application
}
