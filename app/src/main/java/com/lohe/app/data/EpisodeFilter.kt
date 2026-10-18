package com.lohe.app.data

import com.lohe.app.data.db.entity.EpisodeEntity

/**
 * Filtering and sorting for an episode list.
 *
 * Pure and synchronous so the rules can be tested directly. Filtering runs
 * over the already-loaded list rather than in SQL: shows routinely carry a few
 * hundred episodes at most, and doing it here keeps the filter reactive while a
 * download is writing progress back to the database.
 */
object EpisodeFilter {

    enum class Filter {
        ALL,
        UNPLAYED,
        IN_PROGRESS,
        DOWNLOADED,
        STARRED;

        val label: String
            get() = when (this) {
                ALL -> "All"
                UNPLAYED -> "Unplayed"
                IN_PROGRESS -> "Listening"
                DOWNLOADED -> "Offline"
                STARRED -> "Starred"
            }
    }

    enum class Order {
        NEWEST_FIRST,
        OLDEST_FIRST,
        SHORTEST_FIRST;

        val label: String
            get() = when (this) {
                NEWEST_FIRST -> "Newest"
                OLDEST_FIRST -> "Oldest"
                SHORTEST_FIRST -> "Shortest"
            }
    }

    fun apply(
        episodes: List<EpisodeEntity>,
        filter: Filter,
        order: Order,
        query: String = "",
    ): List<EpisodeEntity> {
        val needle = query.trim().lowercase()

        val matched = episodes.asSequence()
            .filter { episode -> episode.matches(filter) }
            .filter { episode ->
                needle.isEmpty() || episode.title.lowercase().contains(needle)
            }

        val ordered = when (order) {
            Order.NEWEST_FIRST -> matched.sortedByDescending { it.published }
            Order.OLDEST_FIRST -> matched.sortedBy { it.published }
            // Episodes with an unknown duration sort last: they cannot be
            // meaningfully compared and would otherwise scatter to the front.
            Order.SHORTEST_FIRST -> matched.sortedWith(
                compareBy(
                    { it.durationSeconds == null },
                    { it.durationSeconds ?: Int.MAX_VALUE },
                ),
            )
        }

        return ordered.toList()
    }

    private fun EpisodeEntity.matches(filter: Filter): Boolean = when (filter) {
        Filter.ALL -> true
        // "Unplayed" means untouched, not merely unfinished: an episode you
        // abandoned is still unplayed as far as the library is concerned.
        Filter.UNPLAYED -> !isPlayed
        Filter.IN_PROGRESS -> isInProgress
        Filter.DOWNLOADED -> isDownloaded
        Filter.STARRED -> starred
    }

    /** Total time left across [episodes]; unknown durations contribute nothing. */
    fun remainingSeconds(episodes: List<EpisodeEntity>): Long =
        episodes.sumOf { episode ->
            val duration = episode.durationSeconds ?: return@sumOf 0
            if (episode.isPlayed) 0L else (duration - episode.playedUpToSeconds).coerceAtLeast(0L)
        }
}
