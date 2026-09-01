package com.novastream.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeAll(profileId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE downloadId = :id LIMIT 1")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED')")
    suspend fun getActive(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, updatedAt = :updatedAt WHERE downloadId = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE downloads SET
            status = :status,
            bytesDownloaded = :bytesDownloaded,
            contentLength = :contentLength,
            errorMessage = :errorMessage,
            updatedAt = :updatedAt
        WHERE downloadId = :id
    """)
    suspend fun updateProgress(
        id: String,
        status: DownloadStatus,
        bytesDownloaded: Long,
        contentLength: Long,
        errorMessage: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM downloads WHERE downloadId = :id")
    suspend fun delete(id: String)

    @Query("SELECT COALESCE(SUM(bytesDownloaded), 0) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun totalDownloadedBytes(): Long

    @Query("SELECT COUNT(*) FROM downloads")
    suspend fun count(): Int
}
