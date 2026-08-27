package com.novastream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastream.app.ui.navigation.NovaStreamNavHost
import com.novastream.app.ui.theme.NovaStreamTheme
import com.novastream.app.ui.tv.TvUtils
import com.novastream.app.util.VoeWebViewResolver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Use application context for VoeWebViewResolver to avoid Activity memory leaks
        VoeWebViewResolver.setContext(applicationContext)

        // Edge-to-edge nur auf Nicht-TV Geräten (TV hat andere UI Requirements)
        if (!TvUtils.isTvDevice(this)) {
            enableEdgeToEdge()
        }

        // Splash screen sofort entfernen sobald UI bereit ist (verhindert lange Splash Zeit)
        splashScreen.setKeepOnScreenCondition { false }

        setContent {
            val appSettings = remember { com.novastream.app.data.prefs.AppSettings(this) }
            val dynamicColor by appSettings.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
            NovaStreamTheme(useDynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NovaStreamNavHost()
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
        } finally {
            VoeWebViewResolver.clearContext()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Bei niedrigem Memory: VoeWebViewResolver Context kann sicher bleiben
        // aber Coil Image Cache sollte geleert werden
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            try {
                coil.Coil.imageLoader(this).memoryCache?.clear()
            } catch (_: Exception) {}
        }
    }
}
