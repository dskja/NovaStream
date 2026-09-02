package com.novastream.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.novastream.app.data.db.NovaStreamDatabase
import com.novastream.app.data.db.WatchProgress
import com.novastream.app.data.db.WatchlistItem
import com.novastream.app.profile.ProfileManager
import kotlinx.coroutines.runBlocking

internal object WatchRepositoryProviderScopeTestSupport {

    private var testDb: NovaStreamDatabase? = null

    fun createInMemoryRepository(): WatchRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NovaStreamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        testDb = db
        val profileManager = ProfileManager(context, db)
        runBlocking { profileManager.ensureDefaultProfile() }
        return WatchRepository(db, profileManager)
    }

    fun insertProgress(repo: WatchRepository, progress: WatchProgress) {
        runBlocking {
            repo.saveProgress(
                slug = progress.slug,
                seriesTitle = progress.seriesTitle,
                coverUrl = progress.coverUrl,
                season = progress.season,
                episode = progress.episode,
                episodeTitle = progress.episodeTitle,
                positionMs = progress.positionMs,
                durationMs = progress.durationMs,
                isMovie = progress.isMovie,
                providerId = progress.providerId
            )
        }
    }

    fun insertProgressRaw(progress: WatchProgress) {
        val db = requireNotNull(testDb)
        runBlocking { db.watchProgressDao().upsert(progress) }
    }

    fun insertWatchlist(repo: WatchRepository, item: WatchlistItem) {
        runBlocking {
            repo.addToWatchlist(
                slug = item.slug,
                title = item.title,
                coverUrl = item.coverUrl,
                isMovie = item.isMovie,
                providerId = item.providerId
            )
        }
    }

    fun insertWatchlistRaw(item: WatchlistItem) {
        val db = requireNotNull(testDb)
        runBlocking { db.watchlistDao().add(item) }
    }
}
