package com.animexstream.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animexstream.app.data.Anime
import com.animexstream.app.data.ContentRepository
import com.animexstream.app.ui.theme.LocalSelectedProvider
import com.animexstream.app.ui.components.*
import com.animexstream.app.ui.theme.*

@Composable
fun SearchScreen(onAnimeClick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var all by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    val selectedProvider = LocalSelectedProvider.current
    LaunchedEffect(selectedProvider) {
        loading = true
        all = runCatching { ContentRepository.loadIndex() }.getOrDefault(emptyList())
        loading = false
    }

    var remote by remember { mutableStateOf<List<Anime>?>(null) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.isBlank()) { remote = null; searching = false; return@LaunchedEffect }
        searching = true
        kotlinx.coroutines.delay(350)
        remote = ContentRepository.search(query)
        searching = false
    }

    val results = remember(query, all, remote) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.trim().lowercase()
            val local = all.filter { a -> a.title.lowercase().contains(q) || a.genres.any { g -> g.lowercase().contains(q) } }
            // Prefer provider search results (needed for AniList-backed providers),
            // fall back to the local index when the provider returns nothing.
            (remote ?: local).take(60).ifEmpty { local.take(60) }
        }
    }

    Column(Modifier.fillMaxSize().background(BgDark).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Search", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
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
                    placeholder = { Text("Search anime, movies, sports...", color = TextTertiary, fontSize = 14.sp) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = PurpleLight
                    )
                )
                Icon(Icons.Default.Person, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (query.isBlank()) {
                Text("Trending Searches", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                val trend = listOf("Solo Leveling", "One Piece", "Jujutsu Kaisen", "Demon Slayer", "Naruto", "Attack on Titan")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    trend.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { s ->
                                Box(Modifier.clip(RoundedCornerShape(50)).background(BgCardLight)
                                    .clickable { query = s }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(s, color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("Popular Anime", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                if (loading) Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PurplePrimary) }
                all.take(10).forEach { a ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onAnimeClick(a.id) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        coil.compose.AsyncImage(
                            model = a.poster, contentDescription = a.title,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(BgCard)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            StarRating(a.rating)
                        }
                        Icon(Icons.Default.KeyboardArrowRight, null, tint = TextSecondary)
                    }
                }
            } else {
                Text("Results for \"$query\"", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                if (results.isEmpty()) Text("No matches found", color = TextSecondary, fontSize = 13.sp)
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.heightIn(max = 2000.dp)
                ) {
                    items(results) { a -> AnimePosterCard(a, width = 200.dp, onClick = { onAnimeClick(a.id) }) }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
