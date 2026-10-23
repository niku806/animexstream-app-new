package com.animexstream.app.ui.screens.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animexstream.app.R
import com.animexstream.app.data.AuthRepository
import com.animexstream.app.ui.theme.PurpleLight
import com.animexstream.app.ui.theme.TextPrimary
import com.animexstream.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNext: (Boolean) -> Unit) {
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(500))
    }
    LaunchedEffect(Unit) {
        delay(1800)
        onNext(AuthRepository.isLoggedIn)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = painterResource(R.drawable.splash_bg),
            contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(alpha.value)
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0x33000000), Color(0x77000000), Color(0xEE05030A)
        ))))

        Column(
            Modifier.align(Alignment.Center).padding(32.dp).scale(scale.value).alpha(alpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Anime", color = TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                Text("X", color = PurpleLight, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                Text("stream", color = TextPrimary, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text("Your Ultimate Anime Streaming Experience", color = TextSecondary, fontSize = 14.sp)
        }
    }
}
