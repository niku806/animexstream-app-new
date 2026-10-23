package com.animexstream.app.data.servers

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36"

    fun get(url: String, referer: String? = null, extra: Map<String, String> = emptyMap()): String? = try {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        referer?.let { b.header("Referer", it) }
        extra.forEach { (k, v) -> b.header(k, v) }
        client.newCall(b.get().build()).execute().use { r ->
            if (r.isSuccessful || r.code == 302) r.body?.string() else null
        }
    } catch (_: Exception) { null }

    fun postForm(url: String, body: String, headers: Map<String, String> = emptyMap()): String? = try {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        headers.forEach { (k, v) -> b.header(k, v) }
        b.post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
        client.newCall(b.build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    } catch (_: Exception) { null }
}
