package com.animexstream.app.ui.screens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.animexstream.app.data.*
import com.animexstream.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    animeId: String,
    episodeNumber: Int,
    onBack: () -> Unit,
    onNextEpisode: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity

    var detail by remember { mutableStateOf<AnimeDetail?>(null) }
    var resolvedServers by remember { mutableStateOf<List<ResolvedServer>>(emptyList()) }
    var loadingServers by remember { mutableStateOf(true) }
    var selectedServerIdx by remember { mutableIntStateOf(0) }
    var selectedQualityIdx by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(1f) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showServerSheet by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var localVideoPath by remember { mutableStateOf<String?>(null) }
    var localAudioPath by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var lastPos by remember { mutableLongStateOf(0L) }
    var lastPlay by remember { mutableStateOf(true) }

    LaunchedEffect(animeId) { detail = com.animexstream.app.data.ContentRepository.loadDetail(animeId) }

    val d = detail
    val currentEp: Episode? = d?.episodes?.firstOrNull { it.number == episodeNumber }

    LaunchedEffect(d, currentEp) {
        if (d != null && currentEp != null) UserPrefs.saveContinue(d.slug, d.title, d.image, currentEp.number)
    }

    // Check existing download for offline playback
    LaunchedEffect(d, currentEp) {
        val existing = DownloadManager.tasks.value.firstOrNull {
            it.animeId == animeId && it.episodeNum == episodeNumber && it.status == DownloadStatus.COMPLETED
        }
        if (existing?.videoPath != null && File(existing.videoPath).exists()) {
            localVideoPath = existing.videoPath
            localAudioPath = existing.audioPath
        } else {
            localVideoPath = null
            localAudioPath = null
        }
    }

    // Resolve servers
    LaunchedEffect(currentEp) {
        if (currentEp == null) return@LaunchedEffect
        loadingServers = true
        selectedServerIdx = 0
        selectedQualityIdx = 0
        playerError = null
        resolvedServers = com.animexstream.app.data.ContentRepository.resolveServers(currentEp)
        loadingServers = false
    }

    // Fullscreen mode
    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            act.requestedOrientation = if (isFullscreen)
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val w = act.window
            if (isFullscreen) {
                w.decorView.windowInsetsController?.let { c ->
                    c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    c.hide(WindowInsets.Type.systemBars())
                }
            } else {
                w.decorView.windowInsetsController?.show(WindowInsets.Type.systemBars())
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

    // Active URL — local file takes priority (offline playback)
    val activeVariant: QualityStream? = resolvedServers.getOrNull(selectedServerIdx)?.variants?.getOrNull(selectedQualityIdx)
    val playUrl: String? = localVideoPath ?: activeVariant?.url

    LaunchedEffect(exoPlayer) {
        while (true) {
            exoPlayer?.let { lastPos = it.currentPosition; lastPlay = it.playWhenReady }
            kotlinx.coroutines.delay(500)
        }
    }

    // ============ ONE-TIME Player creation ============
    // Same player instance reuse hote hain — sirf media source badalte hain
    // isliye quality/server switch SMOOTH hota hai (instant continue)
    LaunchedEffect(Unit) {
        try {
            val p = ExoPlayer.Builder(context).build()
            p.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playerError = error.message ?: "Playback error"
                    isLoading = false
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) isLoading = false
                }
            })
            exoPlayer = p
        } catch (e: Exception) {
            playerError = e.message
        }
    }

    // ============ Source switch on URL change (fast, no player rebuild) ============
    LaunchedEffect(playUrl, localAudioPath, exoPlayer) {
        val p = exoPlayer ?: return@LaunchedEffect
        val resumePos = lastPos
        val resumePlay = lastPlay

        if (playUrl.isNullOrBlank()) { isLoading = false; return@LaunchedEffect }

        isLoading = true
        try {
            val httpDs = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .setAllowCrossProtocolRedirects(true)
            // Apply per-source headers (Referer/Cookie) that MovieBox streams need.
            val srcHeaders = activeVariant?.headers ?: emptyMap()
            if (srcHeaders.isNotEmpty()) httpDs.setDefaultRequestProperties(srcHeaders)
            val dataSourceFactory = DefaultDataSource.Factory(context, httpDs)

            val item = MediaItem.fromUri(playUrl)
            // Attach subtitle tracks (SRT/VTT) from the episode.
            val subs = currentEp?.subtitles ?: emptyList()
            val builtItem = item.buildUpon()
                .setSubtitleConfigurations(
                    subs.mapIndexed { k, sub ->
                        val m = if (sub.url.contains(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                            .setMimeType(m)
                            .setLanguage(sub.language.ifBlank { "und" })
                            .setLabel(sub.label)
                            .setSelectionFlags(if (k == 0) androidx.media3.common.C.SELECTION_FLAG_DEFAULT else 0)
                            .build()
                    }
                )
                .build()

            val source = when {
                playUrl.contains(".m3u8", true) -> HlsMediaSource.Factory(httpDs).createMediaSource(builtItem)
                else -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(builtItem)
            }

            if (localVideoPath != null && localAudioPath != null) {
                val audioItem = MediaItem.fromUri(localAudioPath!!)
                val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(audioItem)
                p.setMediaSource(MergingMediaSource(source, audioSource))
            } else {
                p.setMediaSource(source)
            }

            p.playbackParameters = PlaybackParameters(speed)
            p.seekTo(resumePos)
            p.playWhenReady = resumePlay
            p.prepare()
            playerError = null
        } catch (e: Exception) {
            playerError = e.message
            isLoading = false
        }
    }

    LaunchedEffect(speed) { exoPlayer?.playbackParameters = PlaybackParameters(speed) }

    // ================== RENDER ==================
    if (isFullscreen) {
        FullscreenPlayer(
            exoPlayer = exoPlayer,
            isLoading = isLoading,
            playerError = playerError,
            servers = resolvedServers,
            selectedServerIdx = selectedServerIdx,
            selectedQualityIdx = selectedQualityIdx,
            onBack = { isFullscreen = false },
            onServerChange = { idx -> selectedServerIdx = idx; selectedQualityIdx = 0 },
            onQualityChange = { idx -> selectedQualityIdx = idx },
            onSettingsClick = { showSettings = true }
        )
    } else {
    Column(Modifier.fillMaxSize().background(BgDark).verticalScroll(rememberScrollState())) {
        // ============ VIDEO (glass style) ============
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            if (exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            val pv = PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                            addView(pv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (loadingServers) "Loading servers…" else "Preparing player…", color = TextSecondary, fontSize = 12.sp)
                }
            }

            // Top glass bar
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(Icons.Default.KeyboardArrowLeft) { exoPlayer?.pause(); onBack() }
                Spacer(Modifier.weight(1f))
                GlassIconButton(Icons.Default.Info) { }
                Spacer(Modifier.width(6.dp))
                GlassIconButton(Icons.Default.Settings) { showSettings = true }
                Spacer(Modifier.width(6.dp))
                GlassIconButton(Icons.Default.Fullscreen) { isFullscreen = true }
            }


        }

        if (playerError != null) {
            Row(Modifier.fillMaxWidth().background(HeartRed.copy(alpha = 0.12f)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Icon(Icons.Default.Info, null, tint = HeartRed, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(playerError!!, color = HeartRed, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(14.dp))
            Text(d?.title ?: animeId, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("S${currentEp?.season ?: 1} E${episodeNumber} • ${currentEp?.title ?: ""}", color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(14.dp))

            // Download button
            val alreadyDl = DownloadManager.tasks.value.any {
                it.animeId == animeId && it.episodeNum == episodeNumber && it.status == DownloadStatus.COMPLETED
            }
            Button(
                onClick = { if (!alreadyDl) showDownloadSheet = true },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = if (alreadyDl) BgCard else MaterialTheme.colorScheme.primary)
            ) {
                Icon(if (alreadyDl) Icons.Default.Check else Icons.Default.KeyboardArrowDown, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (alreadyDl) "Downloaded" else "Download Episode", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(18.dp))
            Text("Next Episode", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            val nextEp = d?.episodes?.firstOrNull { it.number == episodeNumber + 1 }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BgCard)
                    .clickable { onNextEpisode(episodeNumber + 1) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("S1 E${episodeNumber + 1}", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(nextEp?.title?.ifBlank { "Episode ${episodeNumber + 1}" } ?: "Coming soon", color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.secondary)
            }

            Spacer(Modifier.height(18.dp))
            Text("Episode List", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (d?.episodes ?: emptyList()).forEach { ep ->
                    val isCurrent = ep.number == episodeNumber
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else BgCard)
                            .clickable { if (!isCurrent) onNextEpisode(ep.number) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.width(104.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(BgCardLight),
                            contentAlignment = Alignment.Center
                        ) {
                            if (ep.image.isNotBlank()) androidx.compose.foundation.Image(painter = coil.compose.rememberAsyncImagePainter(ep.image), contentDescription = ep.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Icon(Icons.Default.PlayArrow, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
                            if (isCurrent) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Episode ${ep.number}", color = if (isCurrent) MaterialTheme.colorScheme.secondary else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(2.dp))
                            Text(ep.title.ifBlank { "Episode ${ep.number}" }, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (isCurrent) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        else Icon(Icons.Default.PlayArrow, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
    }

    // ========== SETTINGS SHEET (glass) ==========
    if (showSettings) {
        GlassBottomSheet(onDismiss = { showSettings = false }) {
            Text("Settings", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            GlassSettingsRow(Icons.Default.List, "Server", resolvedServers.getOrNull(selectedServerIdx)?.name ?: "—") {
                showSettings = false; showServerSheet = true
            }
            GlassSettingsRow(Icons.Default.Star, "Quality", activeVariant?.quality ?: "—") {
                showSettings = false; showQualitySheet = true
            }
            GlassSettingsRow(Icons.Default.PlayArrow, "Speed", if (speed == 1f) "Normal" else "${speed}x") {
                showSettings = false; showSpeedSheet = true
            }
        }
    }

    // Server sheet — only 3 servers shown
    if (showServerSheet) {
        GlassBottomSheet(onDismiss = { showServerSheet = false }) {
            Text("Servers", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (loadingServers) {
                Row(Modifier.padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.secondary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp)); Text("Resolving…", color = TextSecondary)
                }
            } else if (resolvedServers.isEmpty()) {
                Text("No servers available", color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    itemsIndexed(resolvedServers) { i, rs ->
                        GlassRow(
                            selected = i == selectedServerIdx,
                            onClick = {
                                selectedServerIdx = i
                                selectedQualityIdx = 0
                                showServerSheet = false
                            }
                        ) {
                            Text(rs.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("${rs.variants.size} qualities", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // Quality sheet — qualities of the selected server
    if (showQualitySheet) {
        val variants = resolvedServers.getOrNull(selectedServerIdx)?.variants ?: emptyList()
        GlassBottomSheet(onDismiss = { showQualitySheet = false }) {
            Text("Quality", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (variants.isEmpty()) Text("No qualities available", color = TextSecondary)
            else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                itemsIndexed(variants) { i, q ->
                    GlassRow(
                        selected = i == selectedQualityIdx,
                        onClick = {
                            selectedQualityIdx = i
                            showQualitySheet = false
                        }
                    ) {
                        Text(q.quality, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        if (q.directDownloadUrl != null) Text("Direct", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }

    if (showSpeedSheet) {
        GlassBottomSheet(onDismiss = { showSpeedSheet = false }) {
            Text("Speed", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            speeds.forEach { s ->
                GlassRow(
                    selected = s == speed,
                    onClick = { speed = s; showSpeedSheet = false }
                ) {
                    Text(if (s == 1f) "Normal" else "${if (s == s.toInt().toFloat()) s.toInt() else s}x", color = TextPrimary, fontSize = 14.sp)
                }
            }
        }
    }

    // Download sheet — pick server + quality
    if (showDownloadSheet) {
        var pickServer by remember { mutableIntStateOf(0) }
        var pickQuality by remember { mutableIntStateOf(0) }
        val pickVariants = resolvedServers.getOrNull(pickServer)?.variants ?: emptyList()
        GlassBottomSheet(onDismiss = { showDownloadSheet = false }) {
            Text("Download", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("Server", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            resolvedServers.forEachIndexed { i, rs ->
                GlassRow(
                    selected = i == pickServer,
                    onClick = { pickServer = i; pickQuality = 0 }
                ) {
                    Text(rs.name, color = TextPrimary, fontSize = 13.sp)
                }
            }
            if (pickVariants.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Quality", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                pickVariants.forEachIndexed { i, q ->
                    GlassRow(
                        selected = i == pickQuality,
                        onClick = { pickQuality = i }
                    ) {
                        Text(q.quality, color = TextPrimary, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val rs = resolvedServers.getOrNull(pickServer) ?: return@Button
                    val q = rs.variants.getOrNull(pickQuality) ?: return@Button
                    if (d != null) {
                        DownloadManager.enqueue(context, rs, q, animeId, d.title, episodeNumber, d.image)
                        showDownloadSheet = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Start Download", color = TextPrimary, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ================== COMPONENTS ==================
@Composable
private fun GlassIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0x7AFFFFFF), Color(0x1FFFFFFF))))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassBottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E0A1A),
        dragHandle = { Box(Modifier.padding(vertical = 8.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            content()
        }
    }
}

@Composable
private fun GlassRow(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Color(0x14FFFFFF))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) { content() }
        if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun GlassSettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(Color(0x14FFFFFF)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(value, color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Default.KeyboardArrowRight, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun FullscreenPlayer(
    exoPlayer: ExoPlayer?,
    isLoading: Boolean,
    playerError: String?,
    servers: List<ResolvedServer>,
    selectedServerIdx: Int,
    selectedQualityIdx: Int,
    onBack: () -> Unit,
    onServerChange: (Int) -> Unit,
    onQualityChange: (Int) -> Unit,
    onSettingsClick: () -> Unit
) {
    var serverPopup by remember { mutableStateOf(false) }
    var qualityPopup by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        val pv = PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        addView(pv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // Top-left: back + server selector
        Row(Modifier.align(Alignment.TopStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowLeft, null, tint = Color.White) }
            Box {
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xCC000000), Color(0x88000000))))
                        .clickable { serverPopup = !serverPopup }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.List, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(servers.getOrNull(selectedServerIdx)?.name ?: "Server", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                if (serverPopup) {
                    LazyColumn(
                        Modifier.padding(top = 46.dp).width(220.dp).heightIn(max = 280.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color(0xEE0E0A1A))
                    ) {
                        itemsIndexed(servers) { i, rs ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onServerChange(i); serverPopup = false }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(rs.name, color = if (i == selectedServerIdx) MaterialTheme.colorScheme.secondary else Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (i == selectedServerIdx) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // Quality chip
            val variants = servers.getOrNull(selectedServerIdx)?.variants ?: emptyList()
            if (variants.isNotEmpty()) {
                Box {
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xCC000000), Color(0x88000000))))
                            .clickable { qualityPopup = !qualityPopup }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(variants.getOrNull(selectedQualityIdx)?.quality ?: "Auto", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (qualityPopup) {
                        LazyColumn(
                            Modifier.padding(top = 46.dp).width(140.dp).heightIn(max = 280.dp)
                                .clip(RoundedCornerShape(12.dp)).background(Color(0xEE0E0A1A))
                        ) {
                            itemsIndexed(variants) { i, q ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { onQualityChange(i); qualityPopup = false }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(q.quality, color = if (i == selectedQualityIdx) MaterialTheme.colorScheme.secondary else Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    if (i == selectedQualityIdx) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, null, tint = Color.White) }
        }

        if (isLoading) {
            Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        if (playerError != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(20.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(10.dp)) {
                Text(playerError, color = Color.White, fontSize = 11.sp)
            }
        }
    }
}
