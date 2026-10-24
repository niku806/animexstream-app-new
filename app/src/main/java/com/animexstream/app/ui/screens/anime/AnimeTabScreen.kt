package com.animexstream.app.ui.screens.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animexstream.app.data.Anime
import com.animexstream.app.data.ContentRepository
import com.animexstream.app.ui.theme.LocalSelectedProvider
import com.animexstream.app.data.UserPrefs
import com.animexstream.app.ui.components.*
import com.animexstream.app.ui.theme.*
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
fun AnimeTabScreen(
    onAnimeClick: (String) -> Unit,
    onBack: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onPlayClick: (String, Int) -> Unit = { _, _ -> }
) {
    var allAnime by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedGenre by remember { mutableStateOf("All") }
    var continueItems by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    val selectedProvider = LocalSelectedProvider.current
    LaunchedEffect(selectedProvider) {
        loading = true
        allAnime = runCatching { ContentRepository.loadIndex() }.getOrDefault(emptyList())
        continueItems = UserPrefs.continueList()
        loading = false
    }

    val heroItems = remember(allAnime) { allAnime.filter { it.poster.isNotBlank() }.take(6) }
    val genres = remember(allAnime) {
        val counts = HashMap<String, Int>()
        allAnime.forEach { a -> a.genres.forEach { g -> counts[g] = (counts[g] ?: 0) + 1 } }
        listOf("All") + counts.entries.sortedByDescending { it.value }.map { it.key }.take(20)
    }
    val filtered = remember(allAnime, selectedGenre) {
        if (selectedGenre == "All") allAnime
        else allAnime.filter { it.genres.any { g -> g.equals(selectedGenre, true) } }
    }

    Column(Modifier.fillMaxSize().background(BgDark).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(Icons.Default.KeyboardArrowRight, bg = BgCardLight, onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Text("Anime", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Default.Search, bg = BgCardLight, onClick = onSearchClick)
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PurplePrimary)
            }
            return@Column
        }

        if (heroItems.isNotEmpty()) {
            AnimeSlideshow(heroItems) { onAnimeClick(it) }
            Spacer(Modifier.height(14.dp))
        }

        // Continue watching (only on Anime tab)
        if (continueItems.isNotEmpty()) {
            SectionHeader("Continue Watching", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(continueItems.size) { i ->
                    val c = continueItems[i]
                    Column(Modifier.width(110.dp).clickable { onPlayClick(c.optString("id"), c.optInt("ep", 1)) }) {
                        AsyncImage(
                            model = c.optString("poster"), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.68f).clip(RoundedCornerShape(12.dp)).background(BgCard)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(c.optString("title"), color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Ep ${c.optInt("ep", 1)}", color = PurpleLight, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        // Genres (below banner)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(genres.size) { i ->
                val g = genres[i]
                val sel = g == selectedGenre
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { selectedGenre = g }) {
                    Text(g, color = if (sel) TextPrimary else TextSecondary, fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    if (sel) {
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.width(20.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(PurpleLight))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered.size) { i ->
                val a = filtered[i]
                Column(Modifier.clickable { onAnimeClick(a.id) }) {
                    AsyncImage(
                        model = a.poster, contentDescription = a.title, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.68f).clip(RoundedCornerShape(12.dp)).background(BgCard)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(a.title, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
                    Spacer(Modifier.height(3.dp))
                    StarRating(a.rating)
                }
            }
        }
    }
}

@Composable
private fun AnimeSlideshow(items: List<Anime>, onClick: (String) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    LaunchedEffect(pagerState, items.size) {
        if (items.size > 1) {
            while (true) {
                delay(4500)
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
            }
        }
    }
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(280.dp)) { page ->
        val a = items[page]
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp).clip(RoundedCornerShape(22.dp)).clickable { onClick(a.id) }) {
            AsyncImage(model = a.poster, contentDescription = a.title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(BgCard))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color.Transparent, Color(0x7705030A), Color(0xF005030A)
            ))))
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Text(a.title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row { StarRating(a.rating); Spacer(Modifier.width(8.dp)); Text("${a.year}", color = TextSecondary, fontSize = 11.sp) }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(items.size) { i ->
            val active = pagerState.currentPage == i
            Box(Modifier.padding(horizontal = 3.dp)
                .size(if (active) 16.dp else 6.dp, 6.dp)
                .clip(CircleShape)
                .background(if (active) PurpleLight else TextTertiary))
        }
    }
}
