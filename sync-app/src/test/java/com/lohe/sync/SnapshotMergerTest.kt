package com.lohe.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

        val merged = Json.parseToJsonElement(
            SnapshotMerger.merge(playJson, fossJson, primary = Edition.PLAY, nowMillis = 3000),
        ).jsonObject

        // Both shows present
        assertEquals(2, merged["podcasts"]!!.jsonArray.size)

        val eps = merged["episodes"]!!.jsonArray.associate {
            val o = it.jsonObject
            o["uuid"]!!.jsonPrimitive.content to o
        }
        // e1 finished more recently on FOSS -> becomes PLAYED, drops out of Up Next
        val e1 = eps["e1"]!!
        assertEquals(2, e1["playingStatus"]!!.jsonPrimitive.int)
        assertEquals(600L, e1["listenedSeconds"]!!.jsonPrimitive.long)
        assertNull(e1["queuePosition"]!!.jsonPrimitive.intOrNull)

        // e2 resumed more recently on FOSS, stays in Play's Up Next at position 0
        val e2 = eps["e2"]!!
        assertEquals(1, e2["playingStatus"]!!.jsonPrimitive.int)
        assertEquals(45L, e2["playedUpToSeconds"]!!.jsonPrimitive.long)
        assertEquals(0, e2["queuePosition"]!!.jsonPrimitive.int)

        // Both playlists merged; finished e1 removed from unfinished Daily playlist
        val playlists = merged["playlists"]!!.jsonArray.associate {
            val o = it.jsonObject
            o["id"]!!.jsonPrimitive.content to o
        }
        assertEquals(2, playlists.size)
        val dailyEps = playlists["pl1"]!!["episodeUuids"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("e2"), dailyEps)

        // Both folders merged
        val folders = merged["podcastFolders"]!!.jsonObject
        assertEquals("Tech", folders["p1"]!!.jsonPrimitive.content)
        assertEquals("News", folders["p2"]!!.jsonPrimitive.content)
    }
}
