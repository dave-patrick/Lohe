package com.lohe.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.SessionToken
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.lohe.app.data.ServiceLocator
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Host contracts exercised with local silent audio, never the user's app or network. */
@RunWith(AndroidJUnit4::class)
class PlaybackResumptionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var context: Context
    private lateinit var browser: MediaBrowser
    private lateinit var wav: File
    private val id = "audit-resume-episode"
    private val show = "audit-resume-show"

    private fun <T> onMainFuture(block: () -> ListenableFuture<T>): T {
        var result: ListenableFuture<T>? = null
        instrumentation.runOnMainSync { result = block() }
        return result!!.get(15, TimeUnit.SECONDS)
    }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun await(message: String, condition: () -> Boolean) {
        repeat(150) { if (condition()) return; Thread.sleep(100) }
        fail(message)
    }

    private fun currentIsPlaying(episodeId: String): Boolean {
        var result = false
        main { result = browser.currentMediaItem?.mediaId == episodeId && browser.isPlaying }
        return result
    }

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        check(context.packageName.endsWith(".audit"))
        ServiceLocator.init(context)
        context.getSharedPreferences("playback-resumption", Context.MODE_PRIVATE).edit().clear().commit()
        wav = File(context.cacheDir, "resumption-fixture.wav")
        val rate = 8000
        val samples = rate * 120
        fun FileOutputStream.le(value: Int, count: Int) { repeat(count) { write((value shr (it * 8)) and 255) } }
        FileOutputStream(wav).use { out ->
            out.write("RIFF".toByteArray()); out.le(36 + samples * 2, 4); out.write("WAVEfmt ".toByteArray())
            out.le(16, 4); out.le(1, 2); out.le(1, 2); out.le(rate, 4); out.le(rate * 2, 4)
            out.le(2, 2); out.le(16, 2); out.write("data".toByteArray()); out.le(samples * 2, 4)
            out.write(ByteArray(samples * 2))
        }
        runBlocking {
            val db = ServiceLocator.database()
            db.podcastDao().upsert(PodcastEntity(show, "Resume fixture", feedUrl = "https://example.invalid/resume"))
            db.episodeDao().upsertAll(listOf(EpisodeEntity(id, show, "Resume episode", Uri.fromFile(wav).toString(), 1,
                durationSeconds = 120, playingStatus = 1, playedUpToSeconds = 12, lastPlayedAt = Long.MAX_VALUE - 1)))
        }
        browser = onMainFuture { MediaBrowser.Builder(context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync() }
        main { browser.pause(); browser.stop(); browser.clearMediaItems() }
    }

    @After fun cleanup() {
        if (::browser.isInitialized) main { browser.pause(); browser.stop(); browser.clearMediaItems(); browser.release() }
        if (InstrumentationRegistry.getArguments().getString("keepResumeFixture") == "true") return
        runBlocking { ServiceLocator.database().podcastDao().deleteByUuid(show) }
        if (::wav.isInitialized) wav.delete()
        context.getSharedPreferences("playback-resumption", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun recentRootReturnsLastEpisodeWithoutStartingPlayback() {
        val params = LibraryParams.Builder().setRecent(true).build()
        val root = onMainFuture { browser.getLibraryRoot(params) }
        assertEquals("lohe-recent", root.value?.mediaId)
        val children = onMainFuture { browser.getChildren("lohe-recent", 0, 10, params) }
        assertEquals(listOf(id), children.value?.map { it.mediaId })
        main { assertEquals(0, browser.mediaItemCount); assertFalse(browser.isPlaying) }
    }

    @Test fun playFromEmptySessionRestoresEpisodeAndSavedPosition() {
        main { browser.play() }
        repeat(100) {
            var ready = false
            main {
                ready = browser.currentMediaItem?.mediaId == id && browser.isPlaying
                if (ready) {
                    assertEquals(id, PlaybackResumptionStore(context).episodeId)
                    assertTrue("Resume at saved position", browser.currentPosition >= 12_000L)
                    assertTrue("Do not restart at beginning or end", browser.currentPosition < 17_000L)
                }
            }
            if (ready) return
            Thread.sleep(100)
        }
        fail("Host Play on an empty session did not resume the last episode")
    }

    @Test fun storedIdentityNeverFallsBackWhenArchivedPlayedDeletedOrMissing() {
        val db = ServiceLocator.database()
        val original = runBlocking { db.episodeDao().get(id)!! }
        val prefs = context.getSharedPreferences("playback-resumption", Context.MODE_PRIVATE)
        prefs.edit().putString("last_episode_id", id).commit()
        val params = LibraryParams.Builder().setRecent(true).build()
        listOf(original.copy(isArchived = true), original.copy(isDeleted = true),
            original.copy(playingStatus = EpisodeEntity.PLAYED), original.copy(playedUpToSeconds = 119)).forEach { episode ->
            runBlocking { db.episodeDao().upsertAll(listOf(episode)) }
            val result = onMainFuture { browser.getChildren("lohe-recent", 0, 10, params) }
            assertTrue("Ineligible saved episode must not be resumed", result.value?.isEmpty() == true)
        }
        runBlocking { db.episodeDao().upsertAll(listOf(original)) }
        prefs.edit().putString("last_episode_id", "missing-id").commit()
        val result = onMainFuture { browser.getChildren("lohe-recent", 0, 10, params) }
        assertTrue("Missing saved ID must not fall back to an older episode", result.value?.isEmpty() == true)
        main { assertEquals(0, browser.mediaItemCount); assertFalse(browser.isPlaying) }
    }

    @Test fun legacyResumptionDoesNotMistakeNewlyQueuedForLastPlayed() {
        runBlocking {
            val original = ServiceLocator.database().episodeDao().get(id)!!
            ServiceLocator.database().episodeDao().upsertAll(listOf(original.copy(
                uuid = "$id-newly-queued", playingStatus = EpisodeEntity.UNPLAYED,
                playedUpToSeconds = 0, queuePosition = 0, lastPlayedAt = Long.MAX_VALUE)))
        }
        val params = LibraryParams.Builder().setRecent(true).build()
        val result = onMainFuture { browser.getChildren("lohe-recent", 0, 10, params) }
        assertEquals("Queueing a new episode is not playback", listOf(id), result.value?.map { it.mediaId })
    }

    @Test fun requeueingAnOlderInProgressEpisodePreservesLastPlaybackIdentity() {
        val now = System.currentTimeMillis()
        val older = "$id-older"
        runBlocking {
            val db = ServiceLocator.database()
            val original = db.episodeDao().get(id)!!
            db.episodeDao().upsertAll(listOf(original.copy(lastPlayedAt = now - 10_000L),
                original.copy(uuid = older, lastPlayedAt = now - 20_000L)))
            ServiceLocator.subscriptionRepository().enqueue(older)
            assertEquals(now - 20_000L, db.episodeDao().get(older)!!.lastPlayedAt)
        }
        val params = LibraryParams.Builder().setRecent(true).build()
        val result = onMainFuture { browser.getChildren("lohe-recent", 0, 10, params) }
        assertEquals(listOf(id), result.value?.map { it.mediaId })
    }

    @Test fun continuousPlaybackRemembersTheNewEpisode() {
        val next = "$id-next"
        runBlocking {
            val original = ServiceLocator.database().episodeDao().get(id)!!
            ServiceLocator.database().episodeDao().upsertAll(listOf(original.copy(uuid = next, title = "Next resume episode")))
        }
        main {
            browser.setMediaItems(listOf(MediaItem.Builder().setMediaId(id).build(), MediaItem.Builder().setMediaId(next).build()), 0, 119_500L)
            browser.prepare()
            browser.play()
        }
        await("continuous playback did not reach second episode") { currentIsPlaying(next) }
        assertEquals(next, PlaybackResumptionStore(context).episodeId)
        main { browser.pause(); browser.stop(); browser.clearMediaItems() }
        main { browser.play() }
        await("second episode was not restored") { currentIsPlaying(next) }
    }

    @Test fun archiveClearIsGuardedAndCancelsDeferredPlayback() {
        val connection = PlayerConnection(context)
        try {
            main {
                connection.play(MediaItem.Builder().setMediaId(id).build())
                connection.stopAndClearEpisode(id)
                connection.attach()
            }
            await("connection failed") { var ready = false; main { ready = connection.controller != null }; ready }
            main { assertEquals(0, connection.controller!!.mediaItemCount) }
            main { browser.setMediaItem(MediaItem.Builder().setMediaId(id).build()); browser.prepare() }
            await("fixture not prepared") { var ready = false; main { ready = browser.playbackState == Player.STATE_READY }; ready }
            main { connection.stopAndClearEpisode("another-episode") }
            main { assertEquals(id, connection.controller!!.currentMediaItem?.mediaId) }
            main { connection.stopAndClearEpisode(id) }
            await("archived loaded episode not cleared") { var empty = false; main { empty = browser.mediaItemCount == 0 }; empty }
        } finally {
            main { connection.detach() }
        }
    }

    @Test fun mediaButtonReceiverIsAvailableForColdResumption() {
        val receivers = context.packageManager.queryBroadcastReceivers(
            android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).setPackage(context.packageName), 0)
        assertTrue(receivers.any { it.activityInfo.name == "androidx.media3.session.MediaButtonReceiver" && it.activityInfo.exported })
    }

    @Test fun actualPlaybackPersistsIdentityForColdProcess() {
        main { browser.play() }
        await("fixture did not play") { currentIsPlaying(id) }
        main { browser.pause() }
        await("pause position not saved") {
            runBlocking { ServiceLocator.database().episodeDao().get(id)!!.playedUpToSeconds >= 12 }
        }
        assertEquals(id, PlaybackResumptionStore(context).episodeId)
    }
}
