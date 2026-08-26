package com.novastream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.novastream.app.ui.navigation.SerienStreamNavHost
import com.novastream.app.ui.theme.SerienStreamTheme
import com.novastream.app.util.VoeWebViewResolver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VoeWebViewResolver.setContext(this)
        enableEdgeToEdge()
        setContent {
            SerienStreamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SerienStreamNavHost()
                }
            }
        }
    }
}
