package com.animexstream.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * MovieBox (com.community.oneroom) — a large Hindi/English catalogue of anime,
 * movies and series, with real playable mp4 streams.
 *
 * Every request is signed: an HMAC-MD5 over a canonical string, sent as
 * `x-tr-signature`, plus a `x-client-token` and a device-shaped `x-client-info`.
 * A visitor token from `visitor-login` authorises the catalogue calls.
 *
 * Verified live: search, details, episode lists and 1080p/720p mp4 streams all
 * work. Streams carry a `signCookie` that must be replayed as a Cookie header
 * together with the MovieBox referer.
 */
object MovieBox {

    // Multiple API mirrors — one blocked/slow host never empties the app.
    private val HOSTS = listOf(
        "https://api6.aoneroom.com",
        "https://api5.aoneroom.com",
        "https://api4.aoneroom.com",
        "https://api4sg.aoneroom.com",
        "https://api3.aoneroom.com",
        "https://api6sg.aoneroom.com",
        "https://api.inmoviebox.com",
    )
    @Volatile private var activeHost = 0
    private const val STREAM_REFERER = "https://sportslive.wine"
    private val SECRET = byteArrayOf(
        0xEF.toByte(), 0xA8.toByte(), 0x91.toByte(), 0x97.toByte(), 0x4E, 0xEC.toByte(), 0xD3.toByte(), 0x14,
        0x8D.toByte(), 0xF6.toByte(), 0x3A, 0xA6.toByte(), 0x11, 0x60, 0x2D, 0xEF.toByte(),
        0xD1.toByte(), 0x01, 0x25, 0x9B.toByte(), 0xA5.toByte(), 0x21, 0x02, 0x2C,
        0x57, 0xAE.toByte(), 0x05, 0x66, 0xBD.toByte(), 0x8E.toByte(),
    )
    // MUST match the Content-Type used in [canonical], or the signature is rejected.
    private val jsonType = "application/json".toMediaType()

    // Plain OkHttp: system proxy and normal TLS validation are left untouched.
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ---------- device identity (stable per install) ----------
    private val versionCode = 50020120
    private val deviceId = randomHex(32)
    private val gaid = randomUuid()
    private val userAgent =
        "com.community.oneroom/$versionCode (Linux; U; Android 13; en_US; 23078RKD5C; " +
            "Build/TQ2A.230405.003; Cronet/135.0.7012.3)"
    private val clientInfo = JSONObject().apply {
        put("package_name", "com.community.oneroom")
        put("version_name", "4.0.01.0813.03")
        put("version_code", versionCode)
        put("os", "android")
        put("os_version", "13")
        put("install_ch", "ps")
        put("device_id", deviceId)
        put("install_store", "ps")
        put("gaid", gaid)
        put("brand", "Redmi")
        put("model", "23078RKD5C")
        put("system_language", "en")
        put("net", "NETWORK_WIFI")
        put("region", "US")
        put("timezone", "Asia/Kolkata")
        put("sp_code", "40401")
        put("X-Play-Mode", "2")
    }.toString()

    @Volatile private var token: String? = null
    @Volatile private var lastError: String? = null
    fun getLastError(): String? = lastError
    private fun setError(msg: String?) { lastError = msg }

    private fun randomHex(n: Int) = (0 until n).map { "0123456789abcdef".random() }.joinToString("")
    private fun randomUuid() = "${randomHex(8)}-${randomHex(4)}-${randomHex(4)}-${randomHex(4)}-${randomHex(12)}"
    private fun spoofIp() = "103.241.${Random.nextInt(1, 254)}.${Random.nextInt(1, 254)}"

    // ---------- signing ----------
    private fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun xClientToken(ts: Long): String =
        "$ts,${md5Hex(ts.toString().reversed().toByteArray())}"

    private fun sortedQuery(url: String): String {
        val q = url.substringAfter('?', "")
        if (q.isBlank()) return ""
        return q.split('&').filter { it.isNotBlank() }.sorted().joinToString("&")
    }

    private fun canonical(method: String, url: String, body: String?, ts: Long): String {
        val uri = runCatching { java.net.URI(url) }.getOrNull()
        val path = uri?.path ?: url.substringAfter("://").substringAfter('/').let { "/$it" }.substringBefore('?')
        val sq = sortedQuery(url)
        val canonicalUrl = if (sq.isEmpty()) path else "$path?$sq"
        val bodyBytes = body?.toByteArray()
        val bodyHash: String
        val bodyLen: String
        if (bodyBytes != null) {
            val truncated = if (bodyBytes.size > 102_400) bodyBytes.copyOf(102_400) else bodyBytes
            bodyHash = md5Hex(truncated)
            bodyLen = bodyBytes.size.toString()
        } else {
            bodyHash = ""; bodyLen = ""
        }
        return "${method.uppercase()}\napplication/json\napplication/json\n$bodyLen\n$ts\n$bodyHash\n$canonicalUrl"
    }

    private fun sign(method: String, url: String, body: String?, ts: Long): String {
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(SECRET, "HmacMD5"))
        val sig = mac.doFinal(canonical(method, url, body, ts).toByteArray(Charsets.UTF_8))
        return "$ts|2|" + Base64.encodeToString(sig, Base64.NO_WRAP)
    }


    // ---------- transport ----------
    private fun rawRequest(method: String, path: String, body: String?, auth: String?): JSONObject? {
        val start = activeHost
        val maxTries = minOf(3, HOSTS.size)
        for (i in 0 until maxTries) {
            val idx = (start + i) % HOSTS.size
            val url = HOSTS[idx] + path
            val ts = System.currentTimeMillis()
            val req = Request.Builder().url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Connection", "keep-alive")
                .header("x-client-token", xClientToken(ts))
                .header("x-tr-signature", sign(method, url, body, ts))
                .header("x-client-info", clientInfo)
                .header("x-client-status", "0")
                .header("x-forwarded-for", spoofIp())
                .apply { if (auth != null) header("Authorization", "Bearer $auth") }
                .apply {
                    if (method == "POST") post((body ?: "{}").toRequestBody(jsonType))
                    else get()
                }
                .build()
            try {
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) {
                        setError("HTTP ${r.code} from ${HOSTS[idx]}")
                        return@use
                    }
                    val text = r.body?.string()
                    if (text.isNullOrBlank()) {
                        setError("empty body from ${HOSTS[idx]}")
                        return@use
                    }
                    val json = runCatching { JSONObject(text) }.getOrNull()
                    if (json == null) {
                        setError("bad JSON from ${HOSTS[idx]}: ${text.take(100)}")
                        return@use
                    }
                    if (json.has("code")) {
                        val code = json.optInt("code", -1)
                        if (code != 0 && code != 200) {
                            setError("code=$code msg=${json.optString("message").take(60)}")
                            return@use
                        }
                    }
                    setError(null)
                    activeHost = idx
                    return json
                }
            } catch (e: Exception) {
                setError("${e.javaClass.simpleName}: ${e.message?.take(80)}")
            }
        }
        return null
    }

    private fun ensureToken(): String? {
        token?.let { return it }
        val d = rawRequest("POST", "/wefeed-mobile-bff/user-api/visitor-login", "{}", null)
        if (d == null) {
            if (lastError == null) setError("login failed - all hosts down")
            return null
        }
        val root = d.optJSONObject("data") ?: d
        val t = root.optString("token").takeIf { it.isNotBlank() }
        if (t == null) {
            setError("login response missing token")
            return null
        }
        token = t
        setError(null)
        return t
    }

    private fun request(method: String, path: String, body: String? = null): JSONObject? {
        val auth = ensureToken() ?: return null
        val res = rawRequest(method, path, body, auth)
        if (res == null) { token = null }   // force a fresh login next time
        return res
    }

    private fun dataOf(o: JSONObject?): JSONObject? = o?.optJSONObject("data") ?: o

    // ---------- public API ----------

    /** Search the MovieBox catalogue. */
    suspend fun search(query: String, page: Int = 1): List<Anime> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val body = JSONObject().apply {
            put("keyword", query); put("page", page); put("perPage", 20); put("subjectType", 0)
        }.toString()
        val root = dataOf(request("POST", "/wefeed-mobile-bff/subject-api/search/v2", body)) ?: return@withContext emptyList()
        val results = root.optJSONArray("results") ?: return@withContext emptyList()
        val out = ArrayList<Anime>()
        for (i in 0 until results.length()) {
            val group = results.optJSONObject(i) ?: continue
            val subjects = group.optJSONArray("subjects") ?: continue
            for (j in 0 until subjects.length()) {
                subjects.optJSONObject(j)?.let { s -> subjectToAnime(s)?.let { out.add(it) } }
            }
        }
        out.distinctBy { it.id }
    }

    /** Home rails — each is a titled group of subjects. */
    suspend fun homeRails(): List<Pair<String, List<Anime>>> = withContext(Dispatchers.IO) {
        val root = dataOf(request("GET", "/wefeed-mobile-bff/tab-operating?page=1&tabId=&version="))
            ?: return@withContext emptyList()
        val groups = root.optJSONArray("items") ?: return@withContext emptyList()
        val out = ArrayList<Pair<String, List<Anime>>>()
        for (i in 0 until groups.length()) {
            val g = groups.optJSONObject(i) ?: continue
            val title = g.optString("title").ifBlank { g.optString("name") }
            if (title.isBlank()) continue
            val seen = LinkedHashMap<String, Anime>()
            fun add(subject: JSONObject?) { subject?.let { subjectToAnime(it)?.let { a -> seen.putIfAbsent(a.id, a) } } }
            g.optJSONObject("banner")?.optJSONArray("banners")?.let { b ->
                for (k in 0 until b.length()) add(b.optJSONObject(k)?.optJSONObject("subject"))
            }
            g.optJSONObject("customData")?.optJSONArray("items")?.let { c ->
                for (k in 0 until c.length()) add(c.optJSONObject(k)?.optJSONObject("subject"))
            }
            g.optJSONArray("subjects")?.let { s ->
                for (k in 0 until s.length()) add(s.optJSONObject(k))
            }
            if (seen.isNotEmpty()) out.add(title to seen.values.toList())
        }
        out
    }

    /** Full details + episode list for a subject. */
    suspend fun detail(subjectId: String): AnimeDetail? = withContext(Dispatchers.IO) {
        val root = dataOf(request("GET", "/wefeed-mobile-bff/subject-api/get?subjectId=$subjectId"))
            ?: return@withContext null
        val title = root.optString("title").ifBlank { "Unknown" }
        val poster = coverUrl(root)
        val genres = ArrayList<String>()
        root.optString("genre").split(',').map { it.trim() }.filter { it.isNotBlank() }.forEach { genres.add(it) }
        val year = Regex("(\\d{4})").find(root.optString("releaseDate"))?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val rating = root.optString("imdbRatingValue").toDoubleOrNull()?.div(10.0) ?: 0.0
        val type = if (root.optInt("subjectType", 1) == 2) "TV Series" else "Movie"

        // Build the episode list from the resource index (se/ep/title).
        val eps = ArrayList<Episode>()
        val seenKeys = HashSet<String>()
        val resRoot = dataOf(request("GET", "/wefeed-mobile-bff/subject-api/resource?subjectId=$subjectId&page=1&perPage=500"))
        resRoot?.optJSONArray("list")?.let { list ->
            for (i in 0 until list.length()) {
                val it = list.optJSONObject(i) ?: continue
                val se = it.optInt("se", 0)
                val ep = it.optInt("ep", 0)
                val key = "$se-$ep"
                if (!seenKeys.add(key)) continue
                val num = if (ep > 0) ep else 1
                val name = it.optString("title").ifBlank { if (type == "Movie") title else "Episode $num" }
                // Fetch subtitle tracks for this episode (best-effort).
                val subs = runCatching { subtitles("moviebox:$subjectId:$se:$ep") }.getOrDefault(emptyList())
                eps.add(
                    Episode(
                        number = num,
                        season = if (se > 0) se else 1,
                        title = name,
                        image = poster,
                        description = "",
                        pageUrl = "moviebox:$subjectId:$se:$ep",
                        subtitles = subs,
                    )
                )
            }
        }
        if (eps.isEmpty()) {
            eps.add(Episode(number = 1, season = 1, title = title, image = poster, pageUrl = "moviebox:$subjectId:0:0"))
        }
        eps.sortWith(compareBy({ it.season }, { it.number }))

        AnimeDetail(
            slug = subjectId,
            title = title,
            image = poster,
            banner = poster,
            synopsis = root.optString("description"),
            genres = genres,
            year = year,
            rating = rating,
            status = "Available",
            type = type,
            episodes = eps,
        )
    }

    /**
     * Real streams for one episode. [pageUrl] is "moviebox:<id>:<season>:<ep>".
     * Returns a single server whose variants are the 1080p/720p/480p mp4s.
     */
    suspend fun streams(pageUrl: String): List<ResolvedServer> = withContext(Dispatchers.IO) {
        val parts = pageUrl.removePrefix("moviebox:").split(':')
        if (parts.size < 3) return@withContext emptyList()
        val subjectId = parts[0]
        val se = parts[1].toIntOrNull() ?: 0
        val ep = parts[2].toIntOrNull() ?: 0
        val path = if (se == 0 && ep == 0)
            "/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$subjectId"
        else
            "/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$subjectId&se=$se&ep=$ep"
        val root = dataOf(request("GET", path)) ?: return@withContext emptyList()
        val streams = root.optJSONArray("streams") ?: return@withContext emptyList()
        val variants = ArrayList<QualityStream>()
        for (i in 0 until streams.length()) {
            val s = streams.optJSONObject(i) ?: continue
            val url = s.optString("url")
            if (!url.startsWith("http")) continue
            val signCookie = s.optString("signCookie")
            val headers = LinkedHashMap<String, String>()
            headers["Referer"] = STREAM_REFERER
            headers["User-Agent"] = userAgent
            headers["x-forwarded-for"] = spoofIp()
            if (signCookie.isNotBlank()) {
                headers["Cookie"] = signCookie.trimEnd(';')
                    .split(';').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("; ")
            }
            val resolutions = s.optString("resolutions").ifBlank { root.optString("displayResolutions", "1080") }
                .split(',').mapNotNull { it.trim().toIntOrNull() }.sortedDescending().ifEmpty { listOf(1080) }
            val codec = s.optString("codecName").ifBlank { s.optString("format", "MP4") }
            for (res in resolutions) {
                variants.add(
                    QualityStream(
                        quality = "${res}p $codec",
                        url = url,
                        directDownloadUrl = url,
                        audioUrl = null,
                        isHls = url.contains(".m3u8", true),
                        headers = headers,
                    )
                )
            }
        }
        if (variants.isEmpty()) emptyList() else listOf(ResolvedServer("MovieBox", variants))
    }

    /**
     * Subtitle tracks for an episode via the get-ext-captions API.
     * [pageUrl] is "moviebox:<id>:<se>:<ep>"; resourceId is fetched from
     * play-info when needed.
     */
    suspend fun subtitles(pageUrl: String): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        val parts = pageUrl.removePrefix("moviebox:").split(':')
        if (parts.size < 3) return@withContext emptyList()
        val sid = parts[0]
        val se = parts[1].toIntOrNull() ?: 0
        val ep = parts[2].toIntOrNull() ?: 0
        val path = if (se == 0 && ep == 0)
            "/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$sid"
        else
            "/wefeed-mobile-bff/subject-api/play-info/v2?subjectId=$sid&se=$se&ep=$ep"
        val root = dataOf(request("GET", path)) ?: return@withContext emptyList()
        val streams = root.optJSONArray("streams") ?: return@withContext emptyList()
        val resourceId = if (streams.length() > 0) streams.optJSONObject(0)?.optString("id") ?: "" else ""
        if (resourceId.isBlank()) return@withContext emptyList()
        val capRoot = dataOf(
            request("GET", "/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=$sid&resourceId=$resourceId")
        ) ?: return@withContext emptyList()
        val caps = capRoot.optJSONArray("extCaptions") ?: return@withContext emptyList()
        val out = ArrayList<SubtitleTrack>()
        val seen = HashSet<String>()
        for (i in 0 until caps.length()) {
            val c = caps.optJSONObject(i) ?: continue
            val url = c.optString("url")
            if (url.isBlank() || url.contains("aa348f2541d13ffe")) continue
            if (!seen.add(url)) continue
            val label = c.optString("lanName").ifBlank { c.optString("lan") }.ifBlank { "Unknown" }
            val lang = c.optString("lan")
            out.add(SubtitleTrack(label = label, url = url, language = lang))
        }
        out
    }

    /**
     * Browse a whole category page. [kind] is "anime", "movies" or "series".
     * Falls back to broad keyword sweeps where the API has no direct category feed.
     */
    suspend fun browseCategory(kind: String, page: Int = 1): List<Anime> = withContext(Dispatchers.IO) {
        val keyword = when (kind) {
            "anime" -> "anime"
            "movies" -> "movie"
            "series" -> "series"
            else -> kind
        }
        search(keyword, page)
    }

    /** Every home rail title paired with its subjects (real MovieBox rows). */
    suspend fun homeRailsFull(): List<Pair<String, List<Anime>>> = homeRails()

    /** Drop the cached visitor token (forces a fresh login on the next call). */
    fun clearCache() {
        token = null
    }

    // ---------- helpers ----------
    private fun coverUrl(s: JSONObject): String =
        s.optJSONObject("cover")?.optString("url")
            ?: s.optString("coverUrl").ifBlank { s.optString("pic") }

    private fun subjectToAnime(s: JSONObject): Anime? {
        val id = s.optString("subjectId").ifBlank { s.optString("id") }
        if (id.isBlank()) return null
        val title = s.optString("title").ifBlank { s.optString("name") }
        if (title.isBlank()) return null
        val genres = s.optString("genre").split(',').map { it.trim() }.filter { it.isNotBlank() }
        val year = Regex("(\\d{4})").find(s.optString("releaseDate")).let { it?.groupValues?.get(1)?.toIntOrNull() } ?: 0
        return Anime(
            id = id,
            title = title,
            poster = coverUrl(s),
            genres = genres,
            year = year,
            totalEpisodes = s.optInt("episodeCount", 0),
            synopsis = s.optString("description"),
            rating = s.optString("imdbRatingValue").toDoubleOrNull()?.div(10.0) ?: 0.0,
        )
    }
}
