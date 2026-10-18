package com.lohe.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lohe.app.data.db.entity.EpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {

    @Query("SELECT * FROM episodes WHERE uuid = :uuid")
    suspend fun get(uuid: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE podcastUuid = :podcastUuid ORDER BY published DESC")
    fun observeByPodcast(podcastUuid: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE podcastUuid = :podcastUuid ORDER BY published DESC")
    suspend fun byPodcastOnce(podcastUuid: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes ORDER BY published DESC LIMIT :limit")
    fun observeLatest(limit: Int = 50): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes")
    fun observeAll(): Flow<List<EpisodeEntity>>

    /** Part-finished episodes, most recently touched first. */
    @Query(
        """
        SELECT * FROM episodes
        WHERE playingStatus = 1 AND isDeleted = 0
        ORDER BY lastPlayedAt DESC, published DESC
        """
    )
    fun observeInProgress(): Flow<List<EpisodeEntity>>

    @Query(
        """
        SELECT * FROM episodes
        WHERE queuePosition IS NOT NULL
        ORDER BY queuePosition ASC
        """
    )
    fun observeQueue(): Flow<List<EpisodeEntity>>

    /**
     * Appends to the queue.
     *
     * `MAX(...) + 1` inside the statement avoids the read-modify-write race a
     * separate count-then-update would introduce.
     */
    @Query(
        """
        UPDATE episodes
        SET queuePosition = (SELECT COALESCE(MAX(queuePosition), -1) + 1 FROM episodes),
            lastPlayedAt = :now
        WHERE uuid = :uuid AND queuePosition IS NULL
        """
    )
    suspend fun enqueue(uuid: String, now: Long)

    @Query("UPDATE episodes SET queuePosition = NULL WHERE uuid = :uuid")
    suspend fun dequeue(uuid: String)

    /** Moves [uuid] to the head of the queue and renumbers the rest. */
    @Transaction
    suspend fun promoteInQueue(uuid: String) {
        val queued = queueSnapshot()
        val target = queued.firstOrNull { it.uuid == uuid } ?: return
        rewriteQueue(listOf(target) + queued.filterNot { it.uuid == uuid })
    }

    /** Drops [uuid] and everything behind it. */
    @Transaction
    suspend fun clearQueueFrom(uuid: String) {
        val queued = queueSnapshot()
        val index = queued.indexOfFirst { it.uuid == uuid }
        if (index <= 0) return
        rewriteQueue(queued.subList(0, index))
    }

    @Query("SELECT * FROM episodes WHERE queuePosition IS NOT NULL ORDER BY queuePosition ASC")
    suspend fun queueSnapshot(): List<EpisodeEntity>

    @Transaction
    suspend fun rewriteQueue(items: List<EpisodeEntity>) {
        items.forEachIndexed { index, episode -> renumber(episode.uuid, index) }
    }

    @Query("UPDATE episodes SET queuePosition = :position WHERE uuid = :uuid")
    suspend fun renumber(uuid: String, position: Int)

    // --- Offline downloads --------------------------------------------------

    @Query(
        """
        UPDATE episodes
        SET downloadState = :state, localPath = :path
        WHERE uuid = :uuid
        """
    )
    suspend fun setDownloadState(uuid: String, state: Int, path: String?)

    /** Clears download bookkeeping without deleting the file itself. */
    @Query(
        """
        UPDATE episodes
        SET downloadState = 0, localPath = NULL
        WHERE uuid = :uuid
        """
    )
    suspend fun clearDownload(uuid: String)

    @Query(
        """
        SELECT * FROM episodes
        WHERE downloadState = 2 AND localPath IS NOT NULL
        ORDER BY published DESC
        """
    )
    fun observeDownloads(): Flow<List<EpisodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    @Query("UPDATE episodes SET playedUpToSeconds = :position, playingStatus = 1 WHERE uuid = :uuid AND playingStatus < 2")
    suspend fun updateProgress(uuid: String, position: Long)

    /**
     * Saves a resume point, filling in the duration when it is still unknown.
     *
     * The `playingStatus < 2` guard matters: a finished episode must never be
     * dragged back to in-progress by a stray write from a player that is still
     * winding down.
     */
    @Query(
        """
        UPDATE episodes
        SET playedUpToSeconds = :position,
            playingStatus = 1,
            durationSeconds = CASE
                WHEN durationSeconds IS NULL OR durationSeconds = 0 THEN :duration
                ELSE durationSeconds
            END
        WHERE uuid = :uuid AND playingStatus < 2
        """
    )
    suspend fun saveProgress(uuid: String, position: Long, duration: Long)

    /** Finishing also clears the resume point, so a replay starts from zero. */
    @Query("UPDATE episodes SET playingStatus = 2, playedUpToSeconds = 0 WHERE uuid = :uuid")
    suspend fun markPlayedAndClearProgress(uuid: String)

    /**
     * Undoes [markPlayedAndClearProgress].
     *
     * Resets the position too: leaving a stale offset behind would start the
     * "unplayed" episode in the middle, which reads as a bug rather than as a
     * resume.
     */
    @Query(
        """
        UPDATE episodes
        SET playingStatus = 0, playedUpToSeconds = 0
        WHERE uuid = :uuid AND playingStatus = 2
        """
    )
    suspend fun reopenEpisode(uuid: String)

    @Query("SELECT playedUpToSeconds FROM episodes WHERE uuid = :uuid")
    suspend fun playedPosition(uuid: String): Long

    @Query("UPDATE episodes SET playingStatus = 2 WHERE uuid = :uuid")
    suspend fun markPlayed(uuid: String)

    @Query("UPDATE episodes SET starred = :starred WHERE uuid = :uuid")
    suspend fun setStarred(uuid: String, starred: Boolean)

    @Query("DELETE FROM episodes WHERE podcastUuid = :podcastUuid")
    suspend fun deleteForPodcast(podcastUuid: String)

    @Query("DELETE FROM episodes WHERE audioUrl LIKE 'content://%'")
    suspend fun deleteLocalFiles()
}