package com.lohe.app.data

import com.lohe.app.data.db.entity.DownloadState
import com.lohe.app.data.db.entity.EpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeFilterTest {

    private fun episode(
        uuid: String,
        title: String = "Episode $uuid",
        published: Long = 0L,
        playingStatus: Int = EpisodeEntity.UNPLAYED,
        playedUpToSeconds: Long = 0L,
        durationSeconds: Int? = null,
        starred: Boolean = false,
        queuePosition: Int? = null,
        downloadState: Int = DownloadState.NONE,
        localPath: String? = null,
        isArchived: Boolean = false,
    ) = EpisodeEntity(
        uuid = uuid,
        podcastUuid = "p1",
        title = title,
        audioUrl = "https://example.com/$uuid.mp3",
        published = published,
        durationSeconds = durationSeconds,
        playingStatus = playingStatus,
        playedUpToSeconds = playedUpToSeconds,
        starred = starred,
        queuePosition = queuePosition,
        downloadState = downloadState,
        localPath = localPath,
        isArchived = isArchived,
    )

    private val list = listOf(
        episode("a", published = 300, playingStatus = EpisodeEntity.PLAYED),
        episode("b", published = 200, playingStatus = EpisodeEntity.IN_PROGRESS),
        episode("c", published = 100, starred = true),
        episode(
            "d",
            published = 400,
            downloadState = DownloadState.DONE,
            localPath = "/data/episode.mp3",
        ),
    )

    private fun ids(order: EpisodeFilter.Order = EpisodeFilter.Order.NEWEST_FIRST) =
        EpisodeFilter.apply(list, EpisodeFilter.Filter.ALL, order).map { it.uuid }

    // --- Ordering ------------------------------------------------------------

    @Test
    fun `newest first`() {
        assertEquals(listOf("d", "a", "b", "c"), ids())
    }

    @Test
    fun `oldest first is the exact reverse`() {
        assertEquals(listOf("c", "b", "a", "d"), ids(EpisodeFilter.Order.OLDEST_FIRST))
    }

    @Test
    fun `shortest first puts unknown durations last`() {
        val mixed = listOf(
            episode("long", durationSeconds = 3_000),
            episode("unknown", durationSeconds = null),
            episode("zero", durationSeconds = 0),
            episode("short", durationSeconds = 600),
        )

        val ordered = EpisodeFilter
            .apply(mixed, EpisodeFilter.Filter.ALL, EpisodeFilter.Order.SHORTEST_FIRST)
            .map { it.uuid }

        assertEquals("zero is a real duration and sorts first", listOf("zero", "short", "long"), ordered.take(3))
        assertEquals("unknown duration sorts last", "unknown", ordered.last())
    }

    @Test
    fun `ordering is stable across equal timestamps`() {
        val tied = listOf(
            episode("x", published = 100),
            episode("y", published = 100),
            episode("z", published = 100),
        )
        assertEquals(3, EpisodeFilter.apply(tied, EpisodeFilter.Filter.ALL, EpisodeFilter.Order.NEWEST_FIRST).size)
    }

    // --- Filtering -----------------------------------------------------------

    @Test
    fun `all returns everything`() {
        assertEquals(4, EpisodeFilter.apply(list, EpisodeFilter.Filter.ALL, EpisodeFilter.Order.NEWEST_FIRST).size)
    }

    @Test
    fun `unplayed includes in-progress episodes`() {
        // An abandoned episode is still unplayed as far as the library goes.
        val result = EpisodeFilter
            .apply(list, EpisodeFilter.Filter.UNPLAYED, EpisodeFilter.Order.NEWEST_FIRST)
            .map { it.uuid }
        assertEquals(listOf("d", "b", "c"), result)
    }

    @Test
    fun `in progress excludes untouched and finished`() {
        val result = EpisodeFilter
            .apply(list, EpisodeFilter.Filter.IN_PROGRESS, EpisodeFilter.Order.NEWEST_FIRST)
            .map { it.uuid }
        assertEquals(listOf("b"), result)
    }

    @Test
    fun `downloaded requires both the state and a path`() {
        val halfConfigured = listOf(
            episode("noPath", downloadState = DownloadState.DONE, localPath = null),
            episode("withPath", downloadState = DownloadState.DONE, localPath = "/data/x.mp3"),
            episode("running", downloadState = DownloadState.RUNNING, localPath = null),
        )

        val result = EpisodeFilter
            .apply(halfConfigured, EpisodeFilter.Filter.DOWNLOADED, EpisodeFilter.Order.NEWEST_FIRST)
            .map { it.uuid }

        assertEquals(listOf("withPath"), result)
    }

    @Test
    fun `starred`() {
        val result = EpisodeFilter
            .apply(list, EpisodeFilter.Filter.STARRED, EpisodeFilter.Order.NEWEST_FIRST)
            .map { it.uuid }
        assertEquals(listOf("c"), result)
    }

    // --- Archived ------------------------------------------------------------

    @Test
    fun `archived episodes appear in all and played only`() {
        val withArchived = list + episode("e", isArchived = true, playingStatus = EpisodeEntity.PLAYED)
        for (filter in listOf(
            EpisodeFilter.Filter.UNPLAYED,
            EpisodeFilter.Filter.IN_PROGRESS,
            EpisodeFilter.Filter.DOWNLOADED,
            EpisodeFilter.Filter.STARRED,
        )) {
            val result = EpisodeFilter
                .apply(withArchived, filter, EpisodeFilter.Order.NEWEST_FIRST)
                .map { it.uuid }
            assertFalse("archived episode leaked into $filter", "e" in result)
        }
        assertTrue("e" in EpisodeFilter.apply(withArchived, EpisodeFilter.Filter.ALL, EpisodeFilter.Order.NEWEST_FIRST).map { it.uuid })
        assertTrue("e" in EpisodeFilter.apply(withArchived, EpisodeFilter.Filter.PLAYED, EpisodeFilter.Order.NEWEST_FIRST).map { it.uuid })
    }

    @Test
    fun `the archived list holds the archived episodes`() {
        val withArchived = list + episode("e", isArchived = true)
        val result = EpisodeFilter
            .apply(withArchived, EpisodeFilter.Filter.ARCHIVED, EpisodeFilter.Order.NEWEST_FIRST)
            .map { it.uuid }
        assertEquals(listOf("e"), result)
    }

    // --- Search --------------------------------------------------------------

    @Test
    fun `search matches titles case-insensitively`() {
        val named = listOf(
            episode("1", title = "The Long Now"),
            episode("2", title = "small things"),
        )
        assertEquals(
            listOf("1"),
            EpisodeFilter.apply(named, EpisodeFilter.Filter.ALL, EpisodeFilter.Order.NEWEST_FIRST, "LONG")
                .map { it.uuid },
        )
    }

    @Test
    fun `blank query is not a filter`() {
        assertEquals(4, EpisodeFilter.apply(list, EpisodeFilter.Filter.ALL, EpisodeFilter.Order.NEWEST_FIRST, "   ").size)
    }

    @Test
    fun `search and filter combine`() {
        val result = EpisodeFilter
            .apply(list, EpisodeFilter.Filter.UNPLAYED, EpisodeFilter.Order.NEWEST_FIRST, "Episode d")
            .map { it.uuid }
        assertEquals(listOf("d"), result)
    }

    @Test
    fun `query with no matches returns empty`() {
        assertTrue(
            EpisodeFilter
                .apply(list, EpisodeFilter.Filter.ALL, EpisodeFilter.Order.NEWEST_FIRST, "zzz")
                .isEmpty(),
        )
    }

    // --- Remaining time ------------------------------------------------------

    @Test
    fun `remaining time ignores finished and unknown-duration episodes`() {
        val timed = listOf(
            episode("a", durationSeconds = 600, playedUpToSeconds = 0),
            episode("b", durationSeconds = 600, playingStatus = EpisodeEntity.PLAYED),
            episode("c", durationSeconds = null),
        )
        assertEquals(600L, EpisodeFilter.remainingSeconds(timed))
    }

    @Test
    fun `remaining time subtracts the resume position`() {
        val partial = listOf(episode("a", durationSeconds = 600, playedUpToSeconds = 120))
        assertEquals(480L, EpisodeFilter.remainingSeconds(partial))
    }

    @Test
    fun `remaining time never goes negative on an overshoot`() {
        val overshoot = listOf(episode("a", durationSeconds = 600, playedUpToSeconds = 900))
        assertEquals(0L, EpisodeFilter.remainingSeconds(overshoot))
    }

    @Test
    fun `remaining time of nothing is zero`() {
        assertEquals(0L, EpisodeFilter.remainingSeconds(emptyList()))
    }

    // --- Edge cases ----------------------------------------------------------

    @Test
    fun `an empty list filters to an empty list`() {
        EpisodeFilter.Filter.entries.forEach { filter ->
            EpisodeFilter.Order.entries.forEach { order ->
                assertTrue(
                    EpisodeFilter.apply(emptyList(), filter, order).isEmpty(),
                )
            }
        }
    }
}
