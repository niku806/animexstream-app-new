package com.animexstream.app.ui.screens.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animexstream.app.data.AuthRepository
import com.animexstream.app.data.OnboardingPrefs
import com.animexstream.app.data.ProviderPrefs
import com.animexstream.app.ui.theme.BgDark
import com.animexstream.app.ui.theme.TextPrimary
import com.animexstream.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Zangetsu-style cold-start splash: deep black background, a soft radial glow
 * behind the wordmark that eases in, the brand fading + settling up to scale,
 * and a thin indeterminate loader appearing last. Accent follows the selected
 * provider. Navigates to onboarding (first run), login, or home.
 */
@Composable
fun SplashScreen(onNext: (loggedIn: Boolean, onboarded: Boolean) -> Unit) {
    val accent = Color(ProviderPrefs.selected().accent)
    val glow = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val settle = remember { Animatable(0.94f) }
    val loader = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        glow.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(180)
        fade.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        settle.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(700)
        loader.animateTo(1f, tween(400))
    }
    LaunchedEffect(Unit) {
        delay(1900)
        onNext(AuthRepository.isLoggedIn, OnboardingPrefs.isDone())
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    Box(Modifier.fillMaxSize().background(BgDark), contentAlignment = Alignment.Center) {
        // Soft radial glow behind the mark — echoes Zangetsu's coral halo.
        Box(
            Modifier
                .size(460.dp)
                .alpha(glow.value * 0.9f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.22f),
                            accent.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                        center = Offset.Unspecified,
                    ),
                    CircleShape,
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(fade.value).scale(settle.value),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Anime", color = TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                Text("X", color = accent, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
                Text("stream", color = TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            }
            Spacer(Modifier.height(10.dp))
            Text("Track. Search. Sync. Organize.", color = TextSecondary, fontSize = 12.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(40.dp))
            // Thin indeterminate loader (Zangetsu-style pill).
            Box(
                Modifier.width(64.dp).height(3.dp).clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f * pulse + 0.08f))
                    .alpha(loader.value)
            )
        }
    }
}
