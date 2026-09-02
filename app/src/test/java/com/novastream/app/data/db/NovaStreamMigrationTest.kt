package com.novastream.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NovaStreamMigrationTest {

    @Test
    fun freshDatabase_persistsDownloadIsMovie() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NovaStreamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val entity = DownloadEntity(
            downloadId = "default|kinoger|film|S1|E1",
            providerId = "kinoger",
            slug = "/filme/abc",
            title = "Film",
            streamUrl = "https://example.com/v.m3u8",
            isMovie = true
        )
        db.downloadDao().upsert(entity)
        val loaded = db.downloadDao().getById(entity.downloadId)
        assertTrue(loaded!!.isMovie)
        db.close()
    }

    @Test
    fun migrate13To14_addsIsMovieColumn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_13_14_test"
        context.deleteDatabase(dbName)
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(13) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE downloads (
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
                    db.execSQL(
                        """
                        INSERT INTO downloads (
                            downloadId, profileId, providerId, slug, title, episodeTitle,
                            season, episode, streamUrl, status, bytesDownloaded, contentLength,
                            createdAt, updatedAt
                        ) VALUES (
                            'default|kinoger|/filme/xyz|S1|E1', 'default', 'kinoger', '/filme/xyz',
                            'Film', 'Film', 1, 1, 'https://example.com/v.m3u8', 'COMPLETED', 0, 0,
                            1000, 1000
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        NovaStreamDatabase.ALL_MIGRATIONS
            .filter { it.startVersion == 13 && it.endVersion == 14 }
            .forEach { migration -> migration.migrate(db) }
        db.query("SELECT isMovie FROM downloads").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        db.close()
    }
}
