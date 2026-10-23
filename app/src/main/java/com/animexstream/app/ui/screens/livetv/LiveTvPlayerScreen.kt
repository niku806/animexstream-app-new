package com.animexstream.app.ui.screens.livetv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.animexstream.app.data.LiveChannel
import com.animexstream.app.ui.theme.*

@Composable
fun LiveTvPlayerScreen(channel: LiveChannel, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isFullscreen by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(channel.url) {
        try {
            val p = ExoPlayer.Builder(context).build()
            val httpDs = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 13)")
                .setAllowCrossProtocolRedirects(true)
            val item = MediaItem.fromUri(channel.url)
            p.setMediaSource(HlsMediaSource.Factory(httpDs).createMediaSource(item))
            p.playbackParameters = PlaybackParameters(1f)
            p.prepare()
            p.playWhenReady = true
            exoPlayer = p
        } catch (e: Exception) { error = e.message }
    }

    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            val w = act.window
            w.decorView.windowInsetsController?.let { c ->
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsets.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.decorView?.windowInsetsController?.show(WindowInsets.Type.systemBars())
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val p = exoPlayer
        if (p != null) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        val pv = PlayerView(ctx).apply {
                            player = p; useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        addView(pv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PurplePrimary)
                    Spacer(Modifier.height(10.dp))
                    Text(error ?: "Loading channel…", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        Row(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Color(0x66000000), Color(0x33000000))))
                    .clickable { onBack() }.padding(8.dp)
            ) { Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.White) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(channel.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${channel.quality} • Live", color = Color(0xFFFF3B3B), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
