package com.novastream.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WatchProgress::class, WatchlistItem::class],
    version = 3,
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
                // Stelle sicher dass der watchlist addedAt Index existiert
                // (war in MIGRATION_1_2 ursprünglich vergessen)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_addedAt ON watchlist(addedAt)")
            }
        }

        fun get(context: Context): NovaStreamDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NovaStreamDatabase::class.java,
                    "novastream.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // Fallback: Wenn eine Migration fehlt, DB neu erstellen
                    // (Watchlist/Watch Progress sind nicht kritisch - User kann sie neu aufbauen)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
