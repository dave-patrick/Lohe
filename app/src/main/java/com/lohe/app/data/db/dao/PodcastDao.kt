package com.lohe.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lohe.app.data.db.entity.PodcastEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {

    @Query("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts ORDER BY title COLLATE NOCASE")
    suspend fun allOnce(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE uuid = :uuid")
    suspend fun get(uuid: String): PodcastEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(podcast: PodcastEntity)

    @Update
    suspend fun update(podcast: PodcastEntity)

    @Delete
    suspend fun delete(podcast: PodcastEntity)

    @Query("DELETE FROM podcasts WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("SELECT COUNT(*) FROM podcasts")
    suspend fun count(): Int

    @Query("SELECT * FROM podcasts WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY title COLLATE NOCASE")
    fun observeSearchResults(query: String): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl LIMIT 1")
    suspend fun byFeedUrl(feedUrl: String): PodcastEntity?

    @Query("UPDATE podcasts SET autoDownload = :autoDownload WHERE uuid = :uuid")
    suspend fun setAutoDownload(uuid: String, autoDownload: Boolean)

    @Transaction
    suspend fun upsertWithEpisodes(podcast: PodcastEntity, episodes: List<com.lohe.app.data.db.entity.EpisodeEntity>) {
        upsert(podcast)
        // episode inserts handled by EpisodeDao; kept as transaction boundary
    }
}