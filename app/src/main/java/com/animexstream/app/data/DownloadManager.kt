package com.animexstream.app.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

data class DownloadTask(
    val id: String,
    val animeId: String,
    val animeTitle: String,
    val episodeNum: Int,
    val serverName: String,
    val quality: String,
    val sourceUrl: String,
    val isHls: Boolean,
    val videoPath: String? = null,
    val audioPath: String? = null,
    val posterPath: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBps: Long = 0L,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

object DownloadManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private var appCtx: Context? = null

    fun init(context: Context) {
        appCtx = context.applicationContext
        _tasks.value = loadFromPrefs()
    }

    fun get(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    fun enqueue(
        context: Context,
        server: ResolvedServer,
        quality: QualityStream,
        animeId: String,
        animeTitle: String,
        episodeNum: Int,
        posterUrl: String?
    ) {
        val id = "${animeId}_e${episodeNum}_${server.name}_${quality.quality}_${System.currentTimeMillis()}"
        val task = DownloadTask(
            id = id, animeId = animeId, animeTitle = animeTitle,
            episodeNum = episodeNum, serverName = server.name, quality = quality.quality,
            sourceUrl = quality.url, isHls = quality.isHls,
        )
        addTask(task)
        scope.launch {
            val p = saveThumb(context, id, posterUrl)
            if (p != null) updateTask(get(task.id)?.copy(posterPath = p) ?: task)
        }
        startDownload(task)
    }

    private fun saveThumb(context: Context, id: String, url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val dir = File(context.getExternalFilesDir(null), "downloads").apply { mkdirs() }
            val f = File(dir, "${id.replace(Regex("[^A-Za-z0-9._-]"), "_")}_thumb.jpg")
            if (f.exists() && f.length() > 1024) return f.absolutePath
            val req = okhttp3.Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            okhttp3.OkHttpClient.Builder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()
                .newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return null
                    r.body?.byteStream()?.use { input -> f.outputStream().use { input.copyTo(it) } }
                }
            if (f.exists() && f.length() > 512) f.absolutePath else null
        } catch (_: Exception) { null }
    }

    fun resume(id: String) {
        val task = get(id) ?: return
        if (task.status == DownloadStatus.DOWNLOADING) return
        val reset = task.copy(status = DownloadStatus.QUEUED, error = null)
        updateTask(reset)
        startDownload(reset)
    }

    fun pause(id: String) {
        val task = get(id) ?: return
        if (task.status != DownloadStatus.DOWNLOADING) return
        updateTask(task.copy(status = DownloadStatus.PAUSED))
    }

    fun delete(id: String) {
        val task = get(id) ?: return
        task.videoPath?.let { runCatching { File(it).delete() } }
        task.audioPath?.let { runCatching { File(it).delete() } }
        task.posterPath?.let { runCatching { File(it).delete() } }
        _tasks.value = _tasks.value.filterNot { it.id == id }
        persist()
    }

    fun cancel(id: String) = delete(id)

    private fun addTask(task: DownloadTask) {
        _tasks.value = _tasks.value + task
        persist()
    }

    private fun updateTask(task: DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == task.id) task else it }
        persist()
    }

    private fun startDownload(task: DownloadTask) {
        val ctx = appCtx ?: return
        scope.launch {
            updateTask(task.copy(status = DownloadStatus.DOWNLOADING, progress = 0))
            try {
                val dir = File(ctx.getExternalFilesDir(null), "downloads").apply { mkdirs() }
                val baseName = "${task.animeId}_e${task.episodeNum}_${task.serverName}_${task.quality}"
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")

                if (task.isHls) {
                    downloadHls(task, dir, baseName)
                } else {
                    downloadDirect(task, dir, baseName)
                }
            } catch (e: Exception) {
                updateTask(get(task.id)?.copy(status = DownloadStatus.FAILED, error = e.message ?: "Error") ?: task)
            }
        }
    }

    private fun downloadDirect(task: DownloadTask, dir: File, baseName: String) {
        val videoFile = File(dir, "$baseName.mp4")
        val url = task.sourceUrl

        try {
            val client = okhttp3.OkHttpClient.Builder()
                
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val req = okhttp3.Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body ?: throw Exception("Empty body")
            val total = body.contentLength()
            var downloaded = 0L
            var lastTick = System.currentTimeMillis()
            var lastBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(videoFile).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val cur = get(task.id)
                        if (cur?.status == DownloadStatus.PAUSED) throw PausedException()
                        val r = input.read(buf)
                        if (r < 0) break
                        out.write(buf, 0, r)
                        downloaded += r
                        val now = System.currentTimeMillis()
                        if (now - lastTick > 400) {
                            val elapsed = (now - lastTick).coerceAtLeast(1)
                            val speed = ((downloaded - lastBytes) * 1000 / elapsed)
                            val prog = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                            updateTask(get(task.id)?.copy(
                                downloadedBytes = downloaded, totalBytes = total,
                                progress = prog, speedBps = speed
                            ) ?: task)
                            lastTick = now
                            lastBytes = downloaded
                        }
                    }
                }
            }
            updateTask(get(task.id)?.copy(
                status = DownloadStatus.COMPLETED,
                progress = 100,
                videoPath = videoFile.absolutePath,
                totalBytes = total,
            ) ?: task)
        } catch (_: PausedException) {
            updateTask(get(task.id)?.copy(status = DownloadStatus.PAUSED) ?: task)
        } catch (e: Exception) {
            updateTask(get(task.id)?.copy(status = DownloadStatus.FAILED, error = e.message) ?: task)
        }
    }

    private fun downloadHls(task: DownloadTask, dir: File, baseName: String) {
        runBlocking {
            HlsDownloader.download(
                masterUrl = task.sourceUrl,
                outputDir = dir,
                videoFileName = "$baseName.ts",
                audioFileName = "${baseName}_audio.ts",
                onProgress = { downloaded, total, speed ->
                    val cur = get(task.id)
                    if (cur?.status == DownloadStatus.PAUSED) throw PausedException()
                    val prog = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    updateTask(cur?.copy(
                        downloadedBytes = downloaded, totalBytes = total,
                        progress = prog, speedBps = speed
                    ) ?: task)
                },
                onComplete = { vPath, aPath ->
                    updateTask(get(task.id)?.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 100,
                        videoPath = vPath,
                        audioPath = aPath,
                    ) ?: task)
                },
                onError = { err ->
                    updateTask(get(task.id)?.copy(status = DownloadStatus.FAILED, error = err) ?: task)
                },
                onPaused = {
                    updateTask(get(task.id)?.copy(status = DownloadStatus.PAUSED) ?: task)
                }
            )
        }
    }

    private fun persist() {
        val ctx = appCtx ?: return
        val prefs = ctx.getSharedPreferences("ax_downloads", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray()
        _tasks.value.forEach { t ->
            arr.put(org.json.JSONObject().apply {
                put("id", t.id)
                put("animeId", t.animeId)
                put("animeTitle", t.animeTitle)
                put("episodeNum", t.episodeNum)
                put("serverName", t.serverName)
                put("quality", t.quality)
                put("sourceUrl", t.sourceUrl)
                put("isHls", t.isHls)
                put("videoPath", t.videoPath ?: "")
                put("audioPath", t.audioPath ?: "")
                put("posterPath", t.posterPath ?: "")
                put("status", t.status.name)
                put("progress", t.progress)
                put("downloadedBytes", t.downloadedBytes)
                put("totalBytes", t.totalBytes)
                put("error", t.error ?: "")
                put("createdAt", t.createdAt)
            })
        }
        prefs.edit().putString("tasks", arr.toString()).apply()
    }

    private fun loadFromPrefs(): List<DownloadTask> {
        val ctx = appCtx ?: return emptyList()
        val prefs = ctx.getSharedPreferences("ax_downloads", Context.MODE_PRIVATE)
        val raw = prefs.getString("tasks", "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val vp = o.optString("videoPath").ifBlank { null }
                val ap = o.optString("audioPath").ifBlank { null }
                val pp = o.optString("posterPath").ifBlank { null }
                val st = try { DownloadStatus.valueOf(o.optString("status", "FAILED")) } catch (_: Exception) { DownloadStatus.FAILED }
                val finalStatus = if (st == DownloadStatus.COMPLETED && (vp == null || !File(vp).exists())) DownloadStatus.FAILED else st
                DownloadTask(
                    id = o.optString("id"),
                    animeId = o.optString("animeId"),
                    animeTitle = o.optString("animeTitle"),
                    episodeNum = o.optInt("episodeNum", 1),
                    serverName = o.optString("serverName"),
                    quality = o.optString("quality"),
                    sourceUrl = o.optString("sourceUrl"),
                    isHls = o.optBoolean("isHls"),
                    videoPath = vp,
                    audioPath = ap,
                    posterPath = pp,
                    status = finalStatus,
                    progress = o.optInt("progress", 0),
                    downloadedBytes = o.optLong("downloadedBytes"),
                    totalBytes = o.optLong("totalBytes"),
                    error = o.optString("error").ifBlank { null },
                    createdAt = o.optLong("createdAt"),
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}

class PausedException : Exception("Paused")
