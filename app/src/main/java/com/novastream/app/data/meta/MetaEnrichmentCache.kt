package com.novastream.app.data.meta

import com.novastream.app.data.model.Series
import java.util.concurrent.ConcurrentHashMap

/** In-memory TTL cache for [MetaEnrichment] to reduce duplicate free-API calls. */
object MetaEnrichmentCache {

    private data class Entry(val enrichment: MetaEnrichment, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private const val TTL_MS = 30 * 60 * 1000L
    private const val MAX_ENTRIES = 200

    fun cacheKey(series: Series): String = listOfNotNull(
        series.imdbId,
        series.tvmazeId,
        series.anilistId?.toString(),
        series.canonicalKey,
        series.title.lowercase().trim(),
        series.year
    ).joinToString("|")

    fun cacheKey(title: String, imdbId: String?, tvmazeId: String?, anilistId: Int?): String =
        listOfNotNull(imdbId, tvmazeId, anilistId?.toString(), title.lowercase().trim()).joinToString("|")

    fun get(key: String): MetaEnrichment? {
        val entry = cache[key] ?: return null
        if (entry.expiresAtMs < System.currentTimeMillis()) {
            cache.remove(key)
            return null
        }
        return entry.enrichment
    }

    fun put(key: String, enrichment: MetaEnrichment) {
        if (cache.size >= MAX_ENTRIES) {
            val oldest = cache.entries.minByOrNull { it.value.expiresAtMs }?.key
            if (oldest != null) cache.remove(oldest)
        }
        cache[key] = Entry(enrichment, System.currentTimeMillis() + TTL_MS)
    }

    fun clear() = cache.clear()
}
