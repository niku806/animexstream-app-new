package com.animexstream.app.data.servers

import com.animexstream.app.data.QualityStream
import com.animexstream.app.data.ResolvedServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object NeoCdnServer : ServerFetcher {
    override val name = "NeoCDN"

    private const val BASE = "https://animedekho.tv"
    private const val WORKER = "https://young-dust-31f3.reimoto.workers.dev/?url="
    private val ORDER = mapOf("360p" to 1, "480p" to 2, "720p" to 3, "1080p" to 4)

    override suspend fun resolve(playerUrl: String): ResolvedServer? = withContext(Dispatchers.IO) {
        val html = Http.get(playerUrl, referer = "$BASE/") ?: return@withContext null
        val fetchId = Regex("""fetch\.php\?id=([A-Za-z0-9+/=_\-]+)""").find(html)
            ?.groupValues?.get(1) ?: return@withContext null

        val json = Http.get("$BASE/aaa/myth/fetch.php?id=$fetchId", referer = playerUrl)
            ?: return@withContext null

        val sources = runCatching { JSONObject(json).optJSONArray("sources") ?: JSONArray() }
            .getOrDefault(JSONArray())
        if (sources.length() == 0) return@withContext null

        val variants = ArrayList<QualityStream>()
        for (i in 0 until sources.length()) {
            val s = sources.optJSONObject(i) ?: continue
            val direct = s.optString("url")
            if (direct.isBlank()) continue
            variants.add(
                QualityStream(
                    quality = s.optString("type", "HD"),
                    url = WORKER + java.net.URLEncoder.encode(direct, "UTF-8"),
                    directDownloadUrl = direct,
                    audioUrl = null,
                    isHls = false
                )
            )
        }
        if (variants.isEmpty()) return@withContext null
        variants.sortBy { ORDER[it.quality] ?: 99 }
        ResolvedServer(name, variants)
    }
}
