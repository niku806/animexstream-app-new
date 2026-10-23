package com.animexstream.app.data

import android.content.Context
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
 * The three content ecosystems the app can run in.
 * Selection is persisted in SharedPreferences (ProviderPrefs) and switches
 * BOTH the catalogue source AND the accent theme used across the UI.
 */
enum class Provider(
    val id: String,
    val displayName: String,
    val accent: Long,      // primary accent colour used by the UI for this provider
    val accentSoft: Long,  // softer variant (chips, glows)
    val tagline: String,
) {
    ANIMEXSTREAM("animexstream", "AnimeXstream", 0xFF9D5CFF, 0xFFA78BFA, "Original library"),
    ANIMESTREAM("animestream", "AnimeStream", 0xFFCAF979, 0xFFE4FFA3, "Trending now"),
    ZANGETSU("zangetsu", "Zangetsu", 0xFFFF4D57, 0xFFFF8A90, "Top rated");

    companion object {
        fun fromId(id: String?): Provider =
            entries.firstOrNull { it.id == id } ?: ANIMEXSTREAM
    }
}

/** Persisted provider choice — survives restarts. */
object ProviderPrefs {
    private const val PREFS = "ax_provider_prefs"
    private const val KEY = "selected_provider"
    @Volatile private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun selected(): Provider = Provider.fromId(prefs?.getString(KEY, null))

    fun set(provider: Provider) {
        prefs?.edit()?.putString(KEY, provider.id)?.apply()
    }
}

/** Common contract every content provider implements. */
interface ContentProvider {
    val provider: Provider
    suspend fun loadIndex(force: Boolean = false): List<Anime>
    suspend fun loadDetail(slug: String): AnimeDetail?
    suspend fun search(query: String): List<Anime>
    suspend fun resolveServers(episode: Episode): List<ResolvedServer>
}

/** Shared HTTP client + AniList GraphQL helpers for the two extra providers. */
internal object ProviderHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val ANILIST = "https://graphql.anilist.co"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Run an AniList GraphQL query and return the parsed `data` object. */
    fun anilist(query: String, variables: Map<String, Any?> = emptyMap()): JSONObject? {
        return try {
            val payload = JSONObject().apply {
                put("query", query)
                put("variables", JSONObject(variables as Map<*, *>))
            }.toString()
            val req = Request.Builder()
                .url(ANILIST)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(payload.toRequestBody(jsonType))
                .build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return null
                val text = r.body?.string() ?: return null
                JSONObject(text).optJSONObject("data")
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** Maps an AniList `media` JSON object to our [Anime] model. */
internal fun JSONObject.toAnime(): Anime? {
    val id = optInt("id", 0)
    if (id == 0) return null
    val titleObj = optJSONObject("title")
    val title = titleObj?.optString("english")?.takeIf { it.isNotBlank() && it != "null" }
        ?: titleObj?.optString("romaji")?.takeIf { it.isNotBlank() }
        ?: optString("title")
    if (title.isBlank()) return null
    val genres = ArrayList<String>()
    optJSONArray("genres")?.let { g -> for (i in 0 until g.length()) genres.add(g.optString(i)) }
    val cover = optJSONObject("coverImage")?.optString("large")
        ?: optJSONObject("coverImage")?.optString("extraLarge") ?: ""
    val year = optJSONObject("startDate")?.optInt("year", 0) ?: 0
    val score = optDouble("averageScore", 0.0)
    // Real streaming platforms from AniList externalLinks (site + url).
    val platforms = ArrayList<String>()
    optJSONArray("externalLinks")?.let { links ->
        for (i in 0 until links.length()) {
            val l = links.optJSONObject(i) ?: continue
            val site = l.optString("site")
            if (site.isNotBlank()) platforms.add(site)
        }
    }
    return Anime(
        id = id.toString(),
        title = title,
        poster = cover,
        genres = genres,
        year = year,
        totalEpisodes = optInt("episodes", 0),
        synopsis = optString("description").replace(Regex("<[^>]+>"), "").trim(),
        rating = if (score > 0) score / 10.0 else 0.0,
        platforms = platforms,
    )
}

/**
 * Shared implementation for the AniList-backed providers (Animestream, Zangetsu).
 * The catalogue comes from AniList's public GraphQL API; episodes are synthesised
 * from the metadata and, where a same-titled entry exists in the AnimeXstream
 * library, its real playable servers are reused so playback still works.
 */
abstract class AniListProvider : ContentProvider {

    protected abstract val sort: String
    protected abstract val extraFilter: String

    @Volatile private var indexCache: List<Anime>? = null
    private val detailCache = HashMap<String, AnimeDetail>()

    private fun buildQuery(page: Int): String = """
        query {
          Page(page: $page, perPage: 30) {
            media(type: ANIME, sort: $sort, isAdult: false${extraFilter}) {
              id
              title { romaji english }
              coverImage { large extraLarge }
              genres
              averageScore
              startDate { year }
              episodes
              description(asHtml: false)
              externalLinks { site url }
            }
          }
        }
    """.trimIndent()

    override suspend fun loadIndex(force: Boolean): List<Anime> = withContext(Dispatchers.IO) {
        if (!force) indexCache?.let { if (it.isNotEmpty()) return@withContext it }
        val out = ArrayList<Anime>()
        val data = ProviderHttp.anilist(buildQuery(1)) ?: return@withContext (indexCache ?: emptyList())
        val media = data.optJSONObject("Page")?.optJSONArray("media") ?: JSONArray()
        for (i in 0 until media.length()) {
            media.optJSONObject(i)?.toAnime()?.let { out.add(it) }
        }
        if (out.isNotEmpty()) indexCache = out
        indexCache ?: emptyList()
    }

    override suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = """
            query(${'$'}search: String) {
              Page(page: 1, perPage: 30) {
                media(type: ANIME, search: ${'$'}search, isAdult: false, sort: SEARCH_MATCH) {
                  id title { romaji english } coverImage { large extraLarge }
                  genres averageScore startDate { year } episodes description(asHtml: false)
                  externalLinks { site url }
                }
              }
            }
        """.trimIndent()
        val data = ProviderHttp.anilist(q, mapOf("search" to query)) ?: return@withContext emptyList()
        val media = data.optJSONObject("Page")?.optJSONArray("media") ?: JSONArray()
        val out = ArrayList<Anime>()
        for (i in 0 until media.length()) media.optJSONObject(i)?.toAnime()?.let { out.add(it) }
        out
    }

    override suspend fun loadDetail(slug: String): AnimeDetail? = withContext(Dispatchers.IO) {
        detailCache[slug]?.let { return@withContext it }
        val id = slug.toIntOrNull() ?: return@withContext null
        val q = """
            query(${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                id title { romaji english } coverImage { large extraLarge }
                bannerImage genres averageScore startDate { year } episodes
                description(asHtml: false) status format
              }
            }
        """.trimIndent()
        val data = ProviderHttp.anilist(q, mapOf("id" to id)) ?: return@withContext null
        val m = data.optJSONObject("Media") ?: return@withContext null
        val anime = m.toAnime() ?: return@withContext null
        val total = anime.totalEpisodes.coerceAtLeast(1)
        val banner = m.optString("bannerImage").ifBlank { anime.poster }
        val status = when (m.optString("status")) {
            "FINISHED" -> "Completed"
            "RELEASING" -> "Ongoing"
            "NOT_YET_RELEASED" -> "Upcoming"
            else -> "Ongoing"
        }
        val type = when (m.optString("format")) {
            "MOVIE" -> "Movie"
            "TV" -> "TV Series"
            "OVA" -> "OVA"
            else -> "Anime"
        }
        // Reuse real playable servers from the AnimeXstream library when a
        // same-titled entry exists, so video playback still works.
        val bridge = runCatching { AnimeRepository.bridgeDetailForTitle(anime.title) }.getOrNull()
        val episodes = (1..total).map { n ->
            val bridged = bridge?.episodes?.firstOrNull { it.number == n }
            Episode(
                number = n,
                season = 1,
                title = "Episode $n",
                image = anime.poster,
                description = "",
                servers = bridged?.servers ?: emptyList(),
                downloads = bridged?.downloads ?: emptyList(),
            )
        }
        val detail = AnimeDetail(
            slug = slug,
            title = anime.title,
            image = anime.poster,
            banner = banner,
            synopsis = anime.synopsis,
            genres = anime.genres,
            year = anime.year,
            rating = anime.rating,
            status = status,
            type = type,
            episodes = episodes,
        )
        detailCache[slug] = detail
        detail
    }

    override suspend fun resolveServers(episode: Episode): List<ResolvedServer> =
        ServerResolver.resolveAll(episode.servers, cacheKey = "${provider.id}:${episode.number}")

    /** Drop this provider's cached index + details (used on provider switch). */
    fun clearCache() {
        indexCache = null
        detailCache.clear()
    }
}

/** Animestream — trending catalogue from AniList. */
object AnimestreamProvider : AniListProvider() {
    override val provider = Provider.ANIMESTREAM
    override val sort = "TRENDING_DESC"
    override val extraFilter = ""
}

/** Zangetsu — top-rated catalogue from AniList. */
object ZangetsuProvider : AniListProvider() {
    override val provider = Provider.ZANGETSU
    // Same catalogue as Animestream — the user wants identical data on both.
    override val sort = "TRENDING_DESC"
    override val extraFilter = ""
}

/** AnimeXstream — the original GitHub-hosted library (unchanged logic). */
object AnimeXstreamProvider : ContentProvider {
    override val provider = Provider.ANIMEXSTREAM
    override suspend fun loadIndex(force: Boolean) = AnimeRepository.loadIndex(force)
    override suspend fun loadDetail(slug: String) = AnimeRepository.loadDetail(slug)
    override suspend fun search(query: String): List<Anime> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        return AnimeRepository.loadIndex().filter {
            it.title.lowercase().contains(q) || it.genres.any { g -> g.lowercase().contains(q) }
        }
    }
    override suspend fun resolveServers(episode: Episode) =
        ServerResolver.resolveAll(episode.servers, cacheKey = "ax:${episode.number}")
}

/** Routes every call to the currently-selected provider. */
object ContentRepository {
    fun current(): ContentProvider = when (ProviderPrefs.selected()) {
        Provider.ANIMEXSTREAM -> AnimeXstreamProvider
        Provider.ANIMESTREAM -> AnimestreamProvider
        Provider.ZANGETSU -> ZangetsuProvider
    }

    /** Drops every provider's in-memory caches so a switch never leaks old data. */
    fun clearAll() {
        AnimeRepository.clearCache()
        ServerResolver.clearCache()
        AnimestreamProvider.clearCache()
        ZangetsuProvider.clearCache()
    }

    suspend fun loadIndex(force: Boolean = false) = current().loadIndex(force)
    suspend fun loadDetail(slug: String) = current().loadDetail(slug)
    suspend fun search(query: String) = current().search(query)
    suspend fun resolveServers(episode: Episode) = current().resolveServers(episode)

    /** Providers whose stream sources can be chosen inside the player. */
    fun playerProviderChoices(): List<Provider> = when (ProviderPrefs.selected()) {
        Provider.ANIMEXSTREAM -> listOf(Provider.ANIMEXSTREAM)
        else -> listOf(ProviderPrefs.selected())
    }
}

/** Persisted first-run onboarding flag (Zangetsu-style intro pages). */
object OnboardingPrefs {
    private const val PREFS = "ax_onboarding_prefs"
    private const val KEY = "onboarding_done"
    @Volatile private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun isDone(): Boolean = prefs?.getBoolean(KEY, false) == true

    fun markDone() {
        prefs?.edit()?.putBoolean(KEY, true)?.apply()
    }
}
