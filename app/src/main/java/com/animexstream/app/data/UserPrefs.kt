package com.animexstream.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local persistent store: user session + continue watching + user profile.
 * No Firebase Auth — login state Firestore me username/email se verify hota hai.
 */
object UserPrefs {

    private const val PREFS = "ax_user_prefs"
    private const val K_UID = "uid"
    private const val K_USERNAME = "username"
    private const val K_EMAIL = "email"
    private const val K_ICON = "icon"
    private const val K_NAME = "display_name"
    private const val K_LOGGED = "logged_in"
    private const val K_CONTINUE = "continue_watching"

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun isLoggedIn(): Boolean = prefs?.getBoolean(K_LOGGED, false) == true
    fun uid(): String? = prefs?.getString(K_UID, null)
    fun username(): String? = prefs?.getString(K_USERNAME, null)
    fun email(): String? = prefs?.getString(K_EMAIL, null)
    fun icon(): String? = prefs?.getString(K_ICON, null)
    fun displayName(): String? = prefs?.getString(K_NAME, null)

    fun saveSession(uid: String, username: String, email: String, icon: String, name: String) {
        prefs?.edit()
            ?.putString(K_UID, uid)
            ?.putString(K_USERNAME, username)
            ?.putString(K_EMAIL, email)
            ?.putString(K_ICON, icon)
            ?.putString(K_NAME, name)
            ?.putBoolean(K_LOGGED, true)
            ?.apply()
        // Mirror to the public file so the session survives an uninstall.
        SessionStore.backup()
    }

    fun updateProfile(username: String? = null, icon: String? = null, name: String? = null) {
        val e = prefs?.edit() ?: return
        username?.let { e.putString(K_USERNAME, it) }
        icon?.let { e.putString(K_ICON, it) }
        name?.let { e.putString(K_NAME, it) }
        e.apply()
    }

    fun logout() {
        prefs?.edit()?.remove(K_LOGGED)?.remove(K_UID)?.remove(K_USERNAME)?.remove(K_EMAIL)?.remove(K_ICON)?.remove(K_NAME)?.apply()
        SessionStore.clearBackup()
    }

    // ---------- Continue watching ----------
    fun saveContinue(animeId: String, title: String, poster: String, epNumber: Int) {
        val p = prefs ?: return
        val list = continueList().toMutableList()
        list.removeAll { it.optString("id") == animeId }
        list.add(0, JSONObject().apply {
            put("id", animeId)
            put("title", title)
            put("poster", poster)
            put("ep", epNumber)
            put("ts", System.currentTimeMillis())
        })
        val trimmed = list.take(20)
        p.edit().putString(K_CONTINUE, JSONArray(trimmed).toString()).apply()
    }

    fun continueList(): List<JSONObject> {
        val raw = prefs?.getString(K_CONTINUE, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        } catch (_: Exception) { emptyList() }
    }

    fun removeContinue(animeId: String) {
        val p = prefs ?: return
        val list = continueList().filter { it.optString("id") != animeId }
        p.edit().putString(K_CONTINUE, JSONArray(list).toString()).apply()
    }
}
