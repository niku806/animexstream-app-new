package com.animexstream.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File

/**
 * Persists the login session so it survives app data-clear and reinstall.
 *
 * Two independent copies are written on every [backup] call:
 *  1. Internal external files dir — always writable, wiped on uninstall.
 *  2. Public Downloads/AnimeXstream/session.json via MediaStore — survives
 *     uninstall on Android 10+ and needs no runtime permission.
 *
 * On launch [restoreIfPresent] tries both and rehydrates UserPrefs, so a
 * returning user lands straight on Home.
 */
object SessionStore {

    private const val DIR = "AnimeXstream"
    private const val FILE = "session.json"
    private const val MIME = "application/json"

    @Volatile private var appCtx: Context? = null

    fun init(context: Context) {
        appCtx = context.applicationContext
        // Keep the on-disk copy fresh every launch (covers a logout on another path).
        if (UserPrefs.isLoggedIn()) backup()
    }

    // ---------- internal copy ----------
    private fun internalFile(): File? = try {
        val ctx = appCtx ?: return null
        val dir = File(ctx.getExternalFilesDir(null), DIR)
        if (!dir.exists()) dir.mkdirs()
        File(dir, FILE)
    } catch (_: Exception) { null }

    // ---------- public Downloads copy (survives uninstall) ----------
    private fun legacyFile(): File? = try {
        @Suppress("DEPRECATION")
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(dl, DIR)
        if (!dir.exists()) dir.mkdirs()
        File(dir, FILE)
    } catch (_: Exception) { null }

    private fun mediaStoreUri(ctx: Context): android.net.Uri? = try {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val args = arrayOf(FILE)
        ctx.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, args, null)?.use { c ->
            if (c.moveToFirst()) android.content.ContentUris.withAppendedId(collection, c.getLong(0)) else null
        }
    } catch (_: Exception) { null }

    private fun writeViaMediaStore(ctx: Context, text: String): Boolean {
        return try {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        var uri = mediaStoreUri(ctx)
        if (uri == null) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/$DIR")
            }
            uri = ctx.contentResolver.insert(collection, values)
        }
        if (uri == null) return false
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
        true
        } catch (_: Exception) { false }
    }

    private fun readViaMediaStore(ctx: Context): String? {
        return try {
            val uri = mediaStoreUri(ctx) ?: return null
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) { null }
    }

    // ---------- public API ----------
    fun backup() {
        try {
            val text = JSONObject().apply {
                put("uid", UserPrefs.uid() ?: "")
                put("username", UserPrefs.username() ?: "")
                put("email", UserPrefs.email() ?: "")
                put("icon", UserPrefs.icon() ?: "")
                put("name", UserPrefs.displayName() ?: "")
                put("logged", UserPrefs.isLoggedIn())
                put("ts", System.currentTimeMillis())
            }.toString()

            // 1. internal (always works)
            internalFile()?.writeText(text)

            // 2. public Downloads (survives uninstall)
            val ctx = appCtx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ctx != null) {
                if (!writeViaMediaStore(ctx, text)) legacyFile()?.writeText(text)
            } else {
                legacyFile()?.writeText(text)
            }
        } catch (_: Exception) { /* ignore */ }
    }

    fun clearBackup() {
        try {
            internalFile()?.delete()
            val ctx = appCtx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ctx != null) {
                mediaStoreUri(ctx)?.let { ctx.contentResolver.delete(it, null, null) }
            }
            legacyFile()?.delete()
        } catch (_: Exception) { }
    }

    fun restoreIfPresent(): Boolean {
        if (UserPrefs.isLoggedIn()) return true
        val ctx = appCtx
        // Try internal first (fastest), then MediaStore, then legacy file.
        val candidates = ArrayList<String?>()
        candidates.add(runCatching { internalFile()?.takeIf { it.exists() && it.length() > 0 }?.readText() }.getOrNull())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ctx != null) {
            candidates.add(runCatching { readViaMediaStore(ctx) }.getOrNull())
        }
        candidates.add(runCatching { legacyFile()?.takeIf { it.exists() && it.length() > 0 }?.readText() }.getOrNull())

        for (text in candidates) {
            if (text.isNullOrBlank()) continue
            try {
                val obj = JSONObject(text)
                val uid = obj.optString("uid")
                val uname = obj.optString("username")
                val logged = obj.optBoolean("logged", false)
                if (!logged || uid.isBlank() || uname.isBlank()) continue
                UserPrefs.saveSession(
                    uid = uid,
                    username = uname,
                    email = obj.optString("email"),
                    icon = obj.optString("icon"),
                    name = obj.optString("name").ifBlank { uname },
                )
                return true
            } catch (_: Exception) { continue }
        }
        return false
    }
}
