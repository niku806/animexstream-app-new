package com.animexstream.app.data.servers

import com.animexstream.app.data.QualityStream
import com.animexstream.app.data.ResolvedServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object VidstreamServer : ServerFetcher {
    override val name = "Vidstream"

    override suspend fun resolve(playerUrl: String): ResolvedServer? = withContext(Dispatchers.IO) {
        val html = Http.get(playerUrl, referer = "https://animedekho.tv/") ?: return@withContext null

        val ravokUrl = Regex("""https://ravok\.buzz/video/([a-f0-9]+)""").find(html)?.value
            ?: Regex("""https://as-cdn26\.top/video/([a-f0-9]+)""").find(html)?.value
            ?: return@withContext null

        val hash = ravokUrl.substringAfterLast('/')
        val json = Http.postForm(
            "https://ravok.buzz/player/index.php?data=$hash&do=getVideo",
            "hash=$hash&r=",
            mapOf(
                "content-type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "origin" to "https://ravok.buzz",
                "referer" to ravokUrl,
                "x-requested-with" to "XMLHttpRequest"
            )
        ) ?: return@withContext null

        val master = runCatching { JSONObject(json).optString("videoSource") }
            .getOrDefault("").takeIf { it.startsWith("http") }
            ?: return@withContext null

        ResolvedServer(
            name,
            listOf(QualityStream("HD", master, null, null, true))
        )
    }
}
