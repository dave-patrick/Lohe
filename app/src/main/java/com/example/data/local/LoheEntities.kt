package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AutoQueueMode(val label: String) {
    OFF("Off"),
    BEGINNING("Beginning"),
    END("End")
}

enum class QueueIndicatorStyle(val label: String, val isPro: Boolean) {
    DOT("Dot", isPro = false),
    RING("Ring", isPro = false),
    ICON("Queue icon", isPro = false),
    PILL("Pill", isPro = true),
    DIAMOND("Diamond", isPro = true),
    GLOW("Glow", isPro = true);

    companion object {
        fun fromName(name: String): QueueIndicatorStyle =
            entries.find { it.name.equals(name, ignoreCase = true) || it.label.equals(name, ignoreCase = true) }
                ?: DOT
    }
}

enum class QueueIndicatorSize(val label: String, val sizeDp: Float) {
    SMALL("Small", 8f),
    MEDIUM("Medium", 12f),
    LARGE("Large", 16f);

    companion object {
        fun fromName(name: String): QueueIndicatorSize =
            entries.find { it.name.equals(name, ignoreCase = true) || it.label.equals(name, ignoreCase = true) }
                ?: SMALL
    }
}

enum class SmartPlaylistType(val id: String, val title: String, val subtitle: String) {
    EVERYTHING("smart_everything", "All Signal", "Every episode in your library"),
    RECENT_RELEASES("smart_recent", "Recent Releases", "Fresh episodes published recently"),
    IN_PROGRESS("smart_in_progress", "In Progress", "Continue where you left off"),
    UNPLAYED("smart_unplayed", "Unplayed", "All unplayed episodes waiting"),
    DOWNLOADED("smart_downloaded", "Downloaded", "Cached on device for offline listening"),
    STARRED("smart_starred", "Bookmarked", "Starred episodes & saved signal"),
    SHORT_EPISODES("smart_short", "Quick Listens", "Compact episodes under 25 minutes")
}

@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val description: String,
    val feedUrl: String,
    val artworkUrl: String,
    val category: String,
    val autoQueueMode: String = AutoQueueMode.OFF.name,
    val useCustomAudio: Boolean = false,
    val customSpeed: Float = 1.0f,
    val customSkipSilence: Boolean = false,
    val customVolumeBoostDb: Int = 0,
    val notifyNewEpisodes: Boolean = true,
    val isSubscribed: Boolean = true,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val podcastId: String,
    val podcastTitle: String,
    val title: String,
    val descriptionHtml: String,
    val audioUrl: String,
    val artworkUrl: String,
    val publishedAt: Long,
    val durationMs: Long,
    val resumePositionMs: Long = 0L,
    val isPlayed: Boolean = false,
    val isArchived: Boolean = false,
    val isDownloaded: Boolean = false,
    val localFilePath: String = "",
    val isStarred: Boolean = false,
    val inUpNext: Boolean = false,
    val upNextOrder: Int = 0,
    val chaptersUrl: String = "",
    val chaptersJson: String = "",
    val transcriptUrl: String = "",
    val transcriptMimeType: String = "",
    val transcriptText: String = "",
    val guid: String = "",
    val playedAt: Long = 0L
)

@Entity(tableName = "episode_bookmarks")
data class EpisodeBookmarkEntity(
    @PrimaryKey val id: String,
    val episodeId: String,
    val positionMs: Long,
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val folderName: String = "General",
    val episodeIdsCsv: String = "",
    val assignedPodcastIdsCsv: String = "",
    val smartRuleType: String = "",
    val isPinned: Boolean = false,
    val autoDownload: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun episodeIdList(): List<String> =
        episodeIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun assignedPodcastIdList(): List<String> =
        assignedPodcastIdsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
