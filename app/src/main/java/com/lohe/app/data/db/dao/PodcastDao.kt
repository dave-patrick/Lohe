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

    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 ORDER BY title COLLATE NOCASE")
    suspend fun allOnce(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE uuid = :uuid")
    suspend fun get(uuid: String): PodcastEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(podcast: PodcastEntity): Long

    /**
     * Refreshes the columns that come from the feed.
     *
     * Deliberately narrow. Auto-download, auto-queue placement, subscription
     * state and unread count belong to the listener, not to the feed — a
     * refresh must never reset them.
     */
    @Query(
        """
        UPDATE podcasts SET
            title = :title,
            author = :author,
            description = :description,
            artworkUrl = :artworkUrl,
            feedUrl = :feedUrl,
            lastEpisodePublished = :lastEpisodePublished
        WHERE uuid = :uuid
        """
    )
    suspend fun updateFeedFields(
        uuid: String,
        title: String,
        author: String?,
        description: String?,
        artworkUrl: String?,
        feedUrl: String,
        lastEpisodePublished: Long?,
    )

    /**
     * Adds the show, or refreshes its feed metadata if it is already here.
     *
     * The obvious spelling of this — `@Insert(onConflict = REPLACE)` — is a
     * trap. Room implements REPLACE as delete-then-insert, and `episodes`
     * carries a foreign key onto `podcasts` with ON DELETE CASCADE. Deleting
     * the podcast row therefore deleted every episode of the show with it,
     * and the refresh then wrote the feed's copy back in with default state:
     * played status, resume positions, downloads, stars and queue order all
     * silently gone, on every single refresh.
     *
     * INSERT OR IGNORE cannot delete anything, so the row survives and only
     * the feed-derived columns change.
     */
    @Transaction
    suspend fun upsert(podcast: PodcastEntity) {
        // -1 means the insert was ignored because the show is already here.
        if (insertIfAbsent(podcast) == -1L) {
            updateFeedFields(
                uuid = podcast.uuid,
                title = podcast.title,
                author = podcast.author,
                description = podcast.description,
                artworkUrl = podcast.artworkUrl,
                feedUrl = podcast.feedUrl,
                lastEpisodePublished = podcast.lastEpisodePublished,
            )
            val stored = get(podcast.uuid)
            if (stored != null && stored.isSubscribed != podcast.isSubscribed) {
                update(stored.copy(isSubscribed = podcast.isSubscribed))
            }
        }
    }

    @Update
    suspend fun update(podcast: PodcastEntity)

    @Delete
    suspend fun delete(podcast: PodcastEntity)

    @Query("DELETE FROM podcasts WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("SELECT COUNT(*) FROM podcasts WHERE isSubscribed = 1")
    suspend fun count(): Int

    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 AND (title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') ORDER BY title COLLATE NOCASE")
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
