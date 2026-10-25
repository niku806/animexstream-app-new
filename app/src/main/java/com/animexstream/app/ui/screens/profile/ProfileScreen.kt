package com.animexstream.app.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animexstream.app.data.AuthRepository
import com.animexstream.app.data.CommunityPost
import com.animexstream.app.data.CommunityRepository
import com.animexstream.app.data.UserPrefs
import com.animexstream.app.ui.screens.auth.SITE_ICONS
import androidx.compose.foundation.lazy.grid.itemsIndexed
import com.animexstream.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onLogout: () -> Unit, onOpenSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var username by remember { mutableStateOf(AuthRepository.username ?: "guest") }
    var displayName by remember { mutableStateOf(AuthRepository.displayName ?: (AuthRepository.username ?: "guest")) }
    var iconLink by remember { mutableStateOf(AuthRepository.iconLink ?: "") }
    var myPosts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var showEdit by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(displayName) }
    var newUsername by remember { mutableStateOf(username) }
    var newIcon by remember { mutableStateOf(iconLink) }
    var newGallery by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { newGallery = it }

    LaunchedEffect(Unit) { myPosts = CommunityRepository.loadUserPosts(username) }

    Column(Modifier.fillMaxSize().background(BgDark).verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = 30.dp)) {
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PurpleDeep, PurplePrimary, BgDark))))
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(BgCard).border(3.dp, PurpleLight, CircleShape)) {
                    if (iconLink.isNotBlank()) AsyncImage(model = iconLink, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Default.Person, null, tint = PurpleLight, modifier = Modifier.padding(24.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(displayName, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("@$username", color = TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat(myPosts.size.toString(), "Posts")
                Stat(myPosts.sumOf { it.likes.size }.toString(), "Likes")
                Stat(fmtWatch(UserPrefs.watchSeconds()), "Watch time")
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    newName = displayName; newUsername = username; newIcon = iconLink; newGallery = null
                    showEdit = true
                },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, PurplePrimary)
            ) { Text("Edit Profile", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = onLogout, modifier = Modifier.weight(1f), shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = BgCard)
            ) { Text("Logout", color = HeartRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            MenuRow(Icons.Default.FavoriteBorder, "Watchlist") { }
            MenuRow(Icons.Default.Settings, "Settings", onClick = onOpenSettings)
            MenuRow(Icons.Default.Info, "About AnimeXstream") { }
        }
        Spacer(Modifier.height(20.dp))
        Text("My Posts", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(10.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (myPosts.isEmpty()) Text("You haven't posted anything yet.", color = TextSecondary, fontSize = 12.sp)
            else myPosts.forEach { p ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(BgCard).padding(12.dp)) {
                    if (p.text.isNotBlank()) Text(p.text, color = TextPrimary, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (p.imageUrl != null) {
                        Spacer(Modifier.height(6.dp))
                        AsyncImage(model = p.imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp)))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row { Icon(Icons.Default.Favorite, null, tint = HeartRed, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("${p.likes.size}", color = TextSecondary, fontSize = 11.sp) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showEdit) {
        AlertDialog(
            onDismissRequest = { showEdit = false },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        saving = true; errorMsg = null
                        var finalIcon = newIcon
                        if (newGallery != null) {
                            val up = CommunityRepository.uploadForSignup(context, newGallery!!)
                            if (up != null) finalIcon = up else { errorMsg = "Icon upload failed"; saving = false; return@launch }
                        }
                        val res = AuthRepository.updateProfile(newUsername.trim().lowercase(), finalIcon, newName.trim())
                        saving = false
                        if (res.isSuccess) {
                            username = newUsername.trim().lowercase()
                            displayName = newName.trim()
                            iconLink = finalIcon
                            showEdit = false
                            myPosts = CommunityRepository.loadUserPosts(username)
                        } else errorMsg = res.exceptionOrNull()?.message
                    }
                }) { Text(if (saving) "Saving…" else "Save", color = PurpleLight) }
            },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("Cancel", color = TextSecondary) } },
            title = { Text("Edit Profile", color = TextPrimary) },
            text = {
                Column {
                    Box(Modifier.size(80.dp).clip(CircleShape).background(BgCard)
                        .clickable { showIconPicker = true }.align(Alignment.CenterHorizontally)) {
                        when {
                            newGallery != null -> AsyncImage(model = newGallery, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            newIcon.isNotBlank() -> AsyncImage(model = newIcon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else -> Icon(Icons.Default.Person, null, tint = PurpleLight, modifier = Modifier.padding(20.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Tap icon to change", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Display Name") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = newUsername, onValueChange = { newUsername = it }, label = { Text("Username") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth())
                    if (errorMsg != null) { Spacer(Modifier.height(8.dp)); Text(errorMsg!!, color = HeartRed, fontSize = 11.sp) }
                }
            },
            containerColor = BgCard
        )
    }

    if (showIconPicker) {
        AlertDialog(
            onDismissRequest = { showIconPicker = false },
            confirmButton = { TextButton(onClick = { showIconPicker = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Gallery", color = PurpleLight) } },
            title = { Text("Pick icon", color = TextPrimary) },
            text = {
                LazyVerticalGrid(columns = GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(260.dp)) {
                    items(SITE_ICONS.size) { idx ->
                        val url = SITE_ICONS[idx]
                        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp).clip(CircleShape).clickable { newIcon = url; newGallery = null; showIconPicker = false })
                    }
                }
            },
            containerColor = BgCard
        )
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(BgCard).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = PurpleLight, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.KeyboardArrowRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
    Spacer(Modifier.height(8.dp))
}

private fun fmtWatch(sec: Long): String {
    if (sec < 60) return "${sec}s"
    if (sec < 3600) return "${sec / 60}m"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (m > 0) "${h}h ${m}m" else "${h}h"
}
