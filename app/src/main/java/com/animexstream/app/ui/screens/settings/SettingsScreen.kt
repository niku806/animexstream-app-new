package com.animexstream.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animexstream.app.data.ContentRepository
import com.animexstream.app.data.Provider
import com.animexstream.app.data.ProviderPrefs
import com.animexstream.app.ui.theme.*

@Composable
fun SettingsScreen(onBack: () -> Unit, onProviderChanged: () -> Unit = {}) {
    var darkMode by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(ProviderPrefs.selected()) }

    Column(Modifier.fillMaxSize().background(BgDark).verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 30.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowRight, null, tint = TextPrimary) }
            Text("Settings", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {

            // ---------- Provider selector ----------
            Text("CONTENT PROVIDER", color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            Provider.entries.forEach { p ->
                ProviderCard(
                    provider = p,
                    selected = selected == p,
                    onClick = {
                        if (selected != p) {
                            selected = p
                            ProviderPrefs.set(p)
                            // Reset all provider-scoped caches so the new data loads fresh.
                            com.animexstream.app.data.AnimeRepository.clearCache()
                            com.animexstream.app.data.ServerResolver.clearCache()
                            onProviderChanged()
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Switches the catalogue, artwork and accent. AnimeXstream uses the original library; Animestream and Zangetsu pull live metadata and borrow playable sources where available.",
                color = TextTertiary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(20.dp))

            Text("GENERAL", color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            SettingRow(Icons.Default.Person, "Account") { }
            SettingRow(Icons.Default.Notifications, "Notifications") { }
            SettingRow(
                icon = Icons.Default.Settings,
                label = "Appearance",
                right = {
                    Switch(checked = darkMode, onCheckedChange = { darkMode = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = TextPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary))
                }
            ) { }
            SettingRow(icon = Icons.Default.Star, label = "Video Quality", right = { Text("Auto", color = TextSecondary, fontSize = 12.sp) }) { }
            SettingRow(icon = Icons.Default.Info, label = "Language", right = { Text("English", color = TextSecondary, fontSize = 12.sp) }) { }
            SettingRow(Icons.Default.Lock, "Privacy & Security") { }
            SettingRow(Icons.Default.Info, "Help & Support") { }
            SettingRow(Icons.Default.Info, "About") { }
        }
    }
}

@Composable
private fun ProviderCard(provider: Provider, selected: Boolean, onClick: () -> Unit) {
    val accent = Color(provider.accent)
    val accentSoft = Color(provider.accentSoft)
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) BgCardLight else BgCard)
            .then(if (selected) Modifier.border(1.5.dp, accent, RoundedCornerShape(16.dp)) else Modifier)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(accent, accentSoft))),
            contentAlignment = Alignment.Center
        ) {
            Text(provider.displayName.take(1), color = Color.Black.copy(alpha = 0.75f), fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(provider.displayName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(provider.tagline, color = TextSecondary, fontSize = 11.sp)
        }
        if (selected) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = Color.Black.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
            }
        } else {
            Box(Modifier.size(24.dp).clip(CircleShape).border(1.5.dp, DividerColor, CircleShape))
        }
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, right: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(BgCard).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (right != null) right() else Icon(Icons.Default.KeyboardArrowRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
    Spacer(Modifier.height(8.dp))
}
