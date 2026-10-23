package com.animexstream.app.ui.screens.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animexstream.app.R
import com.animexstream.app.data.AuthRepository
import com.animexstream.app.data.CommunityRepository
import com.animexstream.app.ui.theme.*
import kotlinx.coroutines.launch

val SITE_ICONS = listOf(
    "https://i.postimg.cc/fycr1VqS/01603f377f6eb111bd0d1334f39ce47f.jpg",
    "https://i.postimg.cc/7h3Wj5tN/0a39898bd6f351bb5bfce8ae3ee14fc4.jpg",
    "https://i.postimg.cc/wxLZ1rq4/190317f1e0d60d95c7738cdd3c34b0e7.jpg",
    "https://i.postimg.cc/tJ3M8svt/2760900e2c0f90f34e89c2449e0f6f3a.jpg",
    "https://i.postimg.cc/kGQpL2YN/277a9d5fe22273e09ceb86900d393d9b.jpg",
    "https://i.postimg.cc/h4T6XYD5/3453d9e6b708118b9d1b95fe5a5a8a38.jpg",
    "https://i.postimg.cc/7P0d5RHR/3857cdca52851a352561f7cad82d80f0.jpg",
    "https://i.postimg.cc/h4T6XYD6/3f8dce2ef62113a38a157f2b4117c12f.jpg",
    "https://i.postimg.cc/2jhM1tkP/4b9aa41e76cb01e016c491713641240a.jpg",
    "https://i.postimg.cc/dQrphfQP/5c9576c90c5d553e2760e362fa735175.jpg",
    "https://i.postimg.cc/BZHkjzZ0/7417b192ab260f3106fca730de811284.jpg",
    "https://i.postimg.cc/7P0d5RPr/76eefb9fedffdcbf39bd0540e0b19f3c.jpg",
    "https://i.postimg.cc/mZCJPnZ4/77acc210d0cad894656d4d6467adace9.jpg",
    "https://i.postimg.cc/7P0d5RPD/783bc3b7db79e93bf7a672266de21267.jpg",
    "https://i.postimg.cc/dQrphfQw/7b9aa2c073cd87d134493c909b7789df.jpg",
    "https://i.postimg.cc/dQrphfQV/81ade635ae280ed6bbacca8f1cff60d7.jpg",
    "https://i.postimg.cc/bYkWZ5YJ/875b4fb82c44a038466807b0dcf884cc.jpg",
    "https://i.postimg.cc/h4T6XY4G/915f70b9c8b2a6f65ba26197500f12d7.jpg",
    "https://i.postimg.cc/SQC3nPQn/bfc58d360012f6eb6a379e533545f04e.jpg",
    "https://i.postimg.cc/bYkWZ5YS/f58c515cfb221fad071d19951944efe3.jpg"
)

@Composable
fun SignupScreen(onSignedUp: () -> Unit, onGoLogin: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("") }
    var galleryIcon by remember { mutableStateOf<Uri?>(null) }
    var showIconPicker by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) galleryIcon = uri
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(painter = painterResource(R.drawable.signup_bg), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0x33000000), Color(0x77000000), Color(0xF205030A)
        ))))

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(30.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Anime", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("X", color = PurpleLight, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("stream", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text("Create your account", color = TextSecondary, fontSize = 13.sp)

            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(BgCard)
                    .border(2.dp, PurplePrimary, CircleShape).clickable { showIconPicker = true },
                contentAlignment = Alignment.Center
            ) {
                when {
                    galleryIcon != null -> AsyncImage(model = galleryIcon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    selectedIcon.isNotBlank() -> AsyncImage(model = selectedIcon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else -> Icon(Icons.Default.Person, null, tint = PurpleLight, modifier = Modifier.size(40.dp))
                }
                Box(Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(PurplePrimary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Tap to pick profile icon", color = TextSecondary, fontSize = 11.sp)

            Spacer(Modifier.height(16.dp))
            GlassField(fullName, { fullName = it; error = null }, "Full Name", Icons.Default.Person)
            Spacer(Modifier.height(12.dp))
            GlassField(username, { username = it; error = null }, "Username (unique)", Icons.Default.Person)
            Spacer(Modifier.height(12.dp))
            GlassField(email, { email = it; error = null }, "Email Address", Icons.Default.Email, keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(12.dp))
            GlassField(password, { password = it; error = null }, "Password", Icons.Default.Lock, isPassword = true, keyboardType = KeyboardType.Password)
            Spacer(Modifier.height(12.dp))
            GlassField(confirm, { confirm = it; error = null }, "Confirm Password", Icons.Default.Lock, isPassword = true, keyboardType = KeyboardType.Password)

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, color = HeartRed, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    when {
                        fullName.isBlank() || username.isBlank() || email.isBlank() || password.isBlank() -> error = "Please fill all fields"
                        username.length < 3 -> error = "Username must be at least 3 characters"
                        !Regex("^[a-zA-Z0-9_]+$").matches(username) -> error = "Username: only letters, numbers, _"
                        password != confirm -> error = "Passwords do not match"
                        password.length < 6 -> error = "Password min 6 characters"
                        else -> scope.launch {
                            loading = true; error = null
                            var iconUrl = selectedIcon
                            if (galleryIcon != null) {
                                val up = CommunityRepository.uploadForSignup(context, galleryIcon!!)
                                if (up != null) iconUrl = up
                                else { error = "Icon upload failed"; loading = false; return@launch }
                            }
                            val res = AuthRepository.signUp(email, username, password, fullName, iconUrl)
                            loading = false
                            if (res.isSuccess) onSignedUp() else error = res.exceptionOrNull()?.message
                        }
                    }
                },
                enabled = !loading, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(PurpleBright, PurpleDeep))), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = TextPrimary, strokeWidth = 2.dp)
                    else Text("Sign Up", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row {
                Text("Already have an account? ", color = TextSecondary, fontSize = 13.sp)
                Text("Sign In", color = PurpleLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onGoLogin() })
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (showIconPicker) {
        AlertDialog(
            onDismissRequest = { showIconPicker = false },
            confirmButton = {},
            title = { Text("Choose profile icon", color = TextPrimary) },
            text = {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { showIconPicker = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            modifier = Modifier.weight(1f)
                        ) { Text("From Gallery", color = TextPrimary) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { showIconPicker = false; galleryIcon = null; selectedIcon = "" },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = BgCardLight),
                            modifier = Modifier.weight(1f)
                        ) { Text("Remove", color = TextPrimary) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Or pick from our collection:", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(280.dp)
                    ) {
                        items(SITE_ICONS.size) { idx ->
                            val url = SITE_ICONS[idx]
                            Box(
                                Modifier.size(52.dp).clip(CircleShape)
                                    .border(2.dp, if (selectedIcon == url) PurpleLight else Color.Transparent, CircleShape)
                                    .clickable {
                                        selectedIcon = url
                                        galleryIcon = null
                                        showIconPicker = false
                                    }
                            ) {
                                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                            }
                        }
                    }
                }
            },
            containerColor = BgCard
        )
    }
}
