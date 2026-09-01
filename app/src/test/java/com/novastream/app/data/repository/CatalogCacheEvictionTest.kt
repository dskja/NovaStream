package com.novastream.app.data.repository

import com.google.gson.Gson
import com.novastream.app.data.db.CatalogCacheDao
import com.novastream.app.data.db.CatalogCacheEntry
import com.novastream.app.data.model.Series
import com.novastream.app.data.provider.ActiveProvider
import com.novastream.app.data.provider.SerienStreamProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CatalogCacheEvictionTest {

    private lateinit var dao: FakeCatalogCacheDao
    private lateinit var repo: NovaStreamRepository

    @Before
    fun setUp() {
        ActiveProvider.setById(SerienStreamProvider().id)
        dao = FakeCatalogCacheDao()
        repo = NovaStreamRepository.forCache(dao)
    }

    @After
    fun tearDown() {
        ActiveProvider.setById("serienstream")
    }

    @Test
    fun catalogCacheEntry_isExpired_whenPastExpiry() {
        val entry = CatalogCacheEntry(
            cacheKey = "k",
            providerId = "p",
            cacheType = CatalogCacheEntry.TYPE_HOME,
            payload = "{}",
            cachedAt = 0,
            expiresAt = System.currentTimeMillis() - 1
        )
        assertTrue(entry.isExpired)
    }

    @Test
    fun catalogCacheEntry_isValid_whenBeforeExpiry() {
        val entry = CatalogCacheEntry(
            cacheKey = "k",
            providerId = "p",
            cacheType = CatalogCacheEntry.TYPE_HOME,
            payload = "{}",
            cachedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 60_000
        )
        assertTrue(!entry.isExpired)
    }

    @Test
    fun purgeExpiredCache_deletesExpiredRows() = runTest {
        dao.upsert(expiredEntry("expired-home", CatalogCacheEntry.TYPE_HOME))
        dao.upsert(validEntry("valid-popular", CatalogCacheEntry.TYPE_LIST, "popular"))

        repo.purgeExpiredCache()

        assertTrue(dao.deletedExpiredCalled)
        assertNull(dao.store["expired-home"])
        assertNotNull(dao.store["valid-popular"])
    }

    @Test
    fun fakeDao_delete_removesSingleKey() = runTest {
        val key = CatalogCacheEntry.key("serienstream", CatalogCacheEntry.TYPE_LIST, "newest")
        dao.upsert(validEntry(key, CatalogCacheEntry.TYPE_LIST, "newest"))
        dao.delete(key)
        assertNull(dao.store[key])
        assertTrue(dao.deletedKeys.contains(key))
    }

    @Test
    fun fakeDao_deleteForProvider_clearsProviderRows() = runTest {
        dao.upsert(validEntry("serien-a", CatalogCacheEntry.TYPE_LIST, "a").copy(providerId = "serienstream"))
        dao.upsert(validEntry("ani-b", CatalogCacheEntry.TYPE_LIST, "b").copy(providerId = "aniworld"))
        dao.deleteForProvider("serienstream")
        assertNull(dao.store["serien-a"])
        assertNotNull(dao.store["ani-b"])
    }

    @Test
    fun evictLruIfNeeded_removesOldestWhenOverBudget() = runTest {
        val bigPayload = "x".repeat(30 * 1024 * 1024)
        dao.upsert(
            CatalogCacheEntry(
                cacheKey = "old",
                providerId = "serienstream",
                cacheType = CatalogCacheEntry.TYPE_HOME,
                payload = bigPayload,
                cachedAt = 1,
                expiresAt = System.currentTimeMillis() + 60_000
            )
        )
        dao.upsert(
            CatalogCacheEntry(
                cacheKey = "new",
                providerId = "serienstream",
                cacheType = CatalogCacheEntry.TYPE_HOME,
                payload = bigPayload,
                cachedAt = 2,
                expiresAt = System.currentTimeMillis() + 60_000
            )
        )
        repo.evictLruIfNeeded()
        assertNull(dao.store["old"])
        assertNotNull(dao.store["new"])
    }

    private fun gsonSeriesList(items: List<Series>): String = Gson().toJson(items)

    private fun expiredEntry(key: String, type: String) = CatalogCacheEntry(
        cacheKey = key,
        providerId = "serienstream",
        cacheType = type,
        payload = gsonSeriesList(emptyList()),
        cachedAt = 0,
        expiresAt = 1
    )

    private fun validEntry(key: String, type: String, suffix: String) = CatalogCacheEntry(
        cacheKey = key,
        providerId = "serienstream",
        cacheType = type,
        payload = gsonSeriesList(listOf(Series(id = suffix, title = suffix))),
        cachedAt = System.currentTimeMillis(),
        expiresAt = System.currentTimeMillis() + 60_000
    )
}

private class FakeCatalogCacheDao : CatalogCacheDao {
    val store = linkedMapOf<String, CatalogCacheEntry>()
    val deletedKeys = mutableListOf<String>()
    var deletedExpiredCalled = false

    override suspend fun get(key: String): CatalogCacheEntry? = store[key]

    override suspend fun upsert(entry: CatalogCacheEntry) {
        store[entry.cacheKey] = entry
    }

    override suspend fun delete(key: String) {
        deletedKeys += key
        store.remove(key)
    }

    override suspend fun deleteExpired(now: Long): Int {
        deletedExpiredCalled = true
        val expired = store.filterValues { it.expiresAt <= now }.keys.toList()
        expired.forEach { store.remove(it) }
        return expired.size
    }

    override suspend fun deleteForProvider(providerId: String) {
        store.entries.removeIf { it.value.providerId == providerId }
    }

    override suspend fun deleteAll() {
        store.clear()
    }

    override suspend fun totalPayloadBytes(): Long =
        store.values.sumOf { it.payload.length.toLong() }

    override suspend fun listByOldest(): List<CatalogCacheEntry> =
        store.values.sortedBy { it.cachedAt }
}
