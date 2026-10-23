package com.animexstream.app.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.animexstream.app.R
import com.animexstream.app.data.AuthRepository
import com.animexstream.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun GlassField(
    value: String, onValueChange: (String) -> Unit, placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text
) {
    var visible by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Color(0x33FFFFFF))
            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            TextField(
                value = value, onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = TextTertiary, fontSize = 14.sp) },
                modifier = Modifier.weight(1f), singleLine = true,
                visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = PurpleLight
                )
            )
            if (isPassword) {
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Default.Info else Icons.Default.Lock, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, onGoSignup: () -> Unit) {
    val scope = rememberCoroutineScope()
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(painter = painterResource(R.drawable.login_bg), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0x33000000), Color(0x77000000), Color(0xF205030A)
        ))))

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Anime", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("X", color = PurpleLight, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("stream", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Continue your anime journey", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(36.dp))

            GlassField(identifier, { identifier = it; error = null }, "Username or Email", Icons.Default.Person)
            Spacer(Modifier.height(14.dp))
            GlassField(password, { password = it; error = null }, "Password", Icons.Default.Lock,
                isPassword = true, keyboardType = KeyboardType.Password)

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, color = HeartRed, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (identifier.isBlank() || password.isBlank()) { error = "Please fill all fields"; return@Button }
                    scope.launch {
                        loading = true; error = null
                        val res = AuthRepository.signIn(identifier, password)
                        loading = false
                        if (res.isSuccess) onLoggedIn() else error = res.exceptionOrNull()?.message
                    }
                },
                enabled = !loading, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(PurpleBright, PurpleDeep))),
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = TextPrimary, strokeWidth = 2.dp)
                    else Text("Sign In", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row {
                Text("Don't have an account? ", color = TextSecondary, fontSize = 13.sp)
                Text("Sign Up", color = PurpleLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onGoSignup() })
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}
