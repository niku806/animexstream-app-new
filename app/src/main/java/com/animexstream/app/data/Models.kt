package com.animexstream.app.data

// ---------- Anime index ----------
data class Anime(
    val id: String,
    val title: String,
    val poster: String,
    val genres: List<String> = emptyList(),
    val year: Int = 0,
    val totalEpisodes: Int = 0,
    val synopsis: String = "",
    val rating: Double = 0.0,
) {
    val slug: String get() = id
}

// ---------- Episode ----------
data class StreamServer(
    val name: String = "Server",
    val url: String = "",
    val quality: String = "",
    val type: String = "sub",
)

data class DownloadLink(
    val name: String = "Download",
    val url: String = "",
    val quality: String = "",
    val size: String = "",
)

data class Episode(
    val number: Int,
    val season: Int = 1,
    val title: String = "",
    val image: String = "",
    val description: String = "",
    val servers: List<StreamServer> = emptyList(),
    val downloads: List<DownloadLink> = emptyList(),
    val isLocked: Boolean = false,
)

// ---------- Anime detail ----------
data class AnimeDetail(
    val slug: String,
    val title: String,
    val image: String,
    val banner: String,
    val synopsis: String,
    val genres: List<String>,
    val year: Int,
    val rating: Double,
    val status: String,
    val type: String,
    val episodes: List<Episode>,
)

// ---------- Community ----------
data class CommunityUser(
    val id: String,
    val username: String,
    val iconLink: String = "",
    val bio: String = "",
    val displayName: String = "",
    val email: String = "",
)

data class CommunityPost(
    val id: String,
    val userId: String,
    val username: String,
    val iconLink: String = "",
    val text: String = "",
    val imageUrl: String? = null,
    val createdAt: Long = 0L,
    val likes: List<String> = emptyList(),
    val replyCount: Int = 0,
    val mentions: List<String> = emptyList(),
    val seenBy: List<String> = emptyList(),
)

// ---------- Categories ----------
data class Category(val label: String, val icon: String)
