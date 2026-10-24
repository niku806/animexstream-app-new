package com.animexstream.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object HlsDownloader {

    // Parallel download pool — 6 concurrent requests (fast but safe)
    private const val PARALLEL = 6

    private val client = OkHttpClient.Builder()
        .proxy(java.net.Proxy.NO_PROXY)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36"

    private fun fetchBytes(url: String, referer: String? = null): ByteArray? = try {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        if (referer != null) b.header("Referer", referer)
        client.newCall(b.build()).execute().use { r ->
            if (r.isSuccessful) r.body?.bytes() else null
        }
    } catch (_: Exception) { null }

    private fun fetchText(url: String, referer: String? = null): String? = try {
        fetchBytes(url, referer)?.toString(Charsets.UTF_8)
    } catch (_: Exception) { null }

    suspend fun download(
        masterUrl: String,
        outputDir: File,
        videoFileName: String,
        audioFileName: String,
        onProgress: (downloaded: Long, total: Long, speedBps: Long) -> Unit,
        onComplete: (videoPath: String?, audioPath: String?) -> Unit,
        onError: (String) -> Unit,
        onPaused: () -> Unit,
    ) = withContext(Dispatchers.IO) {
        try {
            outputDir.mkdirs()
            val masterBody = fetchText(masterUrl) ?: return@withContext onError("Failed to fetch master")
            val baseUrl = masterUrl.substringBeforeLast('/') + "/"

            var bestVariantUrl: String? = null
            var bestBandwidth = -1L
            var audioUri: String? = null

            val lines = masterBody.split("\n")
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.startsWith("#EXT-X-MEDIA:") && line.contains("TYPE=AUDIO")) {
                    val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                    if (!uri.isNullOrBlank() && audioUri == null) {
                        audioUri = if (uri.startsWith("http")) uri else baseUrl + uri
                    }
                }
                if (line.startsWith("#EXT-X-STREAM-INF:")) {
                    val bw = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    var j = i + 1
                    while (j < lines.size && (lines[j].isBlank() || lines[j].trim().startsWith("#"))) j++
                    if (j < lines.size) {
                        val variant = lines[j].trim()
                        val variantUrl = if (variant.startsWith("http")) variant else baseUrl + variant
                        if (bw > bestBandwidth) { bestBandwidth = bw; bestVariantUrl = variantUrl }
                    }
                }
                i++
            }

            val mediaPlaylistUrl = bestVariantUrl ?: masterUrl
            val videoFile = File(outputDir, videoFileName)

            val videoOk = downloadMediaPlaylistFast(mediaPlaylistUrl, videoFile, onProgress, onPaused)
            if (!videoOk) return@withContext onPaused()

            var audioPath: String? = null
            if (audioUri != null) {
                val audioFile = File(outputDir, audioFileName)
                if (downloadMediaPlaylistFast(audioUri, audioFile, onProgress, onPaused)) {
                    audioPath = audioFile.absolutePath
                }
            }

            onComplete(videoFile.absolutePath, audioPath)
        } catch (_: PausedException) {
            onPaused()
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }

    /**
     * Parallel .ts segment download — 6 concurrent fetches.
     * Downloads out-of-order in chunks, then writes them sequentially → ~6x faster.
     * Uses a sliding window: buffer of PARALLEL pending futures.
     */
    private suspend fun downloadMediaPlaylistFast(
        playlistUrl: String,
        outputFile: File,
        onProgress: (Long, Long, Long) -> Unit,
        onPaused: () -> Unit,
    ): Boolean = coroutineScope {
        val body = fetchText(playlistUrl) ?: throw Exception("Failed playlist")
        val baseUrl = playlistUrl.substringBeforeLast('/') + "/"

        val segments = mutableListOf<String>()
        var initSegment: String? = null
        for (line in body.split("\n")) {
            val t = line.trim()
            if (t.startsWith("#EXT-X-MAP:")) {
                initSegment = Regex("URI=\"([^\"]+)\"").find(t)?.groupValues?.get(1)
            } else if (t.isNotEmpty() && !t.startsWith("#")) {
                val seg = if (t.startsWith("http")) t else baseUrl + t
                segments.add(seg)
            }
        }
        if (segments.isEmpty()) throw Exception("No segments")

        val total = segments.size.toLong()
        val semaphore = Semaphore(PARALLEL)
        val done = AtomicLong(0)
        var lastTick = System.currentTimeMillis()
        var lastCount = 0L

        FileOutputStream(outputFile).use { out ->
            // init segment pehle
            if (initSegment != null) {
                val segUrl = if (initSegment.startsWith("http")) initSegment else baseUrl + initSegment
                val bytes = fetchBytes(segUrl) ?: throw Exception("Init fetch failed")
                out.write(bytes)
            }

            // Sliding window: 6 chunks fetch karo parallel, phir next window
            var idx = 0
            while (idx < segments.size) {
                val windowEnd = minOf(idx + PARALLEL, segments.size)
                val chunk = segments.subList(idx, windowEnd)

                val results = chunk.map { segUrl ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            // pause check
                            fetchBytes(segUrl)
                        }
                    }
                }.awaitAll()

                for (bytes in results) {
                    if (bytes == null) throw Exception("Segment fetch failed")
                    out.write(bytes)
                    done.incrementAndGet()
                }

                // Progress
                val now = System.currentTimeMillis()
                if (now - lastTick > 400) {
                    val elapsed = (now - lastTick).coerceAtLeast(1)
                    val n = done.get()
                    val speed = ((n - lastCount) * 1000 / elapsed)
                    onProgress(n, total, speed)
                    lastTick = now
                    lastCount = n
                }

                idx = windowEnd
            }
        }

        onProgress(total, total, 0)
        true
    }
}
