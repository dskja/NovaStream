package com.novastream.app.data.playback

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Short-lived in-memory store for playback URLs that must not be encoded in nav routes
 * (e.g. long IPTV stream URLs with tokens).
 */
@Singleton
class PlaybackRequestStore @Inject constructor() {

    data class Request(
        val streamUrl: String,
        val isLive: Boolean = false,
        val title: String = "",
        val createdAtMs: Long = System.currentTimeMillis()
    ) {
        fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
            nowMs - createdAtMs > TTL_MS
    }

    private val requests = ConcurrentHashMap<String, Request>()

    fun put(streamUrl: String, isLive: Boolean = false, title: String = ""): String {
        cleanupExpired()
        val id = UUID.randomUUID().toString()
        requests[id] = Request(streamUrl.trim(), isLive, title)
        return id
    }

    fun get(id: String): Request? {
        val request = requests[id] ?: return null
        if (request.isExpired()) {
            requests.remove(id)
            return null
        }
        return request
    }

    fun remove(id: String) {
        requests.remove(id)
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        requests.entries.removeIf { it.value.isExpired(now) }
    }

    companion object {
        private const val TTL_MS = 30L * 60 * 1000
    }
}
