package com.animexstream.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animexstream.app.data.OnboardingPrefs
import com.animexstream.app.data.Provider
import com.animexstream.app.data.ProviderPrefs
import com.animexstream.app.ui.theme.BgCard
import com.animexstream.app.ui.theme.BgDark
import com.animexstream.app.ui.theme.TextPrimary
import com.animexstream.app.ui.theme.TextSecondary
import com.animexstream.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch

/**
 * Zangetsu-style first-run intro: three swipeable pages (brand → how it works →
 * get going). Purely presentational — no network. "Skip" or "Get Started" marks
 * onboarding done and hands off to the app shell via [onDone].
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val accent = Color(ProviderPrefs.selected().accent)
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    fun finish() { OnboardingPrefs.markDone(); onDone() }

    Box(Modifier.fillMaxSize().background(BgDark)) {
        // Top-right Skip
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.End) {
            Text(
                "Skip", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(BgCard).padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickableNoRipple { finish() }
            )
        }

        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> OnboardPage(
                    icon = Icons.Default.PlayArrow, accent = accent,
                    title = "Welcome to AnimeXstream",
                    body = "Track. Search. Sync. Organize. One app for anime, movies and series — with three content providers built in.",
                )
                1 -> OnboardPage(
                    icon = Icons.Default.List, accent = accent,
                    title = "Pick your provider",
                    body = "AnimeXstream · Animestream · Zangetsu. Switch anytime from Settings → Content Provider. The home feed, artwork and accent adapt to your choice.",
                )
                else -> OnboardPage(
                    icon = Icons.Default.Star, accent = accent,
                    title = "You're all set",
                    body = "Stream instantly, download episodes for offline, and join the community. Tap Get Started to dive in.",
                )
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Page dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    val active = pager.currentPage == i
                    Box(
                        Modifier.height(6.dp)
                            .width(if (active) 22.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (active) accent else TextTertiary)
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            val last = pager.currentPage == 2
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.7f))))
                    .clickableNoRipple {
                        if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (last) "Get Started" else "Next", color = Color.Black.copy(alpha = 0.85f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OnboardPage(icon: ImageVector, accent: Color, title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(120.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(title, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
    }
}

// Lightweight clickable without the ripple indirection.
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
