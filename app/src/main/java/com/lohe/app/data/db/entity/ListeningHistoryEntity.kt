package com.lohe.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Listening history for episodes that no longer exist in the library.
 *
 * Unsubscribing from a podcast deletes the podcast row, and the episodes
 * foreign key cascades from there — which would take the accumulated listening
 * time with it. This table has no foreign key, so the time actually spent
 * listening survives the show being removed.
 *
 * A row exists only while its episode is gone. When the episode comes back
 * (the show is re-subscribed, or a feed re-imports it) the history is folded
 * back into the episode and the row is dropped, so nothing is counted twice.
 */
@Entity(tableName = "listening_history")
data class ListeningHistoryEntity(
    @PrimaryKey val episodeUuid: String,
    val listenedSeconds: Long,
    val durationSeconds: Int?,
    val title: String?,
)
