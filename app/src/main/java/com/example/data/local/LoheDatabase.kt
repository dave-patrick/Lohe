package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface LoheDao {
    @Query("SELECT * FROM podcasts WHERE isSubscribed = 1 ORDER BY title ASC")
    fun getSubscribedPodcasts(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts ORDER BY title ASC")
    fun getAllPodcasts(): Flow<List<PodcastEntity>>

    @Query("SELECT COUNT(*) FROM podcasts")
    suspend fun getPodcastCount(): Int

    @Query("SELECT * FROM podcasts WHERE id = :podcastId LIMIT 1")
    suspend fun getPodcastById(podcastId: String): PodcastEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcasts(podcasts: List<PodcastEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcast(podcast: PodcastEntity)

    @Update
    suspend fun updatePodcast(podcast: PodcastEntity)

    @Query("DELETE FROM podcasts WHERE id = :podcastId")
    suspend fun deletePodcastById(podcastId: String)

    @Query("DELETE FROM episodes WHERE podcastId = :podcastId")
    suspend fun deleteEpisodesByPodcastId(podcastId: String)

    @Query("SELECT * FROM episodes WHERE isArchived = 0 ORDER BY publishedAt DESC")
    fun getActiveEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes ORDER BY publishedAt DESC")
    fun getAllEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE inUpNext = 1 AND isPlayed = 0 AND isArchived = 0 ORDER BY upNextOrder ASC, publishedAt DESC")
    fun getUpNextEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE resumePositionMs > 0 AND isPlayed = 0 AND isArchived = 0 ORDER BY publishedAt DESC")
    fun getInProgressEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE (isDownloaded = 1 OR localFilePath != '') AND isArchived = 0 ORDER BY publishedAt DESC")
    fun getDownloadedEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId AND isArchived = 0 ORDER BY publishedAt DESC")
    fun getEpisodesByPodcastId(podcastId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE id = :episodeId LIMIT 1")
    suspend fun getEpisodeById(episodeId: String): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: EpisodeEntity)

    @Update
    suspend fun updateEpisode(episode: EpisodeEntity)

    @Query("UPDATE episodes SET resumePositionMs = :positionMs, durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END WHERE id = :episodeId AND isPlayed = 0")
    suspend fun updatePlaybackPosition(episodeId: String, positionMs: Long, durationMs: Long)

    @Query("UPDATE episodes SET isPlayed = :played, playedAt = CASE WHEN :played = 1 THEN :playedAtMs ELSE 0 END, resumePositionMs = CASE WHEN :played = 1 THEN 0 ELSE resumePositionMs END, inUpNext = CASE WHEN :played = 1 THEN 0 ELSE inUpNext END WHERE id = :episodeId")
    suspend fun markEpisodePlayed(episodeId: String, played: Boolean, playedAtMs: Long = System.currentTimeMillis())

    @Query("UPDATE episodes SET isPlayed = 0, playedAt = 0, resumePositionMs = 0 WHERE id = :episodeId")
    suspend fun resetEpisodeProgress(episodeId: String)

    @Query("UPDATE episodes SET inUpNext = :inUpNext, upNextOrder = :upNextOrder WHERE id = :episodeId")
    suspend fun updateEpisodeUpNext(episodeId: String, inUpNext: Boolean, upNextOrder: Int)

    @Query("UPDATE episodes SET isStarred = :isStarred WHERE id = :episodeId")
    suspend fun updateEpisodeStarred(episodeId: String, isStarred: Boolean)

    @Query("UPDATE episodes SET isArchived = 1, inUpNext = 0, upNextOrder = 0 WHERE id = :episodeId")
    suspend fun updateEpisodeArchived(episodeId: String)

    @Query("UPDATE episodes SET isDownloaded = :downloaded, localFilePath = :localFilePath WHERE id = :episodeId")
    suspend fun updateEpisodeDownloaded(episodeId: String, downloaded: Boolean, localFilePath: String)

    @Query("UPDATE episodes SET chaptersJson = :chaptersJson WHERE id = :episodeId")
    suspend fun updateEpisodeChapters(episodeId: String, chaptersJson: String)

    @Query("UPDATE episodes SET transcriptText = :transcriptText WHERE id = :episodeId")
    suspend fun updateEpisodeTranscript(episodeId: String, transcriptText: String)

    @Query("UPDATE episodes SET durationMs = :durationMs, publishedAt = :publishedAt, artworkUrl = :artworkUrl, descriptionHtml = :descriptionHtml WHERE id = :episodeId")
    suspend fun healEpisodeMetadata(
        episodeId: String,
        durationMs: Long,
        publishedAt: Long,
        artworkUrl: String,
        descriptionHtml: String
    )

    @Query("UPDATE episodes SET inUpNext = 0, upNextOrder = 0")
    suspend fun clearUpNextQueue()

    @Query("UPDATE episodes SET isArchived = 1, inUpNext = 0 WHERE isPlayed = 1 AND isStarred = 0")
    suspend fun archiveAllPlayedNonStarred()

    @Query("UPDATE episodes SET isDownloaded = 0, localFilePath = ''")
    suspend fun clearAllDownloads()

    @Query("SELECT * FROM custom_playlists ORDER BY isPinned DESC, createdAt ASC")
    fun getCustomPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT COUNT(*) FROM custom_playlists")
    suspend fun getPlaylistCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Query("SELECT * FROM custom_playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: String): PlaylistEntity?

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM custom_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("SELECT * FROM episode_bookmarks WHERE episodeId = :episodeId ORDER BY positionMs ASC")
    fun getBookmarksForEpisode(episodeId: String): Flow<List<EpisodeBookmarkEntity>>

    @Query("SELECT * FROM episode_bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<EpisodeBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: EpisodeBookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(bookmarks: List<EpisodeBookmarkEntity>)

    @Query("DELETE FROM episode_bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmarkById(bookmarkId: String)
}

@Database(
    entities = [
        PodcastEntity::class,
        EpisodeEntity::class,
        PlaylistEntity::class,
        EpisodeBookmarkEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class LoheDatabase : RoomDatabase() {
    abstract fun loheDao(): LoheDao

    companion object {
        @Volatile
        private var INSTANCE: LoheDatabase? = null

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE episodes ADD COLUMN guid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE episodes ADD COLUMN playedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): LoheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LoheDatabase::class.java,
                    "lohe_preview.db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
