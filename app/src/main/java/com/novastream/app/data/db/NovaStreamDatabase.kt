package com.novastream.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WatchProgress::class, WatchlistItem::class],
    version = 5,
    exportSchema = false
)
abstract class NovaStreamDatabase : RoomDatabase() {
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun watchlistDao(): WatchlistDao

    companion object {
        @Volatile
        private var INSTANCE: NovaStreamDatabase? = null

        // Migration v1 -> v2: Add index on watch_progress(slug, season, episode) + watchlist(addedAt)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_slug_season_episode ON watch_progress(slug, season, episode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_addedAt ON watchlist(addedAt)")
            }
        }

        // Migration v2 -> v3: Safety migration für Schema-Korrekturen
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_addedAt ON watchlist(addedAt)")
            }
        }

        // Migration v3 -> v4: Add index on updatedAt for performance (ORDER BY updatedAt DESC)
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_updatedAt ON watch_progress(updatedAt)")
            }
        }

        // Migration v4 -> v5: Add index on slug for getBySlug queries
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_slug ON watch_progress(slug)")
            }
        }

        fun get(context: Context): NovaStreamDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NovaStreamDatabase::class.java,
                    "novastream.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    // Fallback: Wenn eine Migration fehlt, DB neu erstellen
                    .fallbackToDestructiveMigration()
                    // WAL Mode für bessere concurrent read/write Performance
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build().also { INSTANCE = it }
            }
    }
}
