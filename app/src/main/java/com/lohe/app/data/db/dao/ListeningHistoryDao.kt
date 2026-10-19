package com.lohe.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lohe.app.data.db.entity.ListeningHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningHistoryDao {

    @Query("SELECT * FROM listening_history")
    fun observeAll(): Flow<List<ListeningHistoryEntity>>

    @Query("SELECT * FROM listening_history")
    suspend fun allOnce(): List<ListeningHistoryEntity>

    @Query("SELECT * FROM listening_history WHERE episodeUuid = :uuid")
    suspend fun get(uuid: String): ListeningHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<ListeningHistoryEntity>)

    @Query("DELETE FROM listening_history WHERE episodeUuid = :uuid")
    suspend fun delete(uuid: String)

    @Query("DELETE FROM listening_history")
    suspend fun clear()
}
