package com.novastream.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WatchProgress::class,
        WatchlistItem::class,
        CatalogCacheEntry::class,
        ContentEntity::class,
        DownloadEntity::class,
        ProfileEntity::class
    ],
    version = 15,
    exportSchema = true
)
abstract class NovaStreamDatabase : RoomDatabase() {
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun catalogCacheDao(): CatalogCacheDao
    abstract fun contentDao(): ContentDao
    abstract fun downloadDao(): DownloadDao
    abstract fun profileDao(): ProfileDao

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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS catalog_cache (
                        cacheKey TEXT NOT NULL PRIMARY KEY,
                        providerId TEXT NOT NULL,
                        cacheType TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        cachedAt INTEGER NOT NULL,
                        expiresAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_catalog_cache_providerId ON catalog_cache(providerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_catalog_cache_expiresAt ON catalog_cache(expiresAt)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS content_mapping (
                        tmdbId INTEGER NOT NULL,
                        slug TEXT NOT NULL,
                        providerId TEXT NOT NULL,
                        contentType TEXT NOT NULL,
                        PRIMARY KEY(providerId, slug)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_content_mapping_tmdbId ON content_mapping(tmdbId)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS content_mapping_new (
                        slug TEXT NOT NULL,
                        providerId TEXT NOT NULL,
                        contentType TEXT NOT NULL,
                        canonicalKey TEXT NOT NULL,
                        imdbId TEXT,
                        tvmazeId TEXT,
                        anilistId INTEGER,
                        wikidataId TEXT,
                        PRIMARY KEY(providerId, slug)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO content_mapping_new (slug, providerId, contentType, canonicalKey)
                    SELECT slug, providerId, contentType, 'tmdb:' || tmdbId
                    FROM content_mapping
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE content_mapping")
                db.execSQL("ALTER TABLE content_mapping_new RENAME TO content_mapping")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_content_mapping_canonicalKey ON content_mapping(canonicalKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_content_mapping_imdbId ON content_mapping(imdbId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_content_mapping_tvmazeId ON content_mapping(tvmazeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_content_mapping_anilistId ON content_mapping(anilistId)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS downloads (
                        downloadId TEXT NOT NULL PRIMARY KEY,
                        profileId TEXT NOT NULL DEFAULT 'default',
                        providerId TEXT NOT NULL,
                        slug TEXT NOT NULL,
                        title TEXT NOT NULL,
                        episodeTitle TEXT NOT NULL DEFAULT '',
                        season INTEGER NOT NULL DEFAULT 1,
                        episode INTEGER NOT NULL DEFAULT 1,
                        coverUrl TEXT,
                        streamUrl TEXT NOT NULL,
                        mimeType TEXT NOT NULL DEFAULT 'application/x-mpegURL',
                        hosterName TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'QUEUED',
                        bytesDownloaded INTEGER NOT NULL DEFAULT 0,
                        contentLength INTEGER NOT NULL DEFAULT 0,
                        localPath TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        errorMessage TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_status ON downloads(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_profileId ON downloads(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_createdAt ON downloads(createdAt)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS profiles (
                        profileId TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        avatarEmoji TEXT NOT NULL DEFAULT '👤',
                        pinHash TEXT,
                        isActive INTEGER NOT NULL DEFAULT 0,
                        isKids INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_profiles_isActive ON profiles(isActive)")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO profiles (profileId, displayName, avatarEmoji, isActive, isKids, createdAt)
                    VALUES ('default', 'Default', '👤', 1, 0, ${System.currentTimeMillis()})
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS watchlist_new (
                        itemKey TEXT NOT NULL PRIMARY KEY,
                        profileId TEXT NOT NULL DEFAULT 'default',
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
                    INSERT INTO watchlist_new (itemKey, profileId, providerId, slug, title, coverUrl, isMovie, addedAt)
                    SELECT 'default|' || providerId || '|' || slug, 'default', providerId, slug, title, coverUrl, isMovie, addedAt
                    FROM watchlist
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE watchlist")
                db.execSQL("ALTER TABLE watchlist_new RENAME TO watchlist")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_addedAt ON watchlist(addedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_providerId ON watchlist(providerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_slug ON watchlist(slug)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_profileId ON watchlist(profileId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS watch_progress_new (
                        episodeKey TEXT NOT NULL PRIMARY KEY,
                        profileId TEXT NOT NULL DEFAULT 'default',
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
                        episodeKey, profileId, providerId, slug, seriesTitle, coverUrl,
                        season, episode, episodeTitle, positionMs, durationMs, isMovie, updatedAt
                    )
                    SELECT
                        'default|' || providerId || '|' || slug || '-' || season || '-' || episode,
                        'default', providerId, slug, seriesTitle, coverUrl,
                        season, episode, episodeTitle, positionMs, durationMs, isMovie, updatedAt
                    FROM watch_progress
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE watch_progress")
                db.execSQL("ALTER TABLE watch_progress_new RENAME TO watch_progress")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_slug_season_episode ON watch_progress(slug, season, episode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_updatedAt ON watch_progress(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_slug ON watch_progress(slug)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_providerId ON watch_progress(providerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_profileId ON watch_progress(profileId)")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Repair keys from a buggy v11 migration that prefixed old itemKey/episodeKey values.
                db.execSQL(
                    """
                    UPDATE watchlist
                    SET itemKey = profileId || '|' || providerId || '|' || slug
                    WHERE itemKey LIKE '%|%|%'
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE watch_progress
                    SET episodeKey = profileId || '|' || providerId || '|' || slug || '-' || season || '-' || episode
                    WHERE episodeKey LIKE '%|%|%'
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Prefix legacy download IDs (provider|slug|S#|E#) with profileId.
                db.execSQL(
                    """
                    UPDATE downloads
                    SET downloadId = profileId || '|' || downloadId
                    WHERE downloadId NOT LIKE profileId || '|%'
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN isMovie INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE downloads
                    SET isMovie = 1
                    WHERE slug LIKE 'movie-%'
                       OR slug LIKE 'movie/%'
                       OR slug LIKE '%/filme/%'
                       OR slug LIKE '%/film/%'
                       OR slug LIKE '%/movie/%'
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watchlist ADD COLUMN isAdult INTEGER")
                db.execSQL("ALTER TABLE watchlist ADD COLUMN genres TEXT")
            }
        }

        internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15
        )

        fun get(context: Context): NovaStreamDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NovaStreamDatabase::class.java,
                    "novastream.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build().also { INSTANCE = it }
            }
    }
}
