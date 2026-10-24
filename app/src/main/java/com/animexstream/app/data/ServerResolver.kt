package com.animexstream.app.data

import com.animexstream.app.data.servers.Mirror720pServer
import com.animexstream.app.data.servers.NeoCdnServer
import com.animexstream.app.data.servers.ServerFetcher
import com.animexstream.app.data.servers.VidstreamServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

object ServerResolver {

    private val fetchers: List<ServerFetcher> = listOf(
        NeoCdnServer, Mirror720pServer, VidstreamServer
    )

    // In-memory cache: episode-key → resolved servers (session ke liye)
    private val cache = HashMap<String, List<ResolvedServer>>()

    suspend fun resolveAll(servers: List<StreamServer>, cacheKey: String? = null): List<ResolvedServer> =
        withContext(Dispatchers.IO) {
            // Cache hit → instant
            if (cacheKey != null) cache[cacheKey]?.let { return@withContext it }

            // Parallel resolve — sab servers ek saath try hote hain, ~2x fast
            val tasks = servers.mapNotNull { s ->
                val fetcher = fetchers.firstOrNull { it.name.equals(s.name, true) } ?: return@mapNotNull null
                async {
                    runCatching { fetcher.resolve(s.url) }.getOrNull()
                }
            }
            val results = tasks.awaitAll().filterNotNull().filter { it.variants.isNotEmpty() }

            if (cacheKey != null) cache[cacheKey] = results
            results
        }

    fun clearCache() = cache.clear()
}

data class QualityStream(
    val quality: String,
    val url: String,
    val directDownloadUrl: String?,
    val audioUrl: String?,
    val isHls: Boolean,
    // Extra request headers the source needs (Referer/Cookie/User-Agent).
    val headers: Map<String, String> = emptyMap(),
)

data class ResolvedServer(
    val name: String,
    val variants: List<QualityStream>,
)
