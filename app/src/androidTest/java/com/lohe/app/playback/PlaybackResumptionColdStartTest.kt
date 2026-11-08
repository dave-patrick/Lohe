package com.lohe.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.lohe.app.data.ServiceLocator
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run after the keepResumeFixture seed in a fresh instrumentation process. */
@RunWith(AndroidJUnit4::class)
class PlaybackResumptionColdStartTest {
    @Test fun lastPlayingEpisodeResumesBeforeAnyActivityInitializesDependencies() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".audit"))
        val contextField = ServiceLocator::class.java.getDeclaredField("context").apply { isAccessible = true }
        assertNull("Requires a fresh process without an activity", contextField.get(ServiceLocator))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var future: ListenableFuture<MediaBrowser>? = null
        instrumentation.runOnMainSync {
            future = MediaBrowser.Builder(context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        }
        val browser = future!!.get(15, TimeUnit.SECONDS)
        try {
            instrumentation.runOnMainSync { browser.play() }
            var resumed = false
            repeat(150) {
                if (!resumed) {
                    instrumentation.runOnMainSync {
                        resumed = browser.currentMediaItem?.mediaId == "audit-resume-episode" && browser.isPlaying
                        if (resumed) assertTrue(browser.currentPosition >= 12_000L)
                    }
                    if (!resumed) Thread.sleep(100)
                }
            }
            assertTrue("Persisted episode did not resume after process recreation", resumed)
        } finally {
            instrumentation.runOnMainSync { browser.pause(); browser.stop(); browser.clearMediaItems(); browser.release() }
            runBlocking { ServiceLocator.database().podcastDao().deleteByUuid("audit-resume-show") }
            File(context.cacheDir, "resumption-fixture.wav").delete()
            context.getSharedPreferences("playback-resumption", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
