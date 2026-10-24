package com.lohe.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A subscribed podcast. Fields mirror what we need from an RSS <channel>.
 *
 * Time fields are stored as epoch millis so we don't need a Room
 * type converter.
 */
@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val uuid: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val artworkUrl: String? = null,
    val feedUrl: String,
    val lastEpisodePublished: Long? = null,
    val subscribedAt: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,

    /**
     * Whether a refresh should immediately download newly-seen episodes.
     *
     * Per show rather than global: most listeners want one or two shows on a
     * metered connection and stream the rest.
     */
    val autoDownload: Boolean = false,
    @ColumnInfo(defaultValue = "1") val isSubscribed: Boolean = true,
)
