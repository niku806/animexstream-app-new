package com.animexstream.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File

/**
 * Backs the login session up to a PUBLIC file so it survives an app uninstall
 * (SharedPreferences are wiped with the app). On launch [restoreIfPresent]
 * re-hydrates UserPrefs from the file; every [UserPrefs.saveSession] also writes
 * it here via [backup].
 *
 * Two storage paths, tried in order:
 *  - Android 9 and below: a plain file under public Downloads/AnimeXstream/
 *    (WRITE_EXTERNAL_STORAGE granted).
 *  - Android 10+: the same public Downloads path via MediaStore, which needs no
 *    runtime permission and survives uninstall.
 *
 * All access is guarded so a failure never breaks login — the app just falls
 * back to the in-app SharedPreferences.
 */
object SessionStore {

    private const val DIR = "AnimeXstream"
    private const val FILE = "session.json"
    private const val MIME = "application/json"

    @Volatile private var appCtx: Context? = null

    fun init(context: Context) { appCtx = context.applicationContext }

    // ---------- legacy direct file (API <= 28) ----------
    private fun legacyFile(): File? = try {
        @Suppress("DEPRECATION")
        val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(dl, DIR)
        if (!dir.exists()) dir.mkdirs()
        File(dir, FILE)
    } catch (_: Exception) { null }

    // ---------- MediaStore (API >= 29) ----------
    private fun mediaStoreUri(ctx: Context): android.net.Uri? = try {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(FILE, "Download/$DIR%")
        ctx.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, args, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                android.content.ContentUris.withAppendedId(collection, id)
            } else null
        }
    } catch (_: Exception) { null }

    private fun writeViaMediaStore(ctx: Context, text: String): Boolean = try {
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

    private fun readViaMediaStore(ctx: Context): String? = try {
        val uri = mediaStoreUri(ctx) ?: return null
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: Exception) { null }

    // ---------- public API ----------
    /** Write the current session to the public file. Silent on failure. */
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

            val ctx = appCtx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ctx != null) {
                if (!writeViaMediaStore(ctx, text)) legacyFile()?.writeText(text)
            } else {
                legacyFile()?.writeText(text)
            }
        } catch (_: Exception) { /* ignore */ }
    }

    /** Clear the public backup (used on logout). */
    fun clearBackup() {
        try {
            val ctx = appCtx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ctx != null) {
                mediaStoreUri(ctx)?.let { ctx.contentResolver.delete(it, null, null) }
            }
            legacyFile()?.delete()
        } catch (_: Exception) { }
    }

    /**
     * If the in-app session is empty but a valid public backup exists, verify it
     * (non-empty uid + username + logged) and restore into UserPrefs so the user
     * lands straight on Home after reinstalling. Returns true when restored.
     */
    fun restoreIfPresent(): Boolean {
        if (UserPrefs.isLoggedIn()) return false
        return try {
            val ctx = appCtx
            val text: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ctx != null) {
                readViaMediaStore(ctx) ?: legacyFile()?.takeIf { it.exists() }?.readText()
            } else {
                legacyFile()?.takeIf { it.exists() }?.readText()
            } ?: return false
            if (text.isBlank()) return false

            val obj = JSONObject(text)
            val uid = obj.optString("uid")
            val uname = obj.optString("username")
            val logged = obj.optBoolean("logged", false)
            if (!logged || uid.isBlank() || uname.isBlank()) return false
            UserPrefs.saveSession(
                uid = uid,
                username = uname,
                email = obj.optString("email"),
                icon = obj.optString("icon"),
                name = obj.optString("name").ifBlank { uname },
            )
            true
        } catch (_: Exception) { false }
    }
}
