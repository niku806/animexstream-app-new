package com.animexstream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animexstream.app.data.AuthRepository
import com.animexstream.app.ui.theme.*

@Composable
fun AppDrawerContent(onNavigate: (String) -> Unit, onClose: () -> Unit) {
    ModalDrawerSheet(drawerContainerColor = BgSurface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(PurpleLight, PurplePrimary))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.PlayArrow, null, tint = TextPrimary, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(10.dp))
                Row {
                    Text("Anime", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("X", color = PurpleLight, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("stream", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(20.dp))

            // Profile header
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgCard)
                    .clickable { onNavigate("profile"); onClose() }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(50)).background(BgCardLight), contentAlignment = Alignment.Center) {
                    val icon = AuthRepository.iconLink ?: ""
                    if (icon.isNotBlank()) AsyncImage(model = icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Default.Person, null, tint = PurpleLight, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(AuthRepository.displayName ?: "Guest", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("@" + (AuthRepository.username ?: "guest"), color = TextSecondary, fontSize = 11.sp)
                }
                Icon(Icons.Default.KeyboardArrowRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(16.dp))
            Text("BROWSE", color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(6.dp))

            DrawerRow(Icons.Default.PlayArrow, "Anime") { onNavigate("anime_tab"); onClose() }
            DrawerRow(Icons.Default.Star, "Movies") { onNavigate("movies"); onClose() }
            DrawerRow(Icons.Default.Home, "Live TV Sports") { onNavigate("sports"); onClose() }
            DrawerRow(Icons.Default.List, "TV Series") { onNavigate("tvseries"); onClose() }
            DrawerRow(Icons.Default.Person, "Community") { onNavigate("community"); onClose() }

            Spacer(Modifier.height(20.dp))
            Text("ACCOUNT", color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(6.dp))
            DrawerRow(Icons.Default.Person, "Profile") { onNavigate("profile"); onClose() }
            DrawerRow(Icons.Default.Settings, "Settings") { onNavigate("settings"); onClose() }
        }
    }
}

@Composable
private fun DrawerRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = PurpleLight, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
