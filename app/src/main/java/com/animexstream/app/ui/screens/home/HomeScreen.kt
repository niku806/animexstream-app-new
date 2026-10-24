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
import com.animexstream.app.data.MovieBox
import com.animexstream.app.data.Provider
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
    val selectedProvider = com.animexstream.app.ui.theme.LocalSelectedProvider.current
    val isMovieBox = selectedProvider == Provider.MOVIEBOX
    var allAnime by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var mbRails by remember { mutableStateOf<List<Pair<String, List<Anime>>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedGenre by remember { mutableStateOf("All") }
    var continueItems by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var retryTick by remember { mutableStateOf(0) }
    val scroll = rememberScrollState()

    LaunchedEffect(selectedProvider, retryTick) {
        loading = true
        mbRails = emptyList()
        allAnime = emptyList()
        if (isMovieBox) {
            val rails = runCatching { MovieBox.homeRails() }.getOrDefault(emptyList())
            mbRails = rails
            allAnime = rails.flatMap { it.second }.distinctBy { it.id }
            // All mirrors down / API error -> sweep keywords so home is never blank.
            if (allAnime.isEmpty()) {
                val fb = LinkedHashMap<String, Anime>()
                for (kw in listOf("movie", "series", "anime", "hindi", "action", "comedy", "drama")) {
                    runCatching { MovieBox.search(kw) }.getOrDefault(emptyList())
                        .forEach { fb.putIfAbsent(it.id, it) }
                }
                allAnime = fb.values.toList()
            }
        } else {
            allAnime = runCatching { ContentRepository.loadIndex() }.getOrDefault(emptyList())
        }
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

        if (isMovieBox && !loading) {
            // Real MovieBox home rows
            mbRails.forEach { (title, items) ->
                if (items.isNotEmpty()) {
                    SectionHeader(title, Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(10.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(items.size) { i ->
                            AnimePosterCard(items[i], width = 120.dp, onClick = { onAnimeClick(items[i].id) })
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (filtered.isEmpty() && mbRails.isEmpty()) {
            // Nothing loaded — show a retry prompt instead of empty headers.
            Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No content loaded", color = TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    PurplePillButton("Retry", onClick = { retryTick++ })
                }
            }
        } else {
            HomeRow("Popular on " + ProviderPrefs.selected().displayName, filtered, onAnimeClick, onOpenAnimeTab)
            HomeRow("New Releases", filtered.reversed(), onAnimeClick, onOpenAnimeTab)
            HomeRow("Top Rated", filtered.sortedByDescending { it.rating }, onAnimeClick, onOpenAnimeTab)
            HomeRow("Trending Now", filtered.sortedByDescending { it.rating }, onAnimeClick, onOpenAnimeTab)
            HomeRow("Popular This Season", filtered, onAnimeClick, onOpenAnimeTab)
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
            Text("X", color = MaterialTheme.colorScheme.secondary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                    color = if (active) MaterialTheme.colorScheme.secondary else TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.width(if (active) 22.dp else 0.dp).height(2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (active) MaterialTheme.colorScheme.secondary else Color.Transparent)
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
                    .background(if (active) MaterialTheme.colorScheme.secondary else TextTertiary)
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
                    Box(Modifier.width(20.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.secondary))
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
        Text("Ep $ep", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp)
    }
}

@Composable
private fun HomeRow(title: String, items: List<Anime>, onClick: (String) -> Unit, onSeeAll: () -> Unit) {
    val limited = items.take(20)
    if (limited.isEmpty()) return
    Spacer(Modifier.height(24.dp))
    SectionHeader(title, Modifier.padding(horizontal = 16.dp), onSeeAll = onSeeAll)
    Spacer(Modifier.height(12.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(limited.size) { i -> AnimePosterCard(limited[i], width = 120.dp, onClick = { onClick(limited[i].id) }) }
    }
}
