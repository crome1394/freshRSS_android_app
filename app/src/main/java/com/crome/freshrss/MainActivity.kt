package com.crome.freshrss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crome.freshrss.ui.FreshRssNav
import com.crome.freshrss.ui.theme.AppThemeMode
import com.crome.freshrss.ui.theme.FreshRssTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application.freshRssApp
        setContent {
            val themeMode by app.settings.themeMode.collectAsStateWithLifecycle(
                initialValue = AppThemeMode.SYSTEM,
            )
            FreshRssTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FreshRssNav(
                        client = app.client,
                        settings = app.settings,
                    )
                }
            }
        }
    }
}
