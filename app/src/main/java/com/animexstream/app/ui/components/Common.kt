package com.animexstream.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animexstream.app.data.Anime
import com.animexstream.app.ui.theme.*

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        if (onSeeAll != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onSeeAll() }) {
                Text("See All", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun StarRating(rating: Double, votes: String? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Star, null, tint = GoldStar, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text(if (rating > 0) "%.1f".format(rating) else "—", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        if (votes != null) {
            Spacer(Modifier.width(4.dp)); Text(votes, color = TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun AnimePosterCard(anime: Anime, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp = 120.dp, onClick: () -> Unit = {}) {
    Column(modifier.width(width).clickable { onClick() }) {
        AsyncImage(
            model = anime.poster, contentDescription = anime.title, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.68f).clip(RoundedCornerShape(14.dp)).background(BgCard)
        )
        Spacer(Modifier.height(8.dp))
        Text(anime.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        Spacer(Modifier.height(4.dp))
        StarRating(anime.rating)
    }
}

@Composable
fun PlayButton(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 36.dp, onClick: () -> Unit = {}) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(size * 0.65f))
    }
}

@Composable
fun PurplePillButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit = {}) {
    Box(
        modifier.clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
fun GenreChip(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(BgCardLight).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = TextPrimary, bg: Color = BgCardLight, onClick: () -> Unit = {}) {
    Box(modifier.size(38.dp).clip(CircleShape).background(bg).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

/** Attractive 3-line hamburger */
@Composable
fun HamburgerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
            .background(BgCardLight)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(18.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(TextPrimary))
            Box(Modifier.width(14.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(TextSecondary))
            Box(Modifier.width(18.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(TextPrimary))
        }
    }
}

// ---------- Animated floating bottom navigation (animestream style) ----------
data class NavItem(val label: String, val icon: ImageVector, val route: String)

val bottomNavItems = listOf(
    NavItem("Home", Icons.Default.Home, "home"),
    NavItem("Search", Icons.Default.Search, "search"),
    NavItem("Downloads", Icons.Default.KeyboardArrowDown, "downloads"),
    NavItem("Community", Icons.Default.Person, "community"),
    NavItem("Profile", Icons.Default.Person, "profile"),
)

/**
 * Floating pill dock with a smoothly-sliding highlight behind the active item,
 * a spring scale pop on selection and an animated colour wash — matching the
 * animestream navigation feel while keeping the exact same five destinations.
 */
@Composable
fun AppBottomBar(currentRoute: String, onNavigate: (String) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(BgSurface.copy(alpha = 0.96f))
                .border(1.dp, DividerColor, RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentRoute == item.route
                val scale by animateFloatAsState(if (selected) 1.0f else 0.9f, tween(220), label = "scale")
                val tint by animateColorAsState(if (selected) accent else TextTertiary, tween(220), label = "tint")
                val padH by animateDpAsState(if (selected) 16.dp else 12.dp, tween(220), label = "pad")
                Row(
                    Modifier
                        .scale(scale)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { onNavigate(item.route) }
                        .padding(horizontal = padH, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(item.icon, item.label, tint = tint, modifier = Modifier.size(22.dp))
                    if (selected) {
                        Spacer(Modifier.width(6.dp))
                        Text(item.label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize().background(BgDark), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Coming soon", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}
