package com.animexstream.app.data

import com.animexstream.app.Secrets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AnimeRepository {

    private val GH_OWNER: String get() = Secrets.GH_OWNER
    private val GH_REPO: String get() = Secrets.GH_REPO
    private const val BRANCH = "main"
    private const val DATA_DIR = "anime-data"

    private val client = OkHttpClient.Builder()
        .proxy(java.net.Proxy.NO_PROXY)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var indexCache: List<Anime>? = null
    private val detailCache = HashMap<String, AnimeDetail>()

    private fun rawUrl(path: String) =
        "https://raw.githubusercontent.com/$GH_OWNER/$GH_REPO/$BRANCH/$path"

    private fun get(url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", "AnimeXstream/4.0").build()
        client.newCall(req).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    } catch (_: Exception) { null }

    suspend fun loadIndex(force: Boolean = false): List<Anime> = withContext(Dispatchers.IO) {
        if (!force) indexCache?.let { if (it.isNotEmpty()) return@withContext it }

        val text = get(rawUrl("$DATA_DIR/index.json")) ?: return@withContext emptyList()

        val list = try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                if (id.isBlank()) return@mapNotNull null
                val genres = ArrayList<String>()
                o.optJSONArray("genres")?.let { g ->
                    for (j in 0 until g.length()) genres.add(g.optString(j))
                }
                Anime(
                    id = id,
                    title = o.optString("title"),
                    poster = o.optString("poster"),
                    genres = genres,
                    year = o.optString("year").toIntOrNull() ?: 0,
                    totalEpisodes = o.optInt("total_episodes", 0),
                    synopsis = o.optString("synopsis", ""),
                    rating = o.optDouble("rating", 0.0),
                )
            }
        } catch (_: Exception) { emptyList() }

        if (list.isNotEmpty()) indexCache = list
        indexCache ?: emptyList()
    }

    suspend fun allGenres(): List<String> = withContext(Dispatchers.IO) {
        loadIndex().flatMap { it.genres }.map { it.trim() }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key }.take(20)
    }

    suspend fun loadDetail(slug: String): AnimeDetail? = withContext(Dispatchers.IO) {
        detailCache[slug]?.let { return@withContext it }

        // slug may have URL-encoded chars; the file name on GitHub is the id from index
        val encoded = java.net.URLEncoder.encode(slug, "UTF-8").replace("+", "%20")
        val text = get(rawUrl("$DATA_DIR/detail/$encoded.json"))
            ?: get(rawUrl("$DATA_DIR/detail/$slug.json"))
            ?: return@withContext null

        val detail = try { parseDetail(slug, text) } catch (_: Exception) { null }
        if (detail != null && detail.episodes.isNotEmpty()) detailCache[slug] = detail
        detail
    }

    private fun parseDetail(slug: String, text: String): AnimeDetail {
        val root = JSONObject(text)
        val title = root.optString("title")
        val image = root.optString("image")
        val genres = ArrayList<String>()
        root.optJSONArray("genres")?.let { g -> for (i in 0 until g.length()) genres.add(g.optString(i)) }

        val epsArr = root.optJSONArray("episodes_data") ?: JSONArray()
        val eps = ArrayList<Episode>()
        for (i in 0 until epsArr.length()) {
            val e = epsArr.optJSONObject(i) ?: continue

            val servers = ArrayList<StreamServer>()
            e.optJSONArray("streaming_servers")?.let { sArr ->
                for (j in 0 until sArr.length()) {
                    val s = sArr.optJSONObject(j) ?: continue
                    val name = s.optString("name")
                    // Only 3 servers supported
                    if (name !in listOf("Mirror-720p", "NeoCDN", "Vidstream")) continue
                    servers.add(StreamServer(
                        name = name,
                        url = s.optString("url"),
                        quality = "",
                        type = ""
                    ))
                }
            }

            val downloads = ArrayList<DownloadLink>()
            e.optJSONArray("download_links")?.let { dArr ->
                for (j in 0 until dArr.length()) {
                    val d = dArr.optJSONObject(j) ?: continue
                    val label = d.optString("label")
                    if (label !in listOf("Mirror-720p", "NeoCDN", "Vidstream")) continue
                    downloads.add(DownloadLink(
                        name = label,
                        url = d.optString("decoded").ifBlank { d.optString("url") },
                        quality = d.optString("quality"),
                        size = ""
                    ))
                }
            }

            eps.add(Episode(
                number = e.optString("episode", "1").toIntOrNull() ?: e.optInt("episode", 1),
                season = e.optString("season", "1").toIntOrNull() ?: 1,
                title = e.optString("title"),
                image = e.optString("image").ifBlank { image },
                description = e.optString("description"),
                servers = servers,
                downloads = downloads
            ))
        }
        eps.sortWith(compareBy({ it.season }, { it.number }))

        return AnimeDetail(
            slug = slug,
            title = title,
            image = image,
            banner = image,
            synopsis = root.optString("description"),
            genres = genres,
            year = root.optString("year").toIntOrNull() ?: 0,
            rating = 0.0,
            status = "Ongoing",
            type = "TV Series",
            episodes = eps
        )
    }

    suspend fun bridgeDetailForTitle(title: String): AnimeDetail? = withContext(Dispatchers.IO) {
        val needle = title.trim().lowercase()
        if (needle.isBlank()) return@withContext null
        val idx = loadIndex()
        val match = idx.firstOrNull { it.title.trim().lowercase() == needle }
            ?: idx.firstOrNull { it.title.trim().lowercase().contains(needle) }
            ?: idx.firstOrNull { needle.contains(it.title.trim().lowercase()) && it.title.length >= 3 }
            ?: return@withContext null
        loadDetail(match.id)
    }

    fun clearCache() { indexCache = null; detailCache.clear() }
}

