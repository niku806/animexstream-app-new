package com.animexstream.app.data

import com.animexstream.app.Secrets

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GitHub repo ko database ki tarah use karta hai.
 * Reads: public raw.githubusercontent.com (fast, CDN-cached).
 * Writes: GitHub Contents API (token encrypted se, sirf build-time).
 */
object GitHubDb {

    private val TOKEN: String get() = Secrets.GH_TOKEN
    private val OWNER: String get() = Secrets.GH_OWNER
    private val REPO: String get() = Secrets.GH_REPO
    private const val BRANCH = "main"

    private const val USERS_PATH = "community/users.json"
    private const val POSTS_PATH = "community/posts.json"

    private val client = OkHttpClient.Builder()
        .proxy(java.net.Proxy.NO_PROXY)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ---------- caches (avoid GitHub API limits) ----------
    @Volatile private var usersCache: JSONArray? = null
    @Volatile private var postsCache: JSONArray? = null
    @Volatile private var usersSha: String? = null
    @Volatile private var postsSha: String? = null

    private fun rawUrl(path: String) =
        "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/$path?t=${System.currentTimeMillis() / 10000}"

    private fun apiUrl(path: String) =
        "https://api.github.com/repos/$OWNER/$REPO/contents/$path"

    // ---------- READ ----------
    suspend fun readUsers(force: Boolean = false): JSONArray = withContext(Dispatchers.IO) {
        if (!force) usersCache?.let { return@withContext it }
        val text = fetchRaw(USERS_PATH)
        val arr = try { if (text.isNullOrBlank()) JSONArray() else JSONArray(text) } catch (_: Exception) { JSONArray() }
        usersCache = arr
        arr
    }

    suspend fun readPosts(force: Boolean = false): JSONArray = withContext(Dispatchers.IO) {
        if (!force) postsCache?.let { return@withContext it }
        val text = fetchRaw(POSTS_PATH)
        val arr = try { if (text.isNullOrBlank()) JSONArray() else JSONArray(text) } catch (_: Exception) { JSONArray() }
        postsCache = arr
        arr
    }

    private fun fetchRaw(path: String): String? = try {
        val req = Request.Builder().url(rawUrl(path)).header("User-Agent", "AnimeXstream").get().build()
        client.newCall(req).execute().use { r ->
            if (r.code == 404) "[]"
            else if (r.isSuccessful) r.body?.string() else null
        }
    } catch (_: Exception) { null }

    // ---------- WRITE ----------
    /** Appends a user. Returns true on success. */
    suspend fun addUser(user: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            val current = readUsers(force = true)
            val existing = current.let { arr ->
                (0 until arr.length()).any {
                    val u = arr.optJSONObject(it) ?: return@any false
                    u.optString("username").equals(user.optString("username"), true) ||
                    u.optString("email").equals(user.optString("email"), true)
                }
            }
            if (existing) return@withContext false
            val updated = JSONArray()
            for (i in 0 until current.length()) current.opt(i)?.let { updated.put(it) }
            updated.put(user)
            val ok = writeFile(USERS_PATH, updated.toString(), "Add user ${user.optString("username")}")
            if (ok) { usersCache = updated }
            ok
        } catch (_: Exception) { false }
    }

    /** Appends a post. Returns true on success. */
    suspend fun addPost(post: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            val current = readPosts(force = true)
            val updated = JSONArray()
            for (i in 0 until current.length()) current.opt(i)?.let { updated.put(it) }
            updated.put(post)
            val ok = writeFile(POSTS_PATH, updated.toString(), "New post by ${post.optString("username")}")
            if (ok) { postsCache = updated }
            ok
        } catch (_: Exception) { false }
    }

    /** Replaces full posts array â€” for likes / seenBy / edits. */
    suspend fun writePosts(posts: JSONArray): Boolean = withContext(Dispatchers.IO) {
        val ok = writeFile(POSTS_PATH, posts.toString(), "Update posts")
        if (ok) postsCache = posts
        ok
    }

    suspend fun updateUser(originalUsername: String, newUser: JSONObject): Boolean = withContext(Dispatchers.IO) {
        try {
            val current = readUsers(force = true)
            val updated = JSONArray()
            for (i in 0 until current.length()) {
                val u = current.optJSONObject(i) ?: continue
                if (u.optString("username").equals(originalUsername, true)) updated.put(newUser)
                else updated.put(u)
            }
            val ok = writeFile(USERS_PATH, updated.toString(), "Update user ${newUser.optString("username")}")
            if (ok) usersCache = updated
            ok
        } catch (_: Exception) { false }
    }

    // ---------- GitHub Contents API ----------
    private fun writeFile(path: String, content: String, message: String): Boolean = try {
        // fetch current sha (needed for updates)
        val sha = fetchSha(path)
        val payload = JSONObject().apply {
            put("message", message)
            put("content", Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP))
            put("branch", BRANCH)
            if (sha != null) put("sha", sha)
        }.toString()

        val req = Request.Builder()
            .url(apiUrl(path))
            .header("Authorization", "token $TOKEN")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "AnimeXstream-App")
            .put(payload.toRequestBody(jsonType))
            .build()

        client.newCall(req).execute().use { r ->
            r.isSuccessful
        }
    } catch (_: Exception) { false }

    private fun fetchSha(path: String): String? = try {
        val req = Request.Builder()
            .url(apiUrl(path))
            .header("Authorization", "token $TOKEN")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "AnimeXstream-App")
            .get().build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            val json = JSONObject(r.body?.string().orEmpty())
            json.optString("sha").takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) { null }

    // ---------- Image upload (same as before, moved here) ----------
    suspend fun uploadImage(context: android.content.Context, uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        try {
            if (TOKEN.isBlank()) return@withContext null
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            if (bytes.size > 4_500_000) return@withContext null
            val ext = if (bytes.size >= 8 && bytes[0].toInt() == 0x89) "png" else "jpg"
            val fileName = "community-images/post_${System.currentTimeMillis()}_${(1000..9999).random()}.$ext"
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val payload = JSONObject().apply {
                put("message", "upload $fileName")
                put("content", b64)
                put("branch", BRANCH)
            }.toString()
            val req = Request.Builder()
                .url("https://api.github.com/repos/$OWNER/$REPO/contents/$fileName")
                .header("Authorization", "token $TOKEN")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "AnimeXstream-App")
                .put(payload.toRequestBody(jsonType))
                .build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val json = JSONObject(r.body?.string().orEmpty())
                json.optJSONObject("content")?.optString("download_url")
                    ?: "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/$fileName"
            }
        } catch (_: Exception) { null }
    }
}

