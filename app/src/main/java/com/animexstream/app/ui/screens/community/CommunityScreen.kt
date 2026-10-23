package com.animexstream.app.ui.screens.community

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.animexstream.app.data.AuthRepository
import com.animexstream.app.data.CommunityPost
import com.animexstream.app.data.CommunityRepository
import com.animexstream.app.data.CommunityUser
import com.animexstream.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val communityTabs = listOf("For You", "Anime Talk", "News", "Events")

@Composable
fun CommunityScreen(onProfileClick: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var posts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var currentUser by remember { mutableStateOf<CommunityUser?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var attachedImage by remember { mutableStateOf<Uri?>(null) }
    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showVideoPopup by remember { mutableStateOf(false) }
    var viewingImage by remember { mutableStateOf<String?>(null) }
    var mentionQuery by remember { mutableStateOf<String?>(null) }
    var mentionSuggestions by remember { mutableStateOf<List<CommunityUser>>(emptyList()) }
    var mentionUnreadCount by remember { mutableIntStateOf(0) }
    var newMessageCount by remember { mutableIntStateOf(0) }
    var firstLoadDone by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) attachedImage = uri
    }

    LaunchedEffect(Unit) {
        val uid = AuthRepository.uid
        val uname = AuthRepository.username ?: "guest"
        val email = AuthRepository.email ?: ""
        val icon = AuthRepository.iconLink ?: ""
        currentUser = CommunityUser(
            id = uid ?: "", username = uname, iconLink = icon, email = email,
            displayName = AuthRepository.displayName ?: uname
        )
    }

    // Realtime listener — Firebase Realtime Database (native SDK)
    DisposableEffect(Unit) {
        val listener = CommunityRepository.listenPosts(
            onUpdate = { fresh ->
                val prevCount = posts.size
                val added = if (prevCount > 0 && fresh.size > prevCount) fresh.size - prevCount else 0
                posts = fresh
                if (!firstLoadDone && fresh.isNotEmpty()) {
                    scope.launch {
                        delay(80)
                        listState.scrollToItem(fresh.size + 1)
                        firstLoadDone = true
                    }
                } else if (added > 0) {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val isNearBottom = lastVisible >= (fresh.size + 1) - 2
                    if (isNearBottom) {
                        scope.launch {
                            delay(50)
                            listState.animateScrollToItem(fresh.size + 1)
                            newMessageCount = 0
                        }
                    } else {
                        newMessageCount += added
                    }
                }
            },
            onError = { error = it }
        )
        onDispose { CommunityRepository.removePostsListener(listener) }
    }

    // Unread mention badge
    LaunchedEffect(posts, currentUser) {
        val uid = currentUser?.id ?: return@LaunchedEffect
        if (uid.isBlank()) return@LaunchedEffect
        val unread = posts.count { uid in it.mentions && uid !in it.seenBy }
        mentionUnreadCount = unread
        delay(2500)
        posts.filter { uid in it.mentions && uid !in it.seenBy }.forEach { CommunityRepository.markSeen(it.id, uid) }
    }

    LaunchedEffect(mentionQuery) {
        val q = mentionQuery
        if (q == null || q.length < 1) { mentionSuggestions = emptyList(); return@LaunchedEffect }
        delay(250)
        mentionSuggestions = CommunityRepository.searchUsers(q)
    }

    // Reset new message count when user scrolls to bottom
    LaunchedEffect(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index) {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = posts.size + 2
        if (last >= total - 2) newMessageCount = 0
    }

    // Sorted ascending (oldest top, newest bottom)
    val chronoPosts = remember(posts) { posts.sortedBy { it.createdAt } }

    Box(Modifier.fillMaxSize().background(BgDark)) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Community", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box {
                    Icon(Icons.Default.Email, null, tint = TextPrimary, modifier = Modifier.size(22.dp))
                    if (mentionUnreadCount > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-6).dp)
                                .size(16.dp).clip(CircleShape).background(HeartRed),
                            contentAlignment = Alignment.Center
                        ) { Text("$mentionUnreadCount", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.width(12.dp))
                CircleAvatar(iconLink = currentUser?.iconLink ?: "", size = 32.dp, onClick = onProfileClick)
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                communityTabs.forEachIndexed { i, tab ->
                    val sel = i == selectedTab
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).clickable { selectedTab = i }.padding(vertical = 8.dp)) {
                        Text(tab, color = if (sel) PurpleLight else TextSecondary, fontSize = 12.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.width(if (sel) 28.dp else 0.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(if (sel) PurpleLight else Color.Transparent))
                    }
                }
            }

            // Chat area (posts, newest at bottom)
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item { Spacer(Modifier.height(6.dp)) }

                    items(chronoPosts.size) { i ->
                        val p = chronoPosts[i]
                        val uid = currentUser?.id
                        PostCard(
                            post = p,
                            isLiked = uid != null && uid in p.likes,
                            onLike = { uid?.let { u -> scope.launch { CommunityRepository.toggleLike(p, u); posts = CommunityRepository.loadPosts() } } },
                            onImageClick = { url -> viewingImage = url }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    item { Spacer(Modifier.height(10.dp)) }
                }

                // Down arrow pill
                if (newMessageCount > 0 && listState.firstVisibleItemIndex < (chronoPosts.size - 1)) {
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(50)).background(PurplePrimary)
                            .clickable {
                                scope.launch { listState.animateScrollToItem(chronoPosts.size + 1) }
                                newMessageCount = 0
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("$newMessageCount new", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Mention suggestions above composer
            if (mentionQuery != null && mentionSuggestions.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().background(BgSurface).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    mentionSuggestions.take(4).forEach { u ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val atIdx = text.lastIndexOf('@')
                                text = if (atIdx >= 0) text.substring(0, atIdx) + "@${u.username} " else text + "@${u.username} "
                                mentionQuery = null; mentionSuggestions = emptyList()
                            }.padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Email, null, tint = PurpleLight, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("@${u.username}", color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Composer pinned bottom
            ComposerBar(
                user = currentUser, text = text,
                onTextChange = { new ->
                    text = new
                    val tail = new.substringAfterLast('@', "")
                    mentionQuery = if (new.contains('@') && new.lastIndexOf('@') > new.lastIndexOf(' ')) tail else null
                },
                attachedImage = attachedImage,
                onClearImage = { attachedImage = null },
                onPlusClick = { showAttachMenu = true },
                posting = posting,
                onSend = {
                    val u = currentUser
                    if (u == null || text.isBlank()) { error = "Type something first"; return@ComposerBar }
                    scope.launch {
                        posting = true; error = null
                        val res = CommunityRepository.createPost(u, text.trim(), attachedImage, context)
                        posting = false
                        if (res.isSuccess) {
                            text = ""; attachedImage = null; mentionQuery = null
                            posts = CommunityRepository.loadPosts()
                            delay(100)
                            listState.animateScrollToItem((posts.size + 1).coerceAtLeast(0))
                        } else error = res.exceptionOrNull()?.message
                    }
                }
            )
            if (error != null) {
                Text(error!!, color = HeartRed, fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().background(BgDark).padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
    }

    if (showAttachMenu) {
        AlertDialog(
            onDismissRequest = { showAttachMenu = false },
            confirmButton = {},
            title = { Text("Attach", color = TextPrimary) },
            text = {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BgCardLight)
                            .clickable { showAttachMenu = false; imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, null, tint = PurpleLight, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Image", color = TextPrimary, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BgCardLight)
                            .clickable { showAttachMenu = false; showVideoPopup = true }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = PurpleLight, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Video", color = TextPrimary, fontSize = 14.sp)
                    }
                }
            },
            containerColor = BgCard
        )
    }

    if (showVideoPopup) {
        AlertDialog(
            onDismissRequest = { showVideoPopup = false },
            confirmButton = { TextButton(onClick = { showVideoPopup = false }) { Text("OK", color = PurpleLight) } },
            title = { Text("Video Upload", color = TextPrimary) },
            text = { Text("Wait for next update", color = TextSecondary) },
            containerColor = BgCard
        )
    }

    val vi = viewingImage
    if (vi != null) {
        Box(
            Modifier.fillMaxSize().background(Color(0xEE000000)).clickable { viewingImage = null }.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(model = vi, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
        }
    }
}

@Composable
private fun CircleAvatar(iconLink: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit = {}) {
    Box(Modifier.size(size).clip(CircleShape).background(BgCard).clickable { onClick() }, contentAlignment = Alignment.Center) {
        if (iconLink.isNotBlank()) AsyncImage(model = iconLink, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Default.Person, null, tint = PurpleLight, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun ComposerBar(
    user: CommunityUser?, text: String, onTextChange: (String) -> Unit,
    attachedImage: Uri?, onClearImage: () -> Unit,
    onPlusClick: () -> Unit, posting: Boolean, onSend: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(BgSurface)
            .navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        if (attachedImage != null) {
            Box {
                AsyncImage(model = attachedImage, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp)))
                IconButton(onClick = onClearImage, modifier = Modifier.align(Alignment.TopEnd)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleAvatar(iconLink = user?.iconLink ?: "", size = 32.dp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(BgCard).padding(horizontal = 14.dp, vertical = 10.dp)) {
                BasicTextField(
                    value = text, onValueChange = onTextChange,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(PurpleLight),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("Share something...", color = TextTertiary, fontSize = 13.sp)
                        inner()
                    },
                    maxLines = 4
                )
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onPlusClick, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.Add, null, tint = PurpleLight, modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = onSend, enabled = !posting, modifier = Modifier.size(38.dp)) {
                if (posting) CircularProgressIndicator(Modifier.size(20.dp), color = PurpleLight, strokeWidth = 2.dp)
                else Icon(Icons.Default.Email, null, tint = PurpleLight, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun PostCard(post: CommunityPost, isLiked: Boolean, onLike: () -> Unit, onImageClick: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp))
            .background(BgCard).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleAvatar(iconLink = post.iconLink, size = 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(post.username, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(timeAgo(post.createdAt), color = TextSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (post.text.isNotBlank()) Text(post.text, color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
        if (post.imageUrl != null) {
            Spacer(Modifier.height(10.dp))
            AsyncImage(
                model = post.imageUrl, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().wrapContentHeight().clip(RoundedCornerShape(12.dp)).background(Color.Black)
                    .background(BgCardLight).clickable { onImageClick(post.imageUrl) }
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clickable { onLike() }, verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null,
                    tint = if (isLiked) HeartRed else TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(post.likes.size.toString(), color = TextSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Star, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

private fun timeAgo(ms: Long): String {
    if (ms <= 0) return "just now"
    val sec = (System.currentTimeMillis() - ms) / 1000
    return when {
        sec < 30 -> "just now"
        sec < 60 -> "${sec}s ago"
        sec < 3600 -> "${sec / 60}m ago"
        sec < 86400 -> "${sec / 3600}h ago"
        sec < 604800 -> "${sec / 86400}d ago"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(ms))
    }
}
