package com.animexstream.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * animedekho.tv (the AnimeXstream library backend) — real search, episode lists
 * and stream servers. Used by the Animestream and Zangetsu providers so their
 * titles actually play and download, not just show metadata.
 */
object AnimeDekho {
    private const val BASE = "https://animedekho.tv"
    private const val API = "$BASE/wp-json/dgt/v1"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // In-memory cookie jar so the 24h ad-gate cookie persists across calls.
    private val jar = object : CookieJar {
        private val store = HashMap<String, MutableList<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = store.getOrPut(url.host) { mutableListOf() }
            cookies.forEach { c -> list.removeAll { it.name == c.name }; list.add(c) }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host] ?: emptyList()
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun get(url: String, referer: String? = null): String? = try {
        val b = Request.Builder().url(url).header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", referer ?: "$BASE/")
        client.newCall(b.build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (_: Exception) { null }

    private fun postJson(url: String, body: JSONObject): JSONObject? = try {
        val req = Request.Builder().url(url).header("User-Agent", UA)
            .header("Accept", "application/json").header("Referer", "$BASE/")
            .post(body.toString().toRequestBody(jsonType)).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) null else JSONObject(r.body?.string() ?: return null)
        }
    } catch (_: Exception) { null }

    private fun postForm(url: String, body: String, referer: String, origin: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", UA)
            .header("Referer", referer).header("Origin", origin)
            .header("X-Requested-With", "XMLHttpRequest")
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType())).build()
        client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (_: Exception) { null }

    private fun abs(u: String): String = when {
        u.isBlank() -> ""
        u.startsWith("http") -> u
        u.startsWith("//") -> "https:$u"
        u.startsWith("/") -> BASE + u
        else -> "$BASE/$u"
    }

    private fun unescape(s: String) = s.replace("&quot;", "\"").replace("&#039;", "'")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")

    /** Search animedekho for [title]; returns the best-matching series page URL. */
    suspend fun findSeries(title: String): String? = withContext(Dispatchers.IO) {
        val html = get("$BASE/?s=" + java.net.URLEncoder.encode(title, "UTF-8")) ?: return@withContext null
        val links = Regex("""<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).mapNotNull { m ->
                val href = m.groupValues[1]
                val text = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
                if (href.contains("/series/") || href.contains("/movies-hindi/")) href to text else null
            }.toList()
        val want = title.lowercase().trim()
        links.firstOrNull { it.second.lowercase().trim() == want }?.first
            ?: links.firstOrNull { it.second.lowercase().contains(want) || want.contains(it.second.lowercase()) }?.first
            ?: links.firstOrNull()?.first
    }

    /** Real episode list for a series page, each carrying its playable [Episode.pageUrl]. */
    suspend fun detail(seriesUrl: String, fallbackTitle: String, fallbackPoster: String): AnimeDetail? =
        withContext(Dispatchers.IO) {
            val html = get(seriesUrl) ?: return@withContext null
            val snap = runCatching {
                JSONObject(unescape(Regex("""data-snapshot="([^"]+)""").find(html)!!.groupValues[1]))
            }.getOrNull() ?: JSONObject()
            val seriesId = Regex("""data-series="(\d+)""").find(html)?.groupValues?.get(1)?.toIntOrNull()
                ?: snap.optInt("id", 0)
            val seasons = Regex("""data-tabs="([^"]+)""").find(html)?.groupValues?.get(1)
                ?.let(::unescape)?.let { runCatching { JSONArray(it) }.getOrNull() }
                ?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optInt("season") } }
                ?.ifEmpty { null } ?: listOf(1)

            val eps = ArrayList<Episode>()
            for (s in seasons) {
                val arr = runCatching {
                    JSONObject(get("$API/episodes?series=$seriesId&season=$s") ?: "{}")
                        .optJSONArray("episodes")
                }.getOrNull() ?: continue
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val url = abs(e.optString("url"))
                    if (url.isBlank()) continue
                    val n = e.optInt("episode", eps.size + 1)
                    eps.add(
                        Episode(
                            number = n,
                            season = e.optInt("season", s),
                            title = e.optString("name").ifBlank { "Episode $n" },
                            image = abs(e.optString("still")).ifBlank { fallbackPoster },
                            pageUrl = url,
                        )
                    )
                }
            }
            if (eps.isEmpty()) return@withContext null
            eps.sortWith(compareBy({ it.season }, { it.number }))
            val genres = snap.optJSONArray("genres")
                ?.let { g -> (0 until g.length()).map { g.optString(it) } } ?: emptyList()
            AnimeDetail(
                slug = seriesUrl,
                title = snap.optString("title").ifBlank { fallbackTitle },
                image = abs(snap.optString("poster")).ifBlank { fallbackPoster },
                banner = abs(snap.optString("backdrop")).ifBlank { fallbackPoster },
                synopsis = snap.optString("overview", ""),
                genres = genres,
                year = snap.optInt("year", 0),
                rating = 0.0,
                status = "Ongoing",
                type = "TV Series",
                episodes = eps,
            )
        }

    /** Episode page → real stream servers (via the watch API, with an iframe fallback). */
    suspend fun servers(pageUrl: String): List<StreamServer> = withContext(Dispatchers.IO) {
        val html = get(pageUrl) ?: return@withContext emptyList()
        val postId = Regex("""data-title="(\d+)""").find(html)?.groupValues?.get(1)?.toIntOrNull()
        val epId = Regex("""data-term="(\d+)""").find(html)?.groupValues?.get(1)?.toIntOrNull()
        val out = ArrayList<StreamServer>()
        if (postId != null && epId != null) {
            val watch = postJson("$API/watch", JSONObject().put("post_id", postId).put("episode_id", epId))
            val arr = watch?.optJSONArray("sources") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                if (!s.optBoolean("show_embed", true)) continue
                val embed = abs(s.optString("embed"))
                if (embed.isBlank()) continue
                out.add(StreamServer(s.optString("server").ifBlank { "Server ${i + 1}" }, embed, s.optString("quality")))
            }
        }
        if (out.isEmpty()) {
            Regex("""<iframe[^>]+src=["']([^"']+)["']""").findAll(html).forEach {
                val u = abs(it.groupValues[1])
                if (u.startsWith("http")) out.add(StreamServer("Server ${out.size + 1}", u))
            }
        }
        out
    }

    /** Resolve an embed URL down to a direct mp4/m3u8 the player can open. */
    suspend fun resolveEmbed(embed: String, referer: String = "$BASE/"): QualityStream? = withContext(Dispatchers.IO) {
        if (embed.contains(".m3u8", true) || embed.contains(".mp4", true)) {
            return@withContext QualityStream("Auto", embed, embed, null, embed.contains(".m3u8", true))
        }
        val page = get(embed, referer) ?: return@withContext null
        Regex("""https?://[^"'\s\\]+\.m3u8[^"'\s\\]*""").find(page)?.value?.let {
            return@withContext QualityStream("Auto", it, it, null, true)
        }
        Regex("""https?://[^"'\s\\]+\.mp4[^"'\s\\]*""").find(page)?.value?.let {
            return@withContext QualityStream("Auto", it, it, null, false)
        }
        val host = runCatching { java.net.URI(embed).host }.getOrNull() ?: return@withContext null
        val id = embed.substringAfterLast('/').substringBefore('?')
        val json = postForm("https://$host/player/index.php?data=$id&do=getVideo", "hash=$id&r=", embed, "https://$host")
            ?: return@withContext null
        val src = runCatching { JSONObject(json).optString("videoSource") }.getOrDefault("")
        if (src.startsWith("http")) QualityStream("Auto", src, src, null, src.contains(".m3u8", true)) else null
    }
}
