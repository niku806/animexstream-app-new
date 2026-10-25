package com.animexstream.app.data

import android.content.Context

/** Single content ecosystem: MovieBox. */
enum class Provider(
    val id: String,
    val displayName: String,
    val accent: Long,
    val accentSoft: Long,
    val tagline: String,
) {
    MOVIEBOX("animexstream", "AnimeXstream", 0xFF9D5CFF, 0xFFA78BFA, "Movies - Series - Anime");

    companion object {
        fun fromId(id: String?): Provider = MOVIEBOX
    }
}

object ProviderPrefs {
    private const val PREFS = "ax_provider_prefs"
    private const val KEY = "selected_provider"
    @Volatile private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
        prefs?.edit()?.putString(KEY, Provider.MOVIEBOX.id)?.apply()
    }

    fun selected(): Provider = Provider.MOVIEBOX

    fun set(provider: Provider) {
        prefs?.edit()?.putString(KEY, Provider.MOVIEBOX.id)?.apply()
    }
}

interface ContentProvider {
    val provider: Provider
    suspend fun loadIndex(force: Boolean = false): List<Anime>
    suspend fun loadDetail(slug: String): AnimeDetail?
    suspend fun search(query: String): List<Anime>
    suspend fun resolveServers(episode: Episode): List<ResolvedServer>
}

object MovieBoxProvider : ContentProvider {
    override val provider = Provider.MOVIEBOX

    override suspend fun loadIndex(force: Boolean): List<Anime> {
        val seen = LinkedHashMap<String, Anime>()
        runCatching { MovieBox.homeRails() }.getOrDefault(emptyList()).forEach { (_, list) ->
            list.forEach { seen.putIfAbsent(it.id, it) }
        }
        if (seen.isEmpty()) {
            for (kw in listOf("movie", "series", "anime", "hindi", "action", "comedy", "drama")) {
                runCatching { MovieBox.search(kw) }.getOrDefault(emptyList())
                    .forEach { seen.putIfAbsent(it.id, it) }
            }
        }
        return seen.values.toList()
    }

    override suspend fun loadDetail(slug: String) = MovieBox.detail(slug)
    override suspend fun search(query: String) = MovieBox.search(query)

    override suspend fun resolveServers(episode: Episode): List<ResolvedServer> {
        if (episode.pageUrl.startsWith("moviebox:")) {
            val r = runCatching { MovieBox.streams(episode.pageUrl) }.getOrDefault(emptyList())
            if (r.isNotEmpty()) return r
        }
        return emptyList()
    }

    suspend fun browse(kind: String, page: Int = 1) = MovieBox.browseCategory(kind, page)
}

object ContentRepository {
    fun current(): ContentProvider = MovieBoxProvider

    fun clearAll() {
        MovieBox.clearCache()
        ServerResolver.clearCache()
    }

    suspend fun browseMovieBox(kind: String, page: Int = 1) = MovieBox.browseCategory(kind, page)

    suspend fun loadIndex(force: Boolean = false) = MovieBoxProvider.loadIndex(force)
    suspend fun loadDetail(slug: String) = MovieBoxProvider.loadDetail(slug)
    suspend fun search(query: String) = MovieBoxProvider.search(query)
    suspend fun resolveServers(episode: Episode) = MovieBoxProvider.resolveServers(episode)
}

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
