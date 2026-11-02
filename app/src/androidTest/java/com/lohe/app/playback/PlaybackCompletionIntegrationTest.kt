package com.lohe.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real player completion against an isolated database and local audio. */
@RunWith(AndroidJUnit4::class)
class PlaybackCompletionIntegrationTest {
    private lateinit var context: Context
    private lateinit var browser: MediaBrowser
    private lateinit var wav: File
    private val show = "audit-completion-show"
    private val first = "audit-completion-first"
    private val next = "audit-completion-next"

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun <T> onMainFuture(block: () -> ListenableFuture<T>): T {
        var future: ListenableFuture<T>? = null
        onMain { future = block() }
        return future!!.get(15, TimeUnit.SECONDS)
    }

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        check(context.packageName.endsWith(".audit")) { "Requires isolated .audit application" }
        ServiceLocator.init(context)
        wav = File(context.cacheDir, "completion-fixture.wav")
        writeSilentWav(wav)
        runBlocking {
            val db = ServiceLocator.database()
            db.podcastDao().upsert(PodcastEntity(show, "Completion fixture", feedUrl = "https://example.invalid/completion"))
            db.episodeDao().upsertAll(listOf(
                EpisodeEntity(first, show, "First", Uri.fromFile(wav).toString(), 1, durationSeconds = 3),
                EpisodeEntity(next, show, "Next", Uri.fromFile(wav).toString(), 2, durationSeconds = 3),
            ))
        }
        browser = onMainFuture { MediaBrowser.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync() }
        onMain {
            Playback.sleepTimer.cancel()
            PlaybackService.player!!.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            PlaybackService.player!!.pause()
            PlaybackService.player!!.clearMediaItems()
        }
    }

    @After fun tearDown() {
        if (::browser.isInitialized) onMain {
            Playback.sleepTimer.cancel()
            PlaybackService.player?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            PlaybackService.player?.pause()
            PlaybackService.player?.stop()
            PlaybackService.player?.clearMediaItems()
            browser.release()
        }
        runBlocking { ServiceLocator.database().podcastDao().deleteByUuid(show) }
        if (::wav.isInitialized) wav.delete()
    }

    private fun startFirst() = onMain {
        val player = PlaybackService.player!!
        player.setMediaItem(PlaybackService.mediaItemFor(first, Uri.fromFile(wav).toString(), "First"))
        player.prepare()
        player.play()
    }

    private fun await(description: String, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(100)
        }
        throw AssertionError("Timed out waiting for $description")
    }

    private fun isPlayed(id: String) = runBlocking { ServiceLocator.subscriptionRepository().episodeOnce(id)?.isPlayed == true }
    private fun currentId(): String? {
        var id: String? = null
        onMain { id = PlaybackService.player?.currentMediaItem?.mediaId }
        return id
    }

    @Test fun naturalCompletionWithEmptyQueueClearsFinishedItem() {
        startFirst()
        await("episode marked played") { isPlayed(first) }
        await("finished item cleared") { currentId() == null }
        assertTrue(runBlocking { ServiceLocator.subscriptionRepository().queueSnapshot().isEmpty() })
        onMain { browser.play() }
        Thread.sleep(500)
        assertNull("play must not resurrect finished episode", currentId())
    }

    @Test fun endOfEpisodeSleepStopsWithoutPlayingQueuedEpisode() {
        runBlocking { ServiceLocator.subscriptionRepository().enqueue(next) }
        Playback.sleepTimer.start(SleepTimer.Mode.EndOfEpisode)
        startFirst()
        await("episode marked played") { isPlayed(first) }
        await("finished item cleared") { currentId() == null }
        assertEquals(listOf(next), runBlocking { ServiceLocator.subscriptionRepository().queueSnapshot().map { it.uuid } })
        assertFalse(Playback.sleepTimer.state.value.active)
        onMain { browser.play() }
        Thread.sleep(500)
        assertNull("play must not resurrect finished episode", currentId())
    }

    @Test fun naturalCompletionAdvancesToQueuedEpisode() {
        runBlocking { ServiceLocator.subscriptionRepository().enqueue(next) }
        startFirst()
        await("queue advances") { currentId() == next }
        assertTrue(isPlayed(first))
    }

    @Test fun repeatOneCannotRestartAnUpNextEpisode() = repeatCannotRestartAnUpNextEpisode(androidx.media3.common.Player.REPEAT_MODE_ONE)

    @Test fun repeatAllCannotRestartAnUpNextEpisode() = repeatCannotRestartAnUpNextEpisode(androidx.media3.common.Player.REPEAT_MODE_ALL)

    @Test fun sessionDisablesRepeatWithoutRemovingPlaybackOrLibraryAccess() {
        onMain {
            val player = PlaybackService.player!!
            player.setMediaItem(PlaybackService.mediaItemFor(first, Uri.fromFile(wav).toString(), "First"))
            player.prepare()
        }
        await("paused fixture ready") {
            var ready = false
            onMain { ready = browser.playbackState == androidx.media3.common.Player.STATE_READY }
            ready
        }
        onMain {
            assertFalse(browser.isCommandAvailable(androidx.media3.common.Player.COMMAND_SET_REPEAT_MODE))
            assertTrue(browser.isCommandAvailable(androidx.media3.common.Player.COMMAND_PLAY_PAUSE))
            assertTrue(browser.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        }
        assertEquals(0, onMainFuture { browser.getLibraryRoot(null) }.resultCode)
    }

    @Test fun staleQueueReorderCannotRestoreACompletedEpisode() = runBlocking {
        val repository = ServiceLocator.subscriptionRepository()
        repository.enqueue(first)
        repository.enqueue(next)
        val staleOrder = repository.queueSnapshot().map { it.uuid }
        repository.markPlayed(first)
        repository.dequeue(first)
        repository.reorderQueue(staleOrder)
        assertEquals("an old drag snapshot must not requeue the completed episode", listOf(next),
            repository.queueSnapshot().map { it.uuid })
        // An explicit listener request can still queue a replay intentionally.
        repository.enqueue(first)
        assertTrue(repository.queueSnapshot().any { it.uuid == first })
    }

    private fun repeatCannotRestartAnUpNextEpisode(mode: Int) {
        runBlocking {
            val repository = ServiceLocator.subscriptionRepository()
            repository.enqueue(first)
            repository.enqueue(next)
        }
        try {
            // Model repeat enabled by an external session controller.
            onMain { PlaybackService.player!!.repeatMode = mode }
            startFirst()
            await("repeat must not prevent advancing to next episode") { currentId() == next }
            onMain { assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, PlaybackService.player!!.repeatMode) }
            assertTrue(isPlayed(first))
            assertFalse(runBlocking { ServiceLocator.subscriptionRepository().queueSnapshot().any { it.uuid == first } })
            await("the queue drains without replaying either episode") { currentId() == null }
            assertTrue(isPlayed(next))
            assertTrue(runBlocking { ServiceLocator.subscriptionRepository().queueSnapshot().isEmpty() })
        } finally {
            onMain { PlaybackService.player!!.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF }
        }
    }

    @Test fun interruptedPausedEpisodeResumesLatestSeekAfterOtherEpisodeCompletes() =
        interruptedEpisodeResumesAfterOtherEpisodeCompletes(wasPlaying = false)

    @Test fun interruptedPlayingEpisodeResumesLatestSeekAfterOtherEpisodeCompletes() =
        interruptedEpisodeResumesAfterOtherEpisodeCompletes(wasPlaying = true)

    private fun interruptedEpisodeResumesAfterOtherEpisodeCompletes(wasPlaying: Boolean) {
        val longWav = File(context.cacheDir, "interrupted-fixture.wav")
        writeSilentWav(longWav, 120)
        try {
            runBlocking {
                ServiceLocator.database().episodeDao().upsertAll(listOf(
                    EpisodeEntity(next, show, "Interrupted", Uri.fromFile(longWav).toString(), 2, durationSeconds = 120),
                ))
            }
            onMain {
                val player = PlaybackService.player!!
                player.pause()
                player.setMediaItem(PlaybackService.mediaItemFor(next, Uri.fromFile(longWav).toString(), "Interrupted"))
                player.prepare()
            }
            await("paused episode ready") {
                var ready = false
                onMain {
                    ready = browser.currentMediaItem?.mediaId == next &&
                        browser.playbackState == androidx.media3.common.Player.STATE_READY &&
                        !browser.playWhenReady && !PlaybackService.player!!.playWhenReady
                }
                ready
            }
            onMain { browser.seekTo(60_000) }
            await("seek reflected by session") {
                var sought = false
                onMain {
                    sought = browser.currentPosition >= 60_000 && PlaybackService.player!!.currentPosition >= 60_000
                }
                sought
            }
            onMain {
                assertFalse("fixture must still be paused after seek", browser.playWhenReady)
                assertFalse("service fixture must still be paused after seek", PlaybackService.player!!.playWhenReady)
            }
            assertEquals("paused seek must not have already persisted the target position", 0L,
                runBlocking { ServiceLocator.subscriptionRepository().playedPosition(next) })
            if (wasPlaying) {
                onMain { browser.play() }
                await("interrupted episode playing") {
                    var playing = false
                    onMain { playing = browser.isPlaying }
                    playing
                }
            }
            runBlocking {
                withContext(Dispatchers.Main) {
                    pauseInterruptedPlayback(browser, next) {
                        ServiceLocator.subscriptionRepository().enqueue(it, toTop = true)
                    }
                }
            }
            assertTrue("switch must save the latest position before replacing media",
                runBlocking { ServiceLocator.subscriptionRepository().playedPosition(next) } >= 60L)
            startFirst()
            await("accidental episode ready") {
                var ready = false
                onMain {
                    ready = PlaybackService.player!!.currentMediaItem?.mediaId == first &&
                        PlaybackService.player!!.playbackState == androidx.media3.common.Player.STATE_READY
                }
                ready
            }
            onMain { PlaybackService.player!!.seekTo(PlaybackService.player!!.duration) }
            await("queue returns to interrupted episode") { currentId() == next }
            onMain {
                assertTrue("interrupted playback must resume at 60 seconds", PlaybackService.player!!.currentPosition >= 60_000)
                PlaybackService.player!!.pause()
            }
        } finally {
            longWav.delete()
        }
    }

    private fun writeSilentWav(file: File, durationSeconds: Int = 3) {
        val sampleRate = 8000
        val samples = sampleRate * durationSeconds
        fun FileOutputStream.le(value: Int, bytes: Int) { repeat(bytes) { write((value shr (it * 8)) and 0xff) } }
        FileOutputStream(file).use { out ->
            out.write("RIFF".toByteArray()); out.le(36 + samples * 2, 4); out.write("WAVEfmt ".toByteArray())
            out.le(16, 4); out.le(1, 2); out.le(1, 2); out.le(sampleRate, 4); out.le(sampleRate * 2, 4)
            out.le(2, 2); out.le(16, 2); out.write("data".toByteArray()); out.le(samples * 2, 4)
            out.write(ByteArray(samples * 2))
        }
    }
}
