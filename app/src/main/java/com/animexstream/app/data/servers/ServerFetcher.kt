package com.animexstream.app.data.servers

import com.animexstream.app.data.ResolvedServer

interface ServerFetcher {
    val name: String
    suspend fun resolve(playerUrl: String): ResolvedServer?
}
