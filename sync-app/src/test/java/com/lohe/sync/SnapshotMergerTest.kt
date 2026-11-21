package com.lohe.sync

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SnapshotMergerTest {

    @Test
    fun smartMergePicksNewerEpisodeProgressAndPreservesPrimaryUpNext() {
        val playJson = """
            {
              "schemaVersion": 1,
              "exportedAt": 1000,
              "sourcePackage": "com.lohe.app",
              "podcasts": [
                {"uuid": "p1", "title": "Show One", "feedUrl": "https://example.com/1", "subscribedAt": 100, "isSubscribed": true}
              ],
              "episodes": [
                {"uuid": "e1", "podcastUuid": "p1", "title": "Ep 1", "audioUrl": "https://example.com/e1.mp3", "published": 100, "playingStatus": 1, "playedUpToSeconds": 120, "listenedSeconds": 120, "lastPlayedAt": 500, "queuePosition": 0},
                {"uuid": "e2", "podcastUuid": "p1", "title": "Ep 2", "audioUrl": "https://example.com/e2.mp3", "published": 200, "playingStatus": 0, "playedUpToSeconds": 0, "listenedSeconds": 0, "queuePosition": 1}
              ],
              "listeningHistory": [],
              "playlists": [
                {"id": "pl1", "name": "Daily", "episodeUuids": ["e1", "e2"], "finished": false}
              ],
              "podcastFolders": {"p1": "Tech"}
            }
        """.trimIndent()

        val fossJson = """
            {
              "schemaVersion": 1,
              "exportedAt": 2000,
              "sourcePackage": "com.lohe.app.foss",
              "podcasts": [
                {"uuid": "p1", "title": "Show One", "feedUrl": "https://example.com/1", "subscribedAt": 100, "isSubscribed": true},
                {"uuid": "p2", "title": "Show Two", "feedUrl": "https://example.com/2", "subscribedAt": 200, "isSubscribed": true}
              ],
              "episodes": [
                {"uuid": "e1", "podcastUuid": "p1", "title": "Ep 1", "audioUrl": "https://example.com/e1.mp3", "published": 100, "playingStatus": 2, "playedAt": 900, "playedUpToSeconds": 0, "listenedSeconds": 600, "lastPlayedAt": 900, "queuePosition": null},
                {"uuid": "e2", "podcastUuid": "p1", "title": "Ep 2", "audioUrl": "https://example.com/e2.mp3", "published": 200, "playingStatus": 1, "playedUpToSeconds": 45, "listenedSeconds": 45, "lastPlayedAt": 950, "queuePosition": 0}
              ],
              "listeningHistory": [],
              "playlists": [
                {"id": "pl1", "name": "Daily", "episodeUuids": ["e2"], "finished": false},
                {"id": "pl2", "name": "Weekend", "episodeUuids": ["e2"], "finished": false}
              ],
              "podcastFolders": {"p2": "News"}
            }
        """.trimIndent()

        val merged = JSONObject(
            SnapshotMerger.merge(playJson, fossJson, primary = Edition.PLAY, nowMillis = 3000),
        )

        // Both shows present
        assertEquals(2, merged.getJSONArray("podcasts").length())

        val epsArr = merged.getJSONArray("episodes")
        val eps = (0 until epsArr.length()).map { epsArr.getJSONObject(it) }
            .associateBy { it.getString("uuid") }

        // e1 finished more recently on FOSS -> becomes PLAYED, drops out of Up Next
        val e1 = eps["e1"]!!
        assertEquals(2, e1.getInt("playingStatus"))
        assertEquals(600L, e1.getLong("listenedSeconds"))
        assertTrue(e1.isNull("queuePosition"))

        // e2 resumed more recently on FOSS, stays in Play's Up Next at position 0
        val e2 = eps["e2"]!!
        assertEquals(1, e2.getInt("playingStatus"))
        assertEquals(45L, e2.getLong("playedUpToSeconds"))
        assertEquals(0, e2.getInt("queuePosition"))

        // Both playlists merged; finished e1 removed from unfinished Daily playlist
        val plArr = merged.getJSONArray("playlists")
        val playlists = (0 until plArr.length()).map { plArr.getJSONObject(it) }
            .associateBy { it.getString("id") }
        assertEquals(2, playlists.size)
        val dailyEpsArr = playlists["pl1"]!!.getJSONArray("episodeUuids")
        val dailyEps = (0 until dailyEpsArr.length()).map { dailyEpsArr.getString(it) }
        assertEquals(listOf("e2"), dailyEps)

        // Both folders merged
        val folders = merged.getJSONObject("podcastFolders")
        assertEquals("Tech", folders.getString("p1"))
        assertEquals("News", folders.getString("p2"))
    }

    @Test
    fun v2DriveSyncPayload_boostsPrimaryUpNextTimestamp() {
        val primary = """{"format":"lohe-backup-v2","driveSync":{"schemaVersion":2,"upNextUpdatedAt":1000}}"""
        val secondary = """{"format":"lohe-backup-v2","driveSync":{"schemaVersion":2,"upNextUpdatedAt":5000}}"""

        assertTrue(SnapshotMerger.isDriveSyncV2Payload(primary))
        val boosted = JSONObject(SnapshotMerger.withPrimaryUpNextPriority(primary, secondary, nowMillis = 4000))
        val boostedTs = boosted.getJSONObject("driveSync").getLong("upNextUpdatedAt")
        assertTrue("Expected boosted timestamp > 5000, got $boostedTs", boostedTs > 5000L)
    }
}
