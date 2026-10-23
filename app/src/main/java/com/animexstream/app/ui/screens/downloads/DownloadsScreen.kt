package com.animexstream.app.ui.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animexstream.app.data.ContentRepository
import com.animexstream.app.data.DownloadManager
import com.animexstream.app.data.DownloadStatus
import com.animexstream.app.data.DownloadTask
import com.animexstream.app.ui.theme.*
import java.io.File

@Composable
fun DownloadsScreen(onPlayOffline: (String) -> Unit) {
    val context = LocalContext.current
    val tasks by DownloadManager.tasks.collectAsState()
    var filter by remember { mutableIntStateOf(0) }
    var forYou by remember { mutableStateOf<List<com.animexstream.app.data.Anime>>(emptyList()) }

    LaunchedEffect(Unit) {
        forYou = ContentRepository.loadIndex().shuffled().take(10)
    }

    val filtered = remember(tasks, filter) {
        when (filter) {
            1 -> tasks.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED }
            2 -> tasks.filter { it.status == DownloadStatus.COMPLETED }
            else -> tasks
        }
    }

    Column(Modifier.fillMaxSize().background(BgDark).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Downloads", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            listOf("All", "Downloading", "Completed").forEachIndexed { i, label ->
                val sel = filter == i
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clickable { filter = i }.padding(vertical = 8.dp)
                ) {
                    Text(label, color = if (sel) PurpleLight else TextSecondary, fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(if (sel) 32.dp else 0.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(if (sel) PurpleLight else Color.Transparent))
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = TextTertiary, modifier = Modifier.size(60.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("No downloads yet", color = TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Download an episode from the player", color = TextTertiary, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { task ->
                    DownloadRow(task, onPlay = { onPlayOffline(task.id) })
                }
            }
        }

        if (forYou.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("For You", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(forYou) { a ->
                    Column(Modifier.width(100.dp)) {
                        AsyncImage(model = a.poster, contentDescription = a.title, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.68f).clip(RoundedCornerShape(10.dp)).background(BgCard))
                        Spacer(Modifier.height(4.dp))
                        Text(a.title, color = TextPrimary, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DownloadRow(task: DownloadTask, onPlay: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 54.dp, height = 76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgCardLight)
            ) {
                val model: Any? = task.posterPath?.let { if (File(it).exists()) File(it) else null }
                if (model != null) {
                    AsyncImage(model = model, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, null, tint = PurpleLight, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(task.animeTitle, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("S1 E${task.episodeNum} • ${task.serverName} • ${task.quality}", color = TextSecondary, fontSize = 11.sp)
            }
            when (task.status) {
                DownloadStatus.COMPLETED -> IconButton(onClick = onPlay) { Icon(Icons.Default.PlayArrow, null, tint = PurpleLight, modifier = Modifier.size(28.dp)) }
                DownloadStatus.DOWNLOADING -> IconButton(onClick = { DownloadManager.pause(task.id) }) { Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(20.dp)) }
                DownloadStatus.PAUSED -> IconButton(onClick = { DownloadManager.resume(task.id) }) { Icon(Icons.Default.PlayArrow, null, tint = PurpleLight, modifier = Modifier.size(24.dp)) }
                DownloadStatus.FAILED -> IconButton(onClick = { DownloadManager.resume(task.id) }) { Icon(Icons.Default.Refresh, null, tint = HeartRed, modifier = Modifier.size(22.dp)) }
                else -> {}
            }
            IconButton(onClick = { DownloadManager.delete(task.id) }) { Icon(Icons.Default.Delete, null, tint = TextTertiary, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        when (task.status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                LinearProgressIndicator(progress = { task.progress / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = PurplePrimary, trackColor = BgCardLight)
                Spacer(Modifier.height(6.dp))
                Row {
                    Text("${task.progress}%", color = PurpleLight, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (task.speedBps > 0) Text("${fmtSpeed(task.speedBps)} • ${fmtSize(task.downloadedBytes)}/${fmtSize(task.totalBytes)}", color = TextSecondary, fontSize = 10.sp)
                }
            }
            DownloadStatus.PAUSED -> Text("Paused • ${task.progress}%", color = TextSecondary, fontSize = 11.sp)
            DownloadStatus.COMPLETED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = PurpleLight, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ready to play offline", color = PurpleLight, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(fmtSize(task.totalBytes), color = TextSecondary, fontSize = 10.sp)
            }
            DownloadStatus.FAILED -> Text("Failed: ${task.error ?: "Unknown error"}", color = HeartRed, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun fmtSize(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
    else -> String.format("%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
}

private fun fmtSpeed(bps: Long): String = when {
    bps < 1024 -> "$bps B/s"
    bps < 1024 * 1024 -> "${bps / 1024} KB/s"
    else -> String.format("%.1f MB/s", bps.toDouble() / (1024 * 1024))
}
