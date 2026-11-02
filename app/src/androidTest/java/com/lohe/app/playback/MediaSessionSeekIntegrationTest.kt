package com.lohe.app.playback

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.lohe.app.data.ServiceLocator
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the controller-facing seek controls with isolated settings and local audio. */
@RunWith(AndroidJUnit4::class)
class MediaSessionSeekIntegrationTest {
    private lateinit var browser: MediaBrowser
    private lateinit var wav: File
    private var originalBack = 10
    private var originalForward = 30

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun <T> onMainFuture(block: () -> ListenableFuture<T>): T {
        var future: ListenableFuture<T>? = null
        onMain { future = block() }
        return future!!.get(15, TimeUnit.SECONDS)
    }
    private fun await(description: String, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for $description")
    }

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".audit")) { "Requires isolated .audit application" }
        ServiceLocator.init(context)
        val settings = ServiceLocator.settingsStore()
        runBlocking {
            val initial = settings.settings.first()
            originalBack = initial.skipBackSeconds
            originalForward = initial.skipForwardSeconds
            settings.setSkipBackSeconds(15)
            settings.setSkipForwardSeconds(30)
        }
        wav = File(context.cacheDir, "seek-fixture.wav")
        writeSilentWav(wav)
        browser = onMainFuture {
            MediaBrowser.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        }
        onMain {
            Playback.sleepTimer.cancel()
            val player = PlaybackService.player!!
            player.setMediaItem(PlaybackService.mediaItemFor("audit-seek-fixture", Uri.fromFile(wav).toString(), "Seek fixture"))
            player.prepare()
        }
        await("seekable fixture") {
            var ready = false
            onMain { ready = PlaybackService.player?.playbackState == Player.STATE_READY && PlaybackService.player?.isCurrentMediaItemSeekable == true }
            ready
        }
    }

    @After fun tearDown() {
        if (::browser.isInitialized) onMain {
            PlaybackService.player?.stop()
            PlaybackService.player?.clearMediaItems()
            browser.release()
        }
        runBlocking {
            ServiceLocator.settingsStore().setSkipBackSeconds(originalBack)
            ServiceLocator.settingsStore().setSkipForwardSeconds(originalForward)
        }
        if (::wav.isInitialized) wav.delete()
    }

    @Test fun browserExposesSeekButtonsAndUsesUpdatedIncrements() {
        var commands = emptyList<Int>()
        onMain { commands = browser.mediaButtonPreferences.map { it.playerCommand } }
        assertTrue("missing seek back preference: $commands", Player.COMMAND_SEEK_BACK in commands)
        assertTrue("missing seek forward preference: $commands", Player.COMMAND_SEEK_FORWARD in commands)

        runBlocking {
            ServiceLocator.settingsStore().setSkipBackSeconds(20)
            ServiceLocator.settingsStore().setSkipForwardSeconds(45)
        }
        await("settings applied to player") {
            var matched = false
            onMain { matched = PlaybackService.player?.seekBackIncrement == 20_000L && PlaybackService.player?.seekForwardIncrement == 45_000L }
            matched
        }
        onMain { PlaybackService.player!!.seekTo(30_000L) }
        await("starting position") { position() == 30_000L }
        onMain { browser.seekForward() }
        await("forward seek") { position() == 75_000L }
        onMain { browser.seekBack() }
        await("back seek") { position() == 55_000L }
        assertEquals(55_000L, position())
    }

    @Test fun seekButtonsHaveNamesInModernAndLegacyPlaybackState() {
        var names = emptyList<String>()
        onMain {
            names = browser.mediaButtonPreferences
                .filter { it.playerCommand == Player.COMMAND_SEEK_BACK || it.playerCommand == Player.COMMAND_SEEK_FORWARD }
                .map { it.displayName?.toString().orEmpty() }
        }
        assertEquals("expected both seek buttons", 2, names.size)
        assertTrue("seek button names must be nonempty: $names", names.all { it.isNotBlank() })

        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = MediaController(context, checkNotNull(PlaybackService.platformToken))
        var legacyNames = emptyList<String>()
        await("legacy seek actions") {
            legacyNames = controller.playbackState?.customActions?.map { it.name?.toString().orEmpty() }.orEmpty()
            names.all { it in legacyNames }
        }
        assertTrue("legacy seek action names missing: $legacyNames", names.all { it in legacyNames })
    }

    private fun position(): Long {
        var value = -1L
        onMain { value = PlaybackService.player?.currentPosition ?: -1L }
        return value
    }

    private fun writeSilentWav(file: File) {
        val sampleRate = 8_000
        val samples = sampleRate * 120
        fun FileOutputStream.le(value: Int, bytes: Int) { repeat(bytes) { write((value shr (it * 8)) and 0xff) } }
        FileOutputStream(file).use { out ->
            out.write("RIFF".toByteArray()); out.le(36 + samples * 2, 4); out.write("WAVEfmt ".toByteArray())
            out.le(16, 4); out.le(1, 2); out.le(1, 2); out.le(sampleRate, 4); out.le(sampleRate * 2, 4)
            out.le(2, 2); out.le(16, 2); out.write("data".toByteArray()); out.le(samples * 2, 4)
            out.write(ByteArray(samples * 2))
        }
    }
}
