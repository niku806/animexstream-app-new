package com.animexstream.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LiveChannel(
    val name: String,
    val tvgId: String,
    val quality: String,
    val url: String,
    val logoUrl: String
)

object LiveTvRepository {
    private const val URL = "https://raw.githubusercontent.com/niku806/DUMPER/main/live-tv.json"
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
    private var cache: List<LiveChannel>? = null

    suspend fun getChannels(): List<LiveChannel> = withContext(Dispatchers.IO) {
        if (cache != null) return@withContext cache!!
        try {
            val req = Request.Builder().url(URL).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext emptyList()
                val text = r.body?.string() ?: return@withContext emptyList()
                val root = JSONObject(text)
                val arr = root.optJSONArray("channels") ?: return@withContext emptyList()
                val list = ArrayList<LiveChannel>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(LiveChannel(
                        name = o.optString("name"),
                        tvgId = o.optString("tvg_id"),
                        quality = o.optString("quality"),
                        url = o.optString("url"),
                        logoUrl = o.optString("logo_url")
                    ))
                }
                cache = list
                list
            }
        } catch (_: Exception) { emptyList() }
    }
}
