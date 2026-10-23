package com.animexstream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseConfig.init(applicationContext)
        DownloadManager.init(applicationContext)
        ProviderPrefs.init(applicationContext)
        com.animexstream.app.data.OnboardingPrefs.init(applicationContext)
        enableEdgeToEdge()

        // Hide system navigation bar — immersive full-screen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            // Re-key the whole theme tree when the provider changes so accents
            // and the catalogue both switch live from Settings.
            var providerVersion by mutableStateOf(0)
            AppProviderHost(onProviderChanged = { providerVersion++ }, version = providerVersion)
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

@androidx.compose.runtime.Composable
private fun AppProviderHost(onProviderChanged: () -> Unit, version: Int) {
    androidx.compose.runtime.key(version) {
        AnimeXTheme {
            Surface(modifier = Modifier.fillMaxSize().background(BgDark)) {
                AppNavGraph(onProviderChanged = onProviderChanged)
            }
        }
    }
}
