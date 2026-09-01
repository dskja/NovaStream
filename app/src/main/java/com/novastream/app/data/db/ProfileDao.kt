package com.novastream.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE profileId = :id LIMIT 1")
    suspend fun getById(id: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Query("UPDATE profiles SET isActive = 0")
    suspend fun deactivateAll()

    @Transaction
    suspend fun setActive(profileId: String) {
        deactivateAll()
        upsert(
            getById(profileId)?.copy(isActive = true)
                ?: ProfileEntity(profileId = profileId, displayName = profileId, isActive = true)
        )
    }

    @Query("DELETE FROM profiles WHERE profileId = :id AND profileId != :defaultId")
    suspend fun delete(id: String, defaultId: String = ProfileEntity.DEFAULT_ID)

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int
}
