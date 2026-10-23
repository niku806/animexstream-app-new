package com.animexstream.app.data

import android.content.Context
import android.net.Uri
import com.animexstream.app.FirebaseConfig
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object CommunityRepository {

    private val db: FirebaseDatabase by lazy { FirebaseConfig.database() }
    private val postsRef: DatabaseReference by lazy { db.getReference("community/posts") }
    private val usersRef: DatabaseReference by lazy { db.getReference("community/users") }

    // ---------- realtime listener ----------
    fun listenPosts(onUpdate: (List<CommunityPost>) -> Unit, onError: (String) -> Unit = {}): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<CommunityPost>()
                for (child in snapshot.children) parsePost(child)?.let { list.add(it) }
                onUpdate(list.sortedBy { it.createdAt })
            }
            override fun onCancelled(error: DatabaseError) { onError(error.message) }
        }
        postsRef.addValueEventListener(listener)
        return listener
    }

    fun removePostsListener(listener: ValueEventListener) {
        try { postsRef.removeEventListener(listener) } catch (_: Exception) {}
    }

    private fun parsePost(snapshot: DataSnapshot): CommunityPost? {
        val id = snapshot.key ?: return null
        return CommunityPost(
            id = id,
            userId = snapshot.child("userId").getValue(String::class.java) ?: "",
            username = snapshot.child("username").getValue(String::class.java) ?: "user",
            iconLink = snapshot.child("iconLink").getValue(String::class.java) ?: "",
            text = snapshot.child("text").getValue(String::class.java) ?: "",
            imageUrl = snapshot.child("imageUrl").getValue(String::class.java),
            createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L,
            likes = snapshot.child("likes").children.mapNotNull { it.getValue(String::class.java) },
            replyCount = (snapshot.child("replyCount").getValue(Long::class.java) ?: 0L).toInt(),
            mentions = snapshot.child("mentions").children.mapNotNull { it.getValue(String::class.java) },
            seenBy = snapshot.child("seenBy").children.mapNotNull { it.getValue(String::class.java) }
        )
    }

    // ---------- create post ----------
    suspend fun createPost(user: CommunityUser, text: String, imageUri: Uri?, context: Context): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val imageUrl = if (imageUri != null) GitHubDb.uploadImage(context, imageUri)
                    ?: return@withContext Result.failure(Exception("Image upload failed"))
                else null

                val mentions = resolveMentions(text)
                val key = postsRef.push().key ?: return@withContext Result.failure(Exception("Push failed"))

                val data = hashMapOf<String, Any>(
                    "userId" to user.id,
                    "username" to user.username,
                    "iconLink" to user.iconLink,
                    "text" to text,
                    "createdAt" to ServerValue.TIMESTAMP,
                    "likes" to emptyList<String>(),
                    "mentions" to mentions,
                    "seenBy" to emptyList<String>(),
                    "replyCount" to 0
                )
                if (imageUrl != null) data["imageUrl"] = imageUrl

                postsRef.child(key).setValue(data).await()
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }

    // ---------- likes / seen ----------
    suspend fun toggleLike(post: CommunityPost, uid: String) = withContext(Dispatchers.IO) {
        postsRef.child(post.id).child("likes").runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val likes = mutableData.children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
                if (uid in likes) likes.remove(uid) else likes.add(uid)
                mutableData.value = likes
                return Transaction.success(mutableData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })
        Unit
    }

    suspend fun markSeen(postId: String, uid: String) = withContext(Dispatchers.IO) {
        postsRef.child(postId).child("seenBy").runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val seen = mutableData.children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
                if (uid !in seen) seen.add(uid)
                mutableData.value = seen
                return Transaction.success(mutableData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })
        Unit
    }

    // ---------- users ----------
    suspend fun syncUser(user: CommunityUser) = withContext(Dispatchers.IO) {
        try {
            usersRef.child(user.id).setValue(mapOf(
                "uid" to user.id,
                "username" to user.username,
                "iconLink" to user.iconLink,
                "displayName" to user.displayName,
                "email" to user.email
            )).await()
        } catch (_: Exception) {}
    }

    suspend fun resolveMentions(text: String): List<String> = withContext(Dispatchers.IO) {
        val names = Regex("@([a-zA-Z0-9_]{3,20})").findAll(text).map { it.groupValues[1].lowercase() }.toSet()
        if (names.isEmpty()) return@withContext emptyList()
        try {
            val snap = usersRef.get().await()
            val out = ArrayList<String>()
            for (child in snap.children) {
                val uname = child.child("username").getValue(String::class.java)?.lowercase() ?: continue
                if (uname in names) out.add(child.key ?: continue)
            }
            out
        } catch (_: Exception) { emptyList() }
    }

    suspend fun searchUsers(query: String): List<CommunityUser> = withContext(Dispatchers.IO) {
        if (query.length < 1) return@withContext emptyList()
        try {
            val snap = usersRef.get().await()
            val out = ArrayList<CommunityUser>()
            val q = query.lowercase()
            for (child in snap.children) {
                val uname = child.child("username").getValue(String::class.java) ?: continue
                if (uname.lowercase().contains(q)) {
                    out.add(CommunityUser(
                        id = child.key ?: "",
                        username = uname,
                        iconLink = child.child("iconLink").getValue(String::class.java) ?: "",
                        displayName = child.child("displayName").getValue(String::class.java) ?: "",
                        email = child.child("email").getValue(String::class.java) ?: ""
                    ))
                }
                if (out.size >= 10) break
            }
            out
        } catch (_: Exception) { emptyList() }
    }

    suspend fun loadUserPosts(username: String): List<CommunityPost> = withContext(Dispatchers.IO) {
        try {
            val snap = postsRef.get().await()
            snap.children.mapNotNull { parsePost(it) }.filter { it.username.equals(username, true) }.sortedByDescending { it.createdAt }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun loadPosts(): List<CommunityPost> = withContext(Dispatchers.IO) {
        try {
            val snap = postsRef.get().await()
            snap.children.mapNotNull { parsePost(it) }.sortedBy { it.createdAt }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun uploadForSignup(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        GitHubDb.uploadImage(context, uri)
    }
}
