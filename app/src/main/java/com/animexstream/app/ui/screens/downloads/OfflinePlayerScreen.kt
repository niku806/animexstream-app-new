package com.animexstream.app.ui.screens.downloads

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.animexstream.app.data.DownloadManager
import com.animexstream.app.ui.theme.*
import java.io.File

@Composable
fun OfflinePlayerScreen(taskId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val task = remember(taskId) { DownloadManager.get(taskId) }

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }
    var showSpeed by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) {
        val t = task ?: return@LaunchedEffect
        val v = t.videoPath ?: return@LaunchedEffect
        if (!File(v).exists()) return@LaunchedEffect

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val p = ExoPlayer.Builder(context).build()
        val ds = DefaultDataSource.Factory(context)

        val video = ProgressiveMediaSource.Factory(ds)
            .createMediaSource(MediaItem.fromUri(v))

        val audioPath = t.audioPath
        if (audioPath != null && File(audioPath).exists()) {
            val audio = ProgressiveMediaSource.Factory(ds)
                .createMediaSource(MediaItem.fromUri(audioPath))
            p.setMediaSource(MergingMediaSource(video, audio))
        } else {
            p.setMediaSource(video)
        }
        p.playbackParameters = PlaybackParameters(speed)
        p.prepare()
        p.playWhenReady = true
        player = p
    }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    LaunchedEffect(speed) { player?.playbackParameters = PlaybackParameters(speed) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val p = player
        if (p != null) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        val pv = PlayerView(ctx).apply {
                            player = p
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        addView(pv, FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("File not found", color = Color.White)
            }
        }

        Box(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x88000000))
                .clickable { onBack() }.padding(8.dp)
        ) { Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.White) }

        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x88000000))
                .clickable { showSpeed = true }.padding(10.dp)
        ) {
            Text(if (speed == 1f) "1x" else "${speed}x",
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (showSpeed) {
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            confirmButton = { TextButton(onClick = { showSpeed = false }) { Text("Close", color = PurpleLight) } },
            title = { Text("Speed", color = TextPrimary) },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clickable { speed = s; showSpeed = false }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (s == 1f) "Normal" else "${s}x",
                                color = if (s == speed) PurpleLight else TextPrimary,
                                fontSize = 14.sp, modifier = Modifier.weight(1f))
                            if (s == speed) Icon(Icons.Default.Check, null, tint = PurpleLight, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            },
            containerColor = BgCard
        )
    }
}
