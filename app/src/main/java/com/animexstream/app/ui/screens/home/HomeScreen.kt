package com.animexstream.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.animexstream.app.data.ContentRepository
import com.animexstream.app.data.ProviderPrefs
import com.animexstream.app.data.UserPrefs
import com.animexstream.app.ui.components.*
import com.animexstream.app.ui.theme.*
import kotlinx.coroutines.delay
import org.json.JSONObject

private val mainCategories = listOf(
    "Home" to "home",
    "Anime" to "anime_tab",
    "Movies" to "movies",
    "Live TV" to "sports",
    "Sports" to "sports"
)

@Composable
fun HomeScreen(
    onAnimeClick: (String) -> Unit,
    onPlayClick: (String, Int) -> Unit,
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onOpenAnimeTab: () -> Unit = {},
    onCategoryClick: (String) -> Unit = {}
) {
    var allAnime by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedGenre by remember { mutableStateOf("All") }
    var continueItems by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    val scroll = rememberScrollState()

    LaunchedEffect(Unit) {
        loading = true
        allAnime = ContentRepository.loadIndex()
        continueItems = UserPrefs.continueList()
        loading = false
    }

    LaunchedEffect(Unit) {
        while (true) { delay(2000); continueItems = UserPrefs.continueList() }
    }

    val heroItems = remember(allAnime) { allAnime.filter { it.poster.isNotBlank() }.take(6) }
    val genres = remember(allAnime) {
        val counts = HashMap<String, Int>()
        allAnime.forEach { a -> a.genres.forEach { g -> counts[g] = (counts[g] ?: 0) + 1 } }
        listOf("All") + counts.entries.sortedByDescending { it.value }.map { it.key }.take(12)
    }
    val filtered = remember(allAnime, selectedGenre) {
        if (selectedGenre == "All") allAnime
        else allAnime.filter { it.genres.any { g -> g.equals(selectedGenre, true) } }
    }

    Column(
        Modifier.fillMaxSize().background(BgDark).verticalScroll(scroll)
            .statusBarsPadding().padding(bottom = 16.dp)
    ) {
        HomeHeader(onSearchClick = onSearchClick, onMenuClick = onMenuClick, onSettingsClick = onSettingsClick)

        Spacer(Modifier.height(8.dp))

        // ============ MAIN CATEGORY SLIDESHOW ON TOP ============
        MainCategoryRow(onSelect = { route ->
            if (route == "home") return@MainCategoryRow
            onCategoryClick(route)
        })

        Spacer(Modifier.height(14.dp))

        if (heroItems.isNotEmpty()) {
            HeroSlideshow(heroItems) { id -> onAnimeClick(id) }
            Spacer(Modifier.height(14.dp))
        }

        // Genre text row (filters)
        GenreTextRow(genres = genres, selected = selectedGenre) { selectedGenre = it }
        Spacer(Modifier.height(18.dp))

        if (continueItems.isNotEmpty()) {
            SectionHeader("Continue Watching", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(continueItems.size) { i ->
                    val c = continueItems[i]
                    ContinueCard(
                        title = c.optString("title"),
                        poster = c.optString("poster"),
                        ep = c.optInt("ep", 1)
                    ) { onPlayClick(c.optString("id"), c.optInt("ep", 1)) }
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PurplePrimary)
            }
        } else {
            SectionHeader(
                if (selectedGenre == "All") "Popular on " + ProviderPrefs.selected().displayName else selectedGenre,
                Modifier.padding(horizontal = 16.dp), onSeeAll = onOpenAnimeTab
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(minOf(filtered.size, 20)) { i ->
                    AnimePosterCard(filtered[i], width = 120.dp, onClick = { onAnimeClick(filtered[i].id) })
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("New Releases", Modifier.padding(horizontal = 16.dp), onSeeAll = onOpenAnimeTab)
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(minOf(filtered.size, 20)) { i ->
                    val a = filtered[filtered.size - 1 - i]
                    AnimePosterCard(a, width = 120.dp, onClick = { onAnimeClick(a.id) })
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("Top Rated", Modifier.padding(horizontal = 16.dp), onSeeAll = onOpenAnimeTab)
            Spacer(Modifier.height(12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val sorted = filtered.sortedByDescending { it.rating }
                items(minOf(sorted.size, 20)) { i ->
                    AnimePosterCard(sorted[i], width = 120.dp, onClick = { onAnimeClick(sorted[i].id) })
                }
            }

            // ===== Streaming-platform rows (Netflix / Disney+ / Prime / Crunchyroll) =====
            val platforms = listOf(
                Triple("Netflix", AccentPink, listOf("Action", "Thriller", "Drama", "Mystery")),
                Triple("Disney+ Hotstar", AccentCyan, listOf("Adventure", "Fantasy", "Family", "Comedy")),
                Triple("Prime Video", AccentOrange, listOf("Sci-Fi", "Supernatural", "Psychological")),
                Triple("Crunchyroll", PurpleBright, listOf("Romance", "Slice of Life", "Music", "Sports"))
            )
            platforms.forEach { (name, color, gens) ->
                val row = filtered.filter { a -> a.genres.any { g -> gens.any { it.equals(g, true) } } }
                    .ifEmpty { filtered.shuffled() }
                    .take(20)
                if (row.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    PlatformHeader(name, color)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(row.size) { i ->
                            AnimePosterCard(row[i], width = 120.dp, onClick = { onAnimeClick(row[i].id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformHeader(name: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp, 18.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(name, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text("Collection", color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HomeHeader(onSearchClick: () -> Unit, onMenuClick: () -> Unit, onSettingsClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HamburgerButton(onClick = onMenuClick)
        Spacer(Modifier.width(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Anime", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("X", color = PurpleLight, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("stream", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        CircleIconButton(Icons.Default.Search, bg = BgCardLight, onClick = onSearchClick)
        Spacer(Modifier.width(6.dp))
        CircleIconButton(Icons.Default.Settings, bg = BgCardLight, onClick = onSettingsClick)
    }
}

@Composable
private fun MainCategoryRow(onSelect: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(mainCategories.size) { idx ->
            val (label, route) = mainCategories[idx]
            val active = route == "home"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(route) }
            ) {
                Text(
                    label,
                    color = if (active) PurpleLight else TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.width(if (active) 22.dp else 0.dp).height(2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (active) PurpleLight else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun HeroSlideshow(items: List<Anime>, onClick: (String) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(pagerState, items.size) {
        if (items.size > 1) {
            while (true) {
                delay(4000)
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
            }
        }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(300.dp)) { page ->
        val anime = items[page]
        Box(
            Modifier.fillMaxSize().padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp)).clickable { onClick(anime.id) }
        ) {
            AsyncImage(
                model = anime.poster, contentDescription = anime.title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(BgCard)
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color.Transparent, Color(0x6605030A), Color(0xF005030A)
            ))))
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Text(anime.title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarRating(anime.rating)
                    Spacer(Modifier.width(10.dp))
                    Text("${anime.year}", color = TextSecondary, fontSize = 11.sp)
                    if (anime.totalEpisodes > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text("${anime.totalEpisodes} eps", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(items.size) { i ->
            val active = pagerState.currentPage == i
            Box(
                Modifier.padding(horizontal = 3.dp)
                    .size(if (active) 18.dp else 6.dp, 6.dp)
                    .clip(CircleShape)
                    .background(if (active) PurpleLight else TextTertiary)
            )
        }
    }
}

@Composable
private fun GenreTextRow(genres: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        items(genres.size) { i ->
            val g = genres[i]
            val sel = g == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelect(g) }) {
                Text(g, color = if (sel) TextPrimary else TextSecondary, fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                if (sel) {
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(20.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(PurpleLight))
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(title: String, poster: String, ep: Int, onClick: () -> Unit) {
    Column(Modifier.width(120.dp).clickable { onClick() }) {
        AsyncImage(
            model = poster, contentDescription = title, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.68f).clip(RoundedCornerShape(14.dp)).background(BgCard)
        )
        Spacer(Modifier.height(6.dp))
        Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Ep $ep", color = PurpleLight, fontSize = 10.sp)
    }
}
