package com.lohe.app.data

import org.junit.Assert.*
import org.junit.Test

class EpisodeProgressTest {
    @Test fun screenshotEpisodesAreComplete() {
        assertTrue(isEpisodeComplete(3530, 3545))
        assertTrue(isEpisodeComplete(2580, 2435))
        assertTrue(isEpisodeComplete(3903, 3928))
    }
    @Test fun incompleteAndUnknownDurationsKeepResumePoints() {
        assertFalse(isEpisodeComplete(600, 3600))
        assertFalse(isEpisodeComplete(600, 0))
        assertFalse(isEpisodeComplete(600, -1))
        assertFalse(isEpisodeComplete(0, 5))
        assertFalse(isEpisodeComplete(-1, 5))
    }
    @Test fun completionBoundariesMatchPlaybackPolicy() {
        assertTrue(isEpisodeComplete(950, 1000))
        assertFalse(isEpisodeComplete(949, 1000))
        assertTrue(isEpisodeComplete(90, 100))
        assertFalse(isEpisodeComplete(89, 100))
    }
}
