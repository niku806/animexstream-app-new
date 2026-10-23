package com.animexstream.app.data.servers

import com.animexstream.app.data.QualityStream
import com.animexstream.app.data.ResolvedServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object Mirror720pServer : ServerFetcher {
    override val name = "Mirror-720p"

    private const val BASE = "https://animedekho.tv"

    override suspend fun resolve(playerUrl: String): ResolvedServer? = withContext(Dispatchers.IO) {
        val html = Http.get(playerUrl, referer = "$BASE/") ?: return@withContext null
        val iframe = Regex("""<iframe[^>]+src=["']([^"']+)["']""")
            .find(html)?.groupValues?.get(1) ?: return@withContext null

        val mirrorHtml = Http.get(iframe, referer = playerUrl) ?: return@withContext null

        val candidates = Regex("""value=["'](https?://[^"']+)["']""")
            .findAll(mirrorHtml).map { it.groupValues[1] }.toList()
            .ifEmpty { listOf(iframe) }

        for (cand in candidates.take(3)) {
            val stream = extractStream(cand) ?: continue
            return@withContext ResolvedServer(name, listOf(stream))
        }
        null
    }

    private fun extractStream(embedUrl: String): QualityStream? {
        val host = runCatching { java.net.URI(embedUrl).host }.getOrNull() ?: return null
        val origin = "https://$host"

        val page = Http.get(embedUrl, referer = "$origin/") ?: return null

        Regex("""https?://[^"'\s\\]+\.m3u8[^"'\s\\]*""").find(page)?.value?.let {
            return QualityStream("720p", it, null, null, true)
        }
        Regex(""""file"\s*:\s*"([^"]+)"""").find(page)?.groupValues?.get(1)?.let { f ->
            if (f.contains(".m3u8")) return QualityStream("720p", f, null, null, true)
        }

        val fileId = Regex("""file_id['"]?\s*,\s*['"](\d+)""").find(page)?.groupValues?.get(1)
            ?: embedUrl.substringAfterLast('/')

        val json = Http.postForm(
            "$origin/player/index.php?data=$fileId&do=getVideo",
            "hash=$fileId&r=",
            mapOf(
                "content-type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "origin" to origin,
                "referer" to embedUrl,
                "x-requested-with" to "XMLHttpRequest"
            )
        ) ?: return null

        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null

        root.optString("videoSource").takeIf { it.startsWith("http") }?.let {
            return QualityStream("720p", it, null, null, true)
        }

        val arr = root.optJSONArray("sources") ?: JSONArray()
        val best = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .maxByOrNull { o -> o.optString("label").filter { it.isDigit() }.toIntOrNull() ?: 0 }

        val file = best?.optString("file")?.takeIf { it.startsWith("http") }
            ?: return null
        return QualityStream("720p", file, null, null, file.contains(".m3u8"))
    }
}
