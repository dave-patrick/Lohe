package com.lohe.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStartPositionTest {
    @Test fun partiallyPlayedEpisodeStartsAtStoredPosition() {
        assertEquals(1_134_000L, playbackStartPositionMs(1_134L, 2_958_000L))
    }

    @Test fun newEpisodeStartsAtZero() {
        assertEquals(0L, playbackStartPositionMs(0L, 600_000L))
    }

    @Test fun missingDurationStillPreservesResumePosition() {
        assertEquals(125_000L, playbackStartPositionMs(125L, 0L))
    }

    @Test fun completedTailAndOutOfRangePositionsRestart() {
        assertEquals(0L, playbackStartPositionMs(595L, 600_000L))
        assertEquals(0L, playbackStartPositionMs(700L, 600_000L))
    }

    @Test fun tenSecondsRemainingIsStillResumable() {
        assertEquals(590_000L, playbackStartPositionMs(590L, 600_000L))
    }

    @Test fun negativePositionCannotSeekBeforeTheStart() {
        assertEquals(0L, playbackStartPositionMs(-5L, 600_000L))
    }
}
