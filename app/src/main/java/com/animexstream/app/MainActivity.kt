package com.animexstream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.animexstream.app.data.DownloadManager
import com.animexstream.app.data.ProviderPrefs
import com.animexstream.app.navigation.AppNavGraph
import com.animexstream.app.ui.theme.AnimeXTheme
import com.animexstream.app.ui.theme.BgDark
import com.animexstream.app.ui.theme.LocalSelectedProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseConfig.init(applicationContext)
        DownloadManager.init(applicationContext)
        ProviderPrefs.init(applicationContext)
        com.animexstream.app.data.OnboardingPrefs.init(applicationContext)
        com.animexstream.app.data.SessionStore.init(applicationContext)
        // Rehydrate a persisted login (survives uninstall) before the splash
        // decides where to go, so a returning user lands straight on Home.
        com.animexstream.app.data.SessionStore.restoreIfPresent()
        enableEdgeToEdge()

        // Hide system navigation bar — immersive full-screen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            // Provider lives in a state: changing it recomposes the theme + the
            // data screens IN PLACE. The NavHost is never re-keyed, so the user
            // stays on the screen they were on instead of being bounced back to
            // the splash (which is what made the app look "empty" after a switch).
            var provider by mutableStateOf(ProviderPrefs.selected())
            CompositionLocalProvider(LocalSelectedProvider provides provider) {
                AnimeXTheme {
                    Surface(modifier = Modifier.fillMaxSize().background(BgDark)) {
                        AppNavGraph(
                            onProviderChanged = {
                                provider = ProviderPrefs.selected()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
}
