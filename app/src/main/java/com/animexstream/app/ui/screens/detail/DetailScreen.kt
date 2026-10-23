package com.animexstream.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animexstream.app.data.Anime
import com.animexstream.app.data.AnimeDetail
import com.animexstream.app.data.ContentRepository
import com.animexstream.app.ui.components.AnimePosterCard
import com.animexstream.app.ui.components.CircleIconButton
import com.animexstream.app.ui.components.GenreChip
import com.animexstream.app.ui.components.PlayButton
import com.animexstream.app.ui.components.PurplePillButton
import com.animexstream.app.ui.components.SectionHeader
import com.animexstream.app.ui.theme.*

@Composable
fun DetailScreen(
    animeId: String,
    onBack: () -> Unit,
    onWatchClick: (String, Int) -> Unit,
    onSimilarClick: (String) -> Unit
) {
    var detail by remember { mutableStateOf<AnimeDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedSeason by remember { mutableIntStateOf(1) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var allSimilar by remember { mutableStateOf<List<Anime>>(emptyList()) }
    val scroll = rememberScrollState()

    LaunchedEffect(animeId) {
        loading = true
        val d = ContentRepository.loadDetail(animeId)
        detail = d
        if (d != null) selectedSeason = d.episodes.map { it.season }.distinct().minOrNull() ?: 1
        allSimilar = ContentRepository.loadIndex().filter { it.id != animeId }.shuffled().take(10)
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize().background(BgDark), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PurplePrimary)
        }
        return
    }

    val d = detail
    if (d == null) {
        Box(Modifier.fillMaxSize().background(BgDark), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Not found", color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                PurplePillButton("Go Back", onClick = onBack)
            }
        }
        return
    }

    val seasons = d.episodes.map { it.season }.distinct().sorted()
    val seasonEps = d.episodes.filter { it.season == selectedSeason }

    Column(Modifier.fillMaxSize().background(BgDark).verticalScroll(scroll)) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            AsyncImage(
                model = d.banner.ifBlank { d.image }, contentDescription = d.title,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(BgCard)
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x9905030A), Color.Transparent, BgDark))))
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(Icons.Default.KeyboardArrowRight, onClick = onBack)
                Spacer(Modifier.weight(1f))
                CircleIconButton(Icons.Default.Star)
                Spacer(Modifier.width(8.dp))
                CircleIconButton(Icons.Default.FavoriteBorder)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(14.dp))
            Text(d.title, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, null, tint = GoldStar, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (d.rating > 0) "%.1f".format(d.rating) else "—", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(d.genres.size) { i -> GenreChip(d.genres[i]) }
            }
            Spacer(Modifier.height(10.dp))
            Text("${d.type} • ${d.year} • ${d.episodes.size} eps • ${d.status}", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text(d.synopsis.ifBlank { "No description available." }, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            PurplePillButton(
                text = "Watch Now", icon = Icons.Default.PlayArrow, modifier = Modifier.fillMaxWidth(),
                onClick = { onWatchClick(d.slug, seasonEps.firstOrNull()?.number ?: 1) }
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { }, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, PurplePrimary)
            ) {
                Icon(Icons.Default.Add, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add to Watchlist", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(18.dp))

        val tabs = listOf("Episodes", "Details", "Cast", "Related")
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            tabs.forEachIndexed { i, t ->
                val sel = i == selectedTab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clickable { selectedTab = i }.padding(vertical = 10.dp)
                ) {
                    Text(t, color = if (sel) PurpleLight else TextSecondary, fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(if (sel) 28.dp else 0.dp).height(2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (sel) PurpleLight else Color.Transparent))
                }
            }
        }

        when (selectedTab) {
            0 -> {
                if (seasons.size > 1) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons.size) { i ->
                            val s = seasons[i]
                            val sel = s == selectedSeason
                            Box(Modifier.clip(RoundedCornerShape(50)).background(if (sel) PurplePrimary else BgCard)
                                .clickable { selectedSeason = s }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text("Season $s", color = if (sel) TextPrimary else TextSecondary, fontSize = 12.sp,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    seasonEps.forEach { ep ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { onWatchClick(d.slug, ep.number) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = ep.image, contentDescription = ep.title, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(width = 100.dp, height = 64.dp).clip(RoundedCornerShape(10.dp)).background(BgCard)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${ep.number}. ${ep.title.ifBlank { "Episode ${ep.number}" }}", color = TextPrimary,
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text("24m", color = TextSecondary, fontSize = 11.sp)
                            }
                            PlayButton(size = 32.dp) { onWatchClick(d.slug, ep.number) }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            1 -> InfoPanel("Full details, staff, studio go here.")
            2 -> InfoPanel("Voice cast and characters go here.")
            else -> InfoPanel("Related anime go here.")
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader("You may also like", Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(allSimilar.size) { i ->
                val a = allSimilar[i]
                AnimePosterCard(a, width = 112.dp, onClick = { onSimilarClick(a.id) })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InfoPanel(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextSecondary, fontSize = 13.sp)
    }
}
