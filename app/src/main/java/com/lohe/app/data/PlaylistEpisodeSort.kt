package com.lohe.app.data

import java.util.Locale

enum class PlaylistEpisodeSortField(val displayLabel: String) {
    MANUAL("Manual"),
    FILE_SIZE("File size"),
    LENGTH("Length"),
    PUBLISH_DATE("Publish date"),
    PODCAST_TITLE("Podcast title"),
}

enum class PlaylistEpisodeSortDirection(val displayLabel: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending"),
}

data class PlaylistEpisodeSort(
    val field: PlaylistEpisodeSortField = PlaylistEpisodeSortField.MANUAL,
    val direction: PlaylistEpisodeSortDirection = PlaylistEpisodeSortDirection.ASCENDING,
) {
    fun encode(): String = "${field.name}:${direction.name}"

    companion object {
        fun decode(value: String?): PlaylistEpisodeSort {
            val parts = value?.split(':') ?: return PlaylistEpisodeSort()
            if (parts.size != 2) return PlaylistEpisodeSort()
            val field = PlaylistEpisodeSortField.entries.firstOrNull { it.name == parts[0] }
                ?: return PlaylistEpisodeSort()
            val direction = PlaylistEpisodeSortDirection.entries.firstOrNull { it.name == parts[1] }
                ?: return PlaylistEpisodeSort()
            return PlaylistEpisodeSort(field, direction)
        }
    }
}

/** A display order only: persisted playlist membership and Up Next are untouched. */
fun sortPlaylistEpisodes(entries: List<QueueEntry>, sort: PlaylistEpisodeSort): List<QueueEntry> {
    if (sort.field == PlaylistEpisodeSortField.MANUAL) return entries.toList()
    fun normalized(value: String) = value.trim().lowercase(Locale.ROOT)
    fun <T : Comparable<T>> compareKnown(a: T?, b: T?): Int = when {
        a == null && b == null -> 0
        a == null -> 1
        b == null -> -1
        sort.direction == PlaylistEpisodeSortDirection.DESCENDING -> b.compareTo(a)
        else -> a.compareTo(b)
    }
    return entries.sortedWith { a, b ->
        val primary = when (sort.field) {
            PlaylistEpisodeSortField.FILE_SIZE -> compareKnown(
                a.episode.sizeBytes?.takeIf { it > 0 }, b.episode.sizeBytes?.takeIf { it > 0 },
            )
            PlaylistEpisodeSortField.LENGTH -> compareKnown(
                a.episode.durationSeconds?.takeIf { it > 0 }, b.episode.durationSeconds?.takeIf { it > 0 },
            )
            PlaylistEpisodeSortField.PUBLISH_DATE -> compareKnown(
                a.episode.published.takeIf { it > 0 }, b.episode.published.takeIf { it > 0 },
            )
            PlaylistEpisodeSortField.PODCAST_TITLE -> compareKnown(
                normalized(a.showTitle).takeIf { it.isNotEmpty() },
                normalized(b.showTitle).takeIf { it.isNotEmpty() },
            )
            PlaylistEpisodeSortField.MANUAL -> 0
        }
        if (primary != 0) primary else {
            val title = normalized(a.episode.title).compareTo(normalized(b.episode.title))
            if (title != 0) title else a.episode.uuid.compareTo(b.episode.uuid)
        }
    }
}
