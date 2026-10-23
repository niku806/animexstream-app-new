package com.animexstream.app.ui.screens.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animexstream.app.data.LiveChannel
import com.animexstream.app.data.LiveTvRepository
import com.animexstream.app.ui.theme.*

@Composable
fun LiveTvScreen(onChannelClick: (LiveChannel) -> Unit, onBack: () -> Unit) {
    var channels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        channels = LiveTvRepository.getChannels()
        loading = false
    }

    val filtered = remember(query, channels) {
        if (query.isBlank()) channels
        else channels.filter { it.name.contains(query, true) }
    }

    Column(Modifier.fillMaxSize().background(BgDark).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowRight, null, tint = TextPrimary) }
            Text("Live TV", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${channels.size} channels", color = TextSecondary, fontSize = 11.sp)
        }
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
                .background(BgCard).padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search channels", color = TextTertiary, fontSize = 14.sp) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = PurpleLight
                    )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PurplePrimary) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered) { ch ->
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(BgCard)
                            .clickable { onChannelClick(ch) }.padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(BgCardLight), contentAlignment = Alignment.Center) {
                                if (ch.logoUrl.isNotBlank()) AsyncImage(model = ch.logoUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                                else Icon(Icons.Default.PlayArrow, null, tint = PurpleLight)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ch.name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(ch.quality, color = TextSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
