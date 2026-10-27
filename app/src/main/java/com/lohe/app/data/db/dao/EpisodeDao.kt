package com.lohe.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.EpisodeSummary
import kotlinx.coroutines.flow.Flow

data class PodcastEpisodeStats(val podcastUuid: String, val total: Int, val unplayed: Int, val latest: Long)

@Dao
interface EpisodeDao {
    @Query("""
        SELECT podcastUuid, SUM(CASE WHEN isDeleted = 0 THEN 1 ELSE 0 END) AS total,
            SUM(CASE WHEN isDeleted = 0 AND playingStatus != 2 AND isArchived = 0 THEN 1 ELSE 0 END) AS unplayed,
            MAX(published) AS latest FROM episodes GROUP BY podcastUuid
    """)
    fun observePodcastStats(): Flow<List<PodcastEpisodeStats>>


    @Query("SELECT * FROM episodes WHERE uuid IN (:uuids)")
    suspend fun getMany(uuids: List<String>): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE uuid = :uuid")
    suspend fun get(uuid: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE podcastUuid = :podcastUuid ORDER BY published DESC")
    fun observeByPodcast(podcastUuid: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE podcastUuid = :podcastUuid ORDER BY published DESC")
    suspend fun byPodcastOnce(podcastUuid: String): List<EpisodeEntity>

    /**
     * Every episode, newest first.
     *
     * A snapshot rather than a flow: the dev surface asks for the library in
     * one go and wants it now, not a stream it has to collect from.
     */
    @Query("SELECT * FROM episodes ORDER BY published DESC")
    suspend fun allOnce(): List<EpisodeEntity>

    @Query("SELECT * FROM episodes ORDER BY published DESC LIMIT :limit")
    fun observeLatest(limit: Int = 50): Flow<List<EpisodeEntity>>

    @Query(
        """
        SELECT episodes.* FROM episodes
        INNER JOIN podcasts ON podcasts.uuid = episodes.podcastUuid
        WHERE podcasts.isSubscribed = 1
          AND episodes.isDeleted = 0
          AND episodes.isArchived = 0
          AND episodes.title LIKE '%' || replace(replace(replace(:query, '!', '!!'), '%', '!%'), '_', '!_') || '%' ESCAPE '!' COLLATE NOCASE
        ORDER BY episodes.published DESC
        LIMIT :limit
        """,
    )
    suspend fun searchByTitle(query: String, limit: Int): List<EpisodeEntity>

    /**
     * Every episode, newest first. A lightweight projection: [description] and
     * [chapters] are omitted because they are large text blobs only needed on
     * the episode detail screen. Full [EpisodeEntity] is fetched by UUID when
     * those fields are required.
     */
    /**
     * Every episode, newest first. A lightweight projection: [description] and
     * [chapters] are omitted because they are large text blobs only needed on
     * the episode detail screen. Full [EpisodeEntity] is fetched by UUID when
     * those fields are required.
     */
    @Query(
        """
        SELECT uuid, podcastUuid, title, audioUrl, published, durationSeconds,
               sizeBytes, fileType, author, artworkUrl, playingStatus,
               playedUpToSeconds, listenedSeconds, starred, isDeleted,
               isArchived, archivedAt, queuePosition, lastPlayedAt, playedAt, localPath, downloadState
        FROM episodes
        ORDER BY published DESC
        """
    )
    fun observeAllSummaries(): Flow<List<EpisodeSummary>>

    /** Every episode as full entities — used when description/chapters are needed. */
    @Query("SELECT * FROM episodes")
    fun observeAllEntities(): Flow<List<EpisodeEntity>>

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

    /** Moves the selected queued episodes to the front without changing their queue order. */
    @Transaction
    suspend fun promoteBatchInQueue(uuids: List<String>) {
        val queued = queueSnapshot()
        val selected = uuids.toSet()
        val reordered = queued.filter { it.uuid in selected } + queued.filterNot { it.uuid in selected }
        reordered.forEachIndexed { index, episode -> renumber(episode.uuid, index) }
    }

    /** Drops [uuid] and everything behind it. */
    @Transaction
    suspend fun clearQueueFrom(uuid: String) {
        val queued = queueSnapshot()
        val index = queued.indexOfFirst { it.uuid == uuid }
        if (index < 0) return
        rewriteQueue(queued.subList(0, index))
        queued.drop(index).forEach { dequeue(it.uuid) }
    }

    @Query("SELECT * FROM episodes WHERE queuePosition IS NOT NULL ORDER BY queuePosition ASC")
    suspend fun queueSnapshot(): List<EpisodeEntity>

    @Transaction
    suspend fun rewriteQueue(items: List<EpisodeEntity>) {
        items.forEachIndexed { index, episode -> renumber(episode.uuid, index) }
    }

    /**
     * Rewrites the whole queue order in one go.
     *
     * Used when a row is dragged to a new position: nudging the rows either
     * side of it one at a time would write the queue over and over, and each
     * write would reshuffle the list underneath the finger being dragged.
     */
    @Transaction
    suspend fun reorderQueue(uuids: List<String>) {
        uuids.forEachIndexed { index, uuid -> renumber(uuid, index) }
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
        SELECT uuid, podcastUuid, title, audioUrl, published, durationSeconds,
               sizeBytes, fileType, author, artworkUrl, playingStatus,
               playedUpToSeconds, listenedSeconds, starred, isDeleted,
               isArchived, archivedAt, queuePosition, lastPlayedAt, playedAt, localPath, downloadState
        FROM episodes
        WHERE downloadState = 2 AND localPath IS NOT NULL
        ORDER BY published DESC
        """
    )
    fun observeDownloads(): Flow<List<EpisodeSummary>>

    /**
     * Inserts or replaces episodes.
     *
     * REPLACE is safe here in a way it is not for podcasts: `episodes` is a
     * leaf table — nothing has a foreign key pointing at it — so the
     * delete-then-insert cannot cascade anything away. Replacing the podcast
     * row, by contrast, would take the whole library with it.
     */
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
     *
     * `listenedSeconds` accumulates only the forward progress made since the
     * last save, so seeking ahead does not inflate the listening total and
     * re-listening to a section counts again.
     */
    @Query(
        """
        UPDATE episodes
        SET playedUpToSeconds = :position,
            playingStatus = 1,
            lastPlayedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000,
            listenedSeconds = listenedSeconds + MAX(0, :position - playedUpToSeconds),
            durationSeconds = CASE
                WHEN durationSeconds IS NULL OR durationSeconds = 0 THEN :duration
                ELSE durationSeconds
            END
        WHERE uuid = :uuid AND playingStatus < 2
        """
    )
    suspend fun saveProgress(uuid: String, position: Long, duration: Long)

    /**
     * Finishing also clears the resume point, so a replay starts from zero.
     *
     * `listenedSeconds` is deliberately left alone: it records what was
     * actually heard, which is not the same as where to resume from.
     */
    @Query("UPDATE episodes SET lastPlayedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000, playedAt = CASE WHEN playingStatus != 2 OR playedAt IS NULL THEN CAST(strftime('%s', 'now') AS INTEGER) * 1000 ELSE playedAt END, playingStatus = 2, playedUpToSeconds = 0 WHERE uuid = :uuid")
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
        SET playingStatus = 0, playedUpToSeconds = 0, listenedSeconds = 0,
            isArchived = 0, archivedAt = NULL, playedAt = NULL
        WHERE uuid = :uuid AND playingStatus = 2
        """
    )
    suspend fun reopenEpisode(uuid: String)

    /**
     * Sets a resume point without touching the listening total.
     *
     * [saveProgress] counts the forward progress as time listened, which is
     * right for a player and wrong for an import: a resume point copied in
     * from another app is a position, not something that was heard here.
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
        WHERE uuid = :uuid
        """
    )
    suspend fun applyProgress(uuid: String, position: Long, duration: Long)

    /**
     * Puts an episode back to unplayed without erasing the listening total.
     *
     * [reopenEpisode] clears `listenedSeconds` too, which is right when the
     * listener un-marks an episode by hand and wrong for an import, where
     * the point is that the two apps agree on what has been heard.
     */
    @Query(
        """
        UPDATE episodes
        SET playingStatus = 0, playedUpToSeconds = 0, playedAt = NULL,
            isArchived = 0, archivedAt = NULL
        WHERE uuid = :uuid
        """
    )
    suspend fun applyUnplayed(uuid: String)

    @Query("SELECT playedUpToSeconds FROM episodes WHERE uuid = :uuid")
    suspend fun playedPosition(uuid: String): Long

    @Query("UPDATE episodes SET playedAt = CASE WHEN playingStatus != 2 OR playedAt IS NULL THEN CAST(strftime('%s', 'now') AS INTEGER) * 1000 ELSE playedAt END, playingStatus = 2, playedUpToSeconds = 0 WHERE uuid = :uuid")
    suspend fun markPlayed(uuid: String)

    @Query("UPDATE episodes SET starred = :starred WHERE uuid = :uuid")
    suspend fun setStarred(uuid: String, starred: Boolean)

    /**
     * Files an episode away, or brings it back.
     *
     * An archived episode is left out of the episode lists and stops
     * counting towards the listening figures.
     */
    @Query("UPDATE episodes SET isArchived = :archived, archivedAt = CASE WHEN :archived = 1 THEN archivedAt ELSE NULL END WHERE uuid = :uuid")
    suspend fun setArchived(uuid: String, archived: Boolean)

    @Query("UPDATE episodes SET isArchived = 1, archivedAt = CAST(strftime('%s', 'now') AS INTEGER) * 1000, queuePosition = NULL WHERE uuid = :uuid AND playingStatus = 2 AND isArchived = 0 AND playedAt IS NOT NULL AND playedAt <= :cutoff")
    suspend fun archivePlayedIfDue(uuid: String, cutoff: Long): Int

    @Query("UPDATE episodes SET isArchived = 1, archivedAt = :now, playingStatus = 2, playedAt = COALESCE(playedAt, :now), playedUpToSeconds = 0, queuePosition = NULL WHERE uuid = :uuid")
    suspend fun archive(uuid: String, now: Long)

    @Query("""
        SELECT uuid FROM episodes WHERE uuid > :afterUuid AND (
            (:archiveEnabled AND playingStatus = 2 AND isArchived = 0 AND playedAt <= :playedCutoff)
            OR (isArchived = 1 AND archivedAt <= :deleteCutoff AND (localPath IS NOT NULL OR downloadState != 0))
        ) ORDER BY uuid LIMIT :limit
    """)
    suspend fun dueArchiveIds(archiveEnabled: Boolean, playedCutoff: Long, deleteCutoff: Long, afterUuid: String, limit: Int): List<String>

    /**
     * Files several episodes away, or brings them back, in one statement.
     *
     * A repair over tens of thousands of rows cannot be done one write at a
     * time: the caller splits the list into chunks, because SQLite has a
     * limit on how many parameters one statement may carry.
     */
    @Query("UPDATE episodes SET isDeleted = :deleted WHERE uuid IN (:uuids)")
    suspend fun setDeletedFor(uuids: List<String>, deleted: Boolean)

    @Query("DELETE FROM episodes WHERE podcastUuid = :podcastUuid")
    suspend fun deleteForPodcast(podcastUuid: String)

    @Query("DELETE FROM episodes WHERE audioUrl LIKE 'content://%'")
    suspend fun deleteLocalFiles()
}
