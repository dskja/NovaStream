package com.novastream.app.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRequestStoreTest {

    @Test
    fun putAndGetRoundTrip() {
        val store = PlaybackRequestStore()
        val id = store.put("https://example.com/live.m3u8", isLive = true, title = "News")
        val request = store.get(id)
        assertEquals("https://example.com/live.m3u8", request?.streamUrl)
        assertTrue(request?.isLive == true)
        assertEquals("News", request?.title)
    }

    @Test
    fun removeClearsRequest() {
        val store = PlaybackRequestStore()
        val id = store.put("https://example.com/v.mp4")
        store.remove(id)
        assertNull(store.get(id))
    }

    @Test
    fun expiredRequestReturnsNull() {
        val store = PlaybackRequestStore()
        val id = store.put("https://example.com/v.mp4")
        val field = PlaybackRequestStore::class.java.getDeclaredField("requests")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(store) as java.util.concurrent.ConcurrentHashMap<String, PlaybackRequestStore.Request>
        val old = map[id]!!
        map[id] = old.copy(createdAtMs = System.currentTimeMillis() - 31L * 60 * 1000)
        assertNull(store.get(id))
    }
}
