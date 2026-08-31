package com.novastream.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WatchProgress::class, WatchlistItem::class],
    version = 6,
    exportSchema = false
)
abstract class NovaStreamDatabase : RoomDatabase() {
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun watchlistDao(): WatchlistDao

    companion object {
        @Volatile
        private var INSTANCE: NovaStreamDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_slug_season_episode ON watch_progress(slug, season, episode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_addedAt ON watchlist(addedAt)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_addedAt ON watchlist(addedAt)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_updatedAt ON watch_progress(updatedAt)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_slug ON watch_progress(slug)")
            }
        }

        /** Provider-Isolation: providerId + neue Keys, damit Kataloge nicht vermischt werden. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS watchlist_new (
                        itemKey TEXT NOT NULL PRIMARY KEY,
                        providerId TEXT NOT NULL,
                        slug TEXT NOT NULL,
                        title TEXT NOT NULL,
                        coverUrl TEXT,
                        isMovie INTEGER NOT NULL DEFAULT 0,
                        addedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO watchlist_new (itemKey, providerId, slug, title, coverUrl, isMovie, addedAt)
                    SELECT 'unknown|' || slug, 'unknown', slug, title, coverUrl, 0, addedAt FROM watchlist
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE watchlist")
                db.execSQL("ALTER TABLE watchlist_new RENAME TO watchlist")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_addedAt ON watchlist(addedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_providerId ON watchlist(providerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_slug ON watchlist(slug)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS watch_progress_new (
                        episodeKey TEXT NOT NULL PRIMARY KEY,
                        providerId TEXT NOT NULL,
                        slug TEXT NOT NULL,
                        seriesTitle TEXT NOT NULL,
                        coverUrl TEXT,
                        season INTEGER NOT NULL,
                        episode INTEGER NOT NULL,
                        episodeTitle TEXT NOT NULL,
                        positionMs INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        isMovie INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO watch_progress_new (
                        episodeKey, providerId, slug, seriesTitle, coverUrl,
                        season, episode, episodeTitle, positionMs, durationMs, isMovie, updatedAt
                    )
                    SELECT 'unknown|' || episodeKey, 'unknown', slug, seriesTitle, coverUrl,
                           season, episode, episodeTitle, positionMs, durationMs, 0, updatedAt
                    FROM watch_progress
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE watch_progress")
                db.execSQL("ALTER TABLE watch_progress_new RENAME TO watch_progress")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_slug_season_episode ON watch_progress(slug, season, episode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_updatedAt ON watch_progress(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_slug ON watch_progress(slug)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_providerId ON watch_progress(providerId)")
            }
        }

        fun get(context: Context): NovaStreamDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NovaStreamDatabase::class.java,
                    "novastream.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6
                    )
                    .apply {
                        if (com.novastream.app.BuildConfig.DEBUG) {
                            fallbackToDestructiveMigration()
                        }
                    }
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build().also { INSTANCE = it }
            }
    }
}
