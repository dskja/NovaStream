package com.novastream.app.data.repository

import com.novastream.app.data.db.ProfileEntity
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.db.WatchlistItem
import com.novastream.app.data.provider.ActiveProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WatchRepositoryProviderScopeTest {

    private lateinit var repo: WatchRepository

    @Before
    fun setUp() {
        ActiveProvider.setById("serienstream")
        repo = WatchRepositoryProviderScopeTestSupport.createInMemoryRepository()
    }

    @After
    fun tearDown() {
        ActiveProvider.setById(ProviderManagerDefault.DEFAULT_PROVIDER_ID)
    }

    @Test
    fun watchProgressForActiveProvider_filtersOtherProviders() = runTest {
        repo.saveProgress(
            slug = "dark",
            seriesTitle = "Dark",
            coverUrl = null,
            season = 1,
            episode = 1,
            episodeTitle = "E1",
            positionMs = 1000,
            durationMs = 10_000,
            providerId = "serienstream"
        )
        repo.saveProgress(
            slug = "naruto",
            seriesTitle = "Naruto",
            coverUrl = null,
            season = 1,
            episode = 1,
            episodeTitle = "E1",
            positionMs = 1000,
            durationMs = 10_000,
            providerId = "aniworld"
        )

        val scoped = repo.watchProgressForActiveProvider().first()
        assertEquals(1, scoped.size)
        assertEquals("dark", scoped.first().slug)
    }

    @Test
    fun watchProgressForActiveProvider_keepsBlankProviderId() = runTest {
        repo.saveProgress(
            slug = "legacy",
            seriesTitle = "Legacy",
            coverUrl = null,
            season = 1,
            episode = 1,
            episodeTitle = "E1",
            positionMs = 500,
            durationMs = 5000,
            providerId = ""
        )

        val scoped = repo.watchProgressForActiveProvider().first()
        assertEquals(1, scoped.size)
    }

    @Test
    fun watchProgressForActiveProvider_keepsUnknownMigrationRows() = runTest {
        WatchRepositoryProviderScopeTestSupport.insertProgressRaw(
            WatchProgress(
                episodeKey = WatchProgress.key(ProfileEntity.DEFAULT_ID, "unknown", "old", 1, 1),
                profileId = ProfileEntity.DEFAULT_ID,
                providerId = "unknown",
                slug = "old",
                seriesTitle = "Old",
                coverUrl = null,
                season = 1,
                episode = 1,
                episodeTitle = "E1",
                positionMs = 100,
                durationMs = 1000
            )
        )

        val scoped = repo.watchProgressForActiveProvider().first()
        assertTrue(scoped.any { it.providerId == "unknown" })
    }

    @Test
    fun addToWatchlist_scopedContainsForActiveProvider() = runTest {
        repo.addToWatchlist("dark", "Dark", null, providerId = "serienstream")
        repo.addToWatchlist("naruto", "Naruto", null, providerId = "aniworld")

        assertTrue(repo.containsInWatchlist("dark"))
        assertFalse(repo.containsInWatchlist("naruto"))
    }

    @Test
    fun watchlistFlow_filtersByActiveProvider() = runTest {
        WatchRepositoryProviderScopeTestSupport.insertWatchlistRaw(
            WatchlistItem(
                itemKey = WatchlistItem.key(ProfileEntity.DEFAULT_ID, "serienstream", "dark"),
                profileId = ProfileEntity.DEFAULT_ID,
                providerId = "serienstream",
                slug = "dark",
                title = "Dark",
                coverUrl = null
            )
        )
        WatchRepositoryProviderScopeTestSupport.insertWatchlistRaw(
            WatchlistItem(
                itemKey = WatchlistItem.key(ProfileEntity.DEFAULT_ID, "aniworld", "naruto"),
                profileId = ProfileEntity.DEFAULT_ID,
                providerId = "aniworld",
                slug = "naruto",
                title = "Naruto",
                coverUrl = null
            )
        )

        val all = repo.watchlist().first()
        val scoped = all.filter { it.providerId.isBlank() || it.providerId == ActiveProvider.id }
        assertEquals(1, scoped.size)
        assertEquals("dark", scoped.first().slug)
    }
}

private object ProviderManagerDefault {
    const val DEFAULT_PROVIDER_ID = "serienstream"
}
