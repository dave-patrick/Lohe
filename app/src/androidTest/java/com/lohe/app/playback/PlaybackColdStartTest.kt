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
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run alone in a fresh .audit process: no activity or fixture initializes dependencies. */
@RunWith(AndroidJUnit4::class)
class PlaybackColdStartTest {
    @Test fun browserCanStartPlaybackBeforeAnyActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".audit")) { "Requires isolated .audit application" }
        val contextField = ServiceLocator::class.java.getDeclaredField("context").apply { isAccessible = true }
        assumeTrue("Run this test alone in a fresh process", contextField.get(ServiceLocator) == null)
        var future: ListenableFuture<MediaBrowser>? = null
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            future = MediaBrowser.Builder(context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        }
        val browser = future!!.get(15, TimeUnit.SECONDS)
        try {
            assertNotNull(ServiceLocator.settingsStore())
            instrumentation.runOnMainSync { assertNotNull(PlaybackService.player) }
        } finally {
            instrumentation.runOnMainSync { browser.release() }
        }
    }
}
