package com.lohe.app.ui.navigation

import com.lohe.app.data.db.entity.EpisodeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class EpisodeMetaTest {

    private val now = 1_760_000_000_000L

    private fun epochOf(value: String): Long =
        LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun episode(
        published: Long = 0L,
        durationSeconds: Int? = null,
        playedUpToSeconds: Long = 0L,
        playingStatus: Int = EpisodeSummary.UNPLAYED,
    ) = EpisodeSummary(
        uuid = "u",
        podcastUuid = "p",
        title = "t",
        audioUrl = "https://x/y.mp3",
        published = published,
        durationSeconds = durationSeconds,
        playedUpToSeconds = playedUpToSeconds,
        playingStatus = playingStatus,
    )

    // --- Duration ------------------------------------------------------------

    @Test
    fun `under a minute reads in seconds`() {
        assertEquals("45s", EpisodeMeta.duration(45))
    }

    @Test
    fun `minutes round down`() {
        assertEquals("24 min", EpisodeMeta.duration(24 * 60 + 30))
    }

    @Test
    fun `whole hours drop the minutes`() {
        assertEquals("2 hr", EpisodeMeta.duration(7_200))
    }

    @Test
    fun `mixed hours and minutes keep both`() {
        assertEquals("1 hr 5 min", EpisodeMeta.duration(3_900))
    }

    @Test
    fun `an unknown duration is blank rather than zero`() {
        // "0 min" on a row reads as a broken feed; a gap reads as unknown.
        assertEquals("", EpisodeMeta.duration(null))
    }

    @Test
    fun `a zero or negative duration is treated as unknown`() {
        assertEquals("", EpisodeMeta.duration(0))
        assertEquals("", EpisodeMeta.duration(-10))
    }

    // --- Published -----------------------------------------------------------

    @Test
    fun `today`() {
        assertEquals("Today", EpisodeMeta.published(now - 60_000, now))
    }

    @Test
    fun `yesterday`() {
        val oneDay = 86_400_000L
        assertEquals("Yesterday", EpisodeMeta.published(now - oneDay, now))
    }

    @Test
    fun `within the week reads as days ago`() {
        val threeDays = 3 * 86_400_000L
        assertEquals("3d ago", EpisodeMeta.published(now - threeDays, now))
    }

    @Test
    fun `older than a week switches to a date`() {
        val old = epochOf("2024-03-12")
        assertEquals("12 Mar 2024", EpisodeMeta.published(old, now))
    }

    @Test
    fun `earlier this year omits the year`() {
        val sameYear = epochOf("2025-03-12")
        val later = epochOf("2025-09-01")
        assertEquals("12 Mar", EpisodeMeta.published(sameYear, later))
    }

    @Test
    fun `an unset publish date is blank`() {
        assertEquals("", EpisodeMeta.published(0L, now))
    }

    // --- The composed line ---------------------------------------------------

    @Test
    fun `date and duration combine`() {
        val line = EpisodeMeta.line(
            episode(published = now - 2 * 86_400_000L, durationSeconds = 1_500),
            now,
        )
        assertEquals("2d ago · 25 min", line)
    }

    @Test
    fun `in progress shows what is left`() {
        val line = EpisodeMeta.line(
            episode(
                published = now - 2 * 86_400_000L,
                durationSeconds = 1_500,
                playedUpToSeconds = 900,
                playingStatus = EpisodeSummary.IN_PROGRESS,
            ),
            now,
        )
        // 1500 - 900 = 600s = 10 min remaining.
        assertEquals("10 min left of 25 min", line)
    }

    @Test
    fun `an overshot resume position reads as finished, not as a full episode left`() {
        // A resume point past the end means the episode is done. Reporting the
        // full runtime as "left" would offer someone ten minutes of an episode
        // they have already finished.
        val line = EpisodeMeta.line(
            episode(
                durationSeconds = 600,
                playedUpToSeconds = 5_000,
                playingStatus = EpisodeSummary.IN_PROGRESS,
            ),
            now,
        )
        assertEquals("10 min", line)
    }

    @Test
    fun `a date alone is used when the duration is unknown`() {
        assertEquals("Today", EpisodeMeta.line(episode(published = now - 1_000), now))
    }

    @Test
    fun `a duration alone is used when the date is unknown`() {
        assertEquals("25 min", EpisodeMeta.line(episode(durationSeconds = 1_500), now))
    }

    @Test
    fun `an episode with neither renders as an empty line`() {
        // Blank, not a placeholder: a row of dashes is worse than no metadata.
        assertTrue(EpisodeMeta.line(episode(), now).isEmpty())
    }

    @Test
    fun `a played episode drops the remaining-time treatment`() {
        val line = EpisodeMeta.line(
            episode(
                published = now - 86_400_000L,
                durationSeconds = 1_500,
                playedUpToSeconds = 1_500,
                playingStatus = EpisodeSummary.PLAYED,
            ),
            now,
        )
        assertEquals("Yesterday · 25 min", line)
    }
}