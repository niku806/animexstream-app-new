package com.animexstream.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

/**
 * GitHub-backed auth. Users live in community/users.json of APP-DATABASE repo.
 * Password = sha256(salt + password). Session in UserPrefs (SharedPreferences).
 */
object AuthRepository {

    val isLoggedIn: Boolean get() = UserPrefs.isLoggedIn()
    val uid: String? get() = UserPrefs.uid()
    val username: String? get() = UserPrefs.username()
    val email: String? get() = UserPrefs.email()
    val displayName: String? get() = UserPrefs.displayName() ?: UserPrefs.username()
    val iconLink: String? get() = UserPrefs.icon()

    suspend fun signUp(email: String, username: String, password: String, displayName: String, iconLink: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val mail = email.trim().lowercase()
            val uname = username.trim().lowercase()

            if (uname.length < 3) return@withContext Result.failure(Exception("Username must be at least 3 characters"))
            if (!Regex("^[a-z0-9_]+$").matches(uname)) return@withContext Result.failure(Exception("Username: only a-z, 0-9, _ allowed"))
            if (password.length < 6) return@withContext Result.failure(Exception("Password must be at least 6 characters"))
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches()) return@withContext Result.failure(Exception("Invalid email"))

            val users = GitHubDb.readUsers(force = true)
            for (i in 0 until users.length()) {
                val u = users.optJSONObject(i) ?: continue
                if (u.optString("username").equals(uname, true))
                    return@withContext Result.failure(Exception("Username already taken. Try another."))
                if (u.optString("email").equals(mail, true))
                    return@withContext Result.failure(Exception("This email is already registered. Please sign in."))
            }

            val salt = java.util.UUID.randomUUID().toString()
            val hash = sha256(salt + password)
            val uid = "u_" + sha256(mail + System.currentTimeMillis()).take(24)

            val userJson = JSONObject().apply {
                put("uid", uid)
                put("username", uname)
                put("displayName", displayName.trim().ifBlank { uname })
                put("email", mail)
                put("iconLink", iconLink)
                put("bio", "")
                put("salt", salt)
                put("hash", hash)
                put("createdAt", System.currentTimeMillis())
            }

            val ok = GitHubDb.addUser(userJson)
            if (!ok) return@withContext Result.failure(Exception("Sign up failed. Check internet."))

            UserPrefs.saveSession(uid, uname, mail, iconLink, displayName.trim().ifBlank { uname })
            CommunityRepository.syncUser(
                CommunityUser(uid, uname, iconLink, "", displayName.trim().ifBlank { uname }, mail)
            )
            Result.success(Unit)
        }

    suspend fun signIn(identifier: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        val id = identifier.trim().lowercase()
        if (id.isBlank() || password.isBlank()) return@withContext Result.failure(Exception("Please fill all fields"))

        val users = GitHubDb.readUsers(force = true)
        var found: JSONObject? = null
        for (i in 0 until users.length()) {
            val u = users.optJSONObject(i) ?: continue
            if (u.optString("username").equals(id, true) || u.optString("email").equals(id, true)) {
                found = u; break
            }
        }
        val u = found ?: return@withContext Result.failure(Exception("No account found"))

        val salt = u.optString("salt")
        val hash = u.optString("hash")
        if (sha256(salt + password) != hash) return@withContext Result.failure(Exception("Wrong password"))

        UserPrefs.saveSession(
            u.optString("uid"),
            u.optString("username"),
            u.optString("email"),
            u.optString("iconLink"),
            u.optString("displayName").ifBlank { u.optString("username") }
        )
        CommunityRepository.syncUser(
            CommunityUser(
                u.optString("uid"),
                u.optString("username"),
                u.optString("iconLink"),
                "",
                u.optString("displayName").ifBlank { u.optString("username") },
                u.optString("email")
            )
        )
        Result.success(Unit)
    }

    suspend fun updateProfile(newUsername: String?, newIcon: String?, newName: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            val oldUsername = UserPrefs.username() ?: return@withContext Result.failure(Exception("Not logged in"))
            val users = GitHubDb.readUsers(force = true)
            var target: JSONObject? = null
            for (i in 0 until users.length()) {
                val u = users.optJSONObject(i) ?: continue
                if (u.optString("username").equals(oldUsername, true)) { target = u; break }
            }
            val u = target ?: return@withContext Result.failure(Exception("Account not found"))

            newUsername?.let { u.put("username", it.trim().lowercase()) }
            newIcon?.let { u.put("iconLink", it) }
            newName?.let { u.put("displayName", it.trim()) }

            val ok = GitHubDb.updateUser(oldUsername, u)
            if (!ok) return@withContext Result.failure(Exception("Update failed"))

            UserPrefs.updateProfile(newUsername?.trim()?.lowercase(), newIcon, newName?.trim())
            Result.success(Unit)
        }

    fun signOut() = UserPrefs.logout()

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
