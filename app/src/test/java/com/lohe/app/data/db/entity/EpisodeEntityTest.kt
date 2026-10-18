package com.lohe.app.data.db.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeEntityTest {

    private fun episode(
        playingStatus: Int = EpisodeEntity.UNPLAYED,
        playedUpToSeconds: Long = 0L,
        durationSeconds: Int? = null,
        queuePosition: Int? = null,
    ) = EpisodeEntity(
        uuid = "e1",
        podcastUuid = "p1",
        title = "Episode",
        audioUrl = "https://example.com/1.mp3",
        published = 0L,
        durationSeconds = durationSeconds,
        playingStatus = playingStatus,
        playedUpToSeconds = playedUpToSeconds,
        queuePosition = queuePosition,
    )

    @Test
    fun `completion is zero when the duration is unknown`() {
        assertEquals(0f, episode(playedUpToSeconds = 120L).completionFraction, 0.001f)
        assertEquals(0f, episode(durationSeconds = 0, playedUpToSeconds = 5L).completionFraction, 0.001f)
    }

    @Test
    fun `completion is the listened fraction`() {
        val e = episode(
            playingStatus = EpisodeEntity.IN_PROGRESS,
            playedUpToSeconds = 300L,
            durationSeconds = 1_200,
        )
        assertEquals(0.25f, e.completionFraction, 0.001f)
    }

    @Test
    fun `a played episode always reads as complete`() {
        val e = episode(
            playingStatus = EpisodeEntity.PLAYED,
            playedUpToSeconds = 10L,
            durationSeconds = 1_000,
        )
        assertEquals(1f, e.completionFraction, 0.001f)
    }

    @Test
    fun `completion is clamped when the position overshoots`() {
        val e = episode(
            playingStatus = EpisodeEntity.IN_PROGRESS,
            playedUpToSeconds = 5_000L,
            durationSeconds = 1_000,
        )
        assertEquals(1f, e.completionFraction, 0.001f)
    }

    @Test
    fun `status helpers reflect playingStatus`() {
        assertTrue(episode(playingStatus = EpisodeEntity.PLAYED).isPlayed)
        assertFalse(episode(playingStatus = EpisodeEntity.PLAYED).isInProgress)

        assertTrue(episode(playingStatus = EpisodeEntity.IN_PROGRESS).isInProgress)
        assertFalse(episode(playingStatus = EpisodeEntity.IN_PROGRESS).isPlayed)

        assertFalse(episode().isPlayed)
        assertFalse(episode().isInProgress)
    }

    @Test
    fun `queued is driven by the queue position`() {
        assertTrue(episode(queuePosition = 0).isQueued)
        assertTrue(episode(queuePosition = 4).isQueued)
        assertFalse(episode(queuePosition = null).isQueued)
    }

    @Test
    fun `a queue position of zero still counts as queued`() {
        // Falsy-int trap: 0 is a valid head-of-queue position.
        assertTrue(episode(queuePosition = 0).isQueued)
    }

    @Test
    fun `defaults describe an untouched episode`() {
        val fresh = episode()
        assertFalse(fresh.isPlayed)
        assertFalse(fresh.isInProgress)
        assertFalse(fresh.isQueued)
        assertEquals(0f, fresh.completionFraction, 0.001f)
    }
}
