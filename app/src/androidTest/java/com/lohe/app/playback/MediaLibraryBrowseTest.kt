package com.lohe.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.lohe.app.data.ServiceLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import kotlinx.coroutines.runBlocking
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.db.entity.EpisodeEntity
import androidx.media3.common.Player
import java.util.concurrent.CountDownLatch
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Browses the library the way Android Auto does.
 *
 * A car does not know anything about this app. It connects to the media
 * browser service, asks for the root, walks down and taps something. If any
 * of that breaks, the app still works perfectly on the phone and the car
 * shows an empty player — which is the worst kind of bug to find on a
 * device you cannot reach.
 */
@RunWith(AndroidJUnit4::class)
class MediaLibraryBrowseTest {

    private lateinit var browser: MediaBrowser

    /**
     * MediaController insists on being called from the thread it was built
     * on, and that is the main thread. Every call is therefore made there and
     * the answer waited for outside it.
     */
    private fun <T> onMainFuture(block: () -> ListenableFuture<T>): T {
        var future: ListenableFuture<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { future = block() }
        return future!!.get(15, TimeUnit.SECONDS)
    }

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".audit")) { "Run browser integration tests in the isolated .audit package" }
        ServiceLocator.init(context)
        runBlocking {
            ServiceLocator.database().podcastDao().upsert(PodcastEntity(
                "audit-browse-show", "Audit fixture", feedUrl = "https://example.invalid/audit-feed"))
            ServiceLocator.database().episodeDao().upsertAll(listOf(EpisodeEntity(
                "audit-browse-episode", "audit-browse-show", "Audit episode", "https://example.invalid/audit.mp3", 1)))
        }
        browser = onMainFuture {
            MediaBrowser.Builder(
                context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java)),
            ).buildAsync()
        }
    }

    @After
    fun tearDown() {
        if (::browser.isInitialized) onMain { browser.stop(); browser.release() }
        runBlocking { ServiceLocator.database().podcastDao().deleteByUuid("audit-browse-show") }
    }

    private fun childrenOf(parentId: String): List<MediaItem> {
        val result = onMainFuture { browser.getChildren(parentId, 0, 50, null) }
        assertNotNull("no children returned for $parentId", result)
        return result.value ?: emptyList()
    }

    @Test
    fun theRootOffersTheLibrary() {
        val root = onMainFuture { browser.getLibraryRoot(null) }
        assertNotNull("the service must publish a library root", root?.value)
        assertEquals(MediaLibrary.ROOT, root?.value?.mediaId)

        val shelves = childrenOf(MediaLibrary.ROOT)
        assertTrue("the root should offer shelves, got ${shelves.size}", shelves.isNotEmpty())
        val ids = shelves.map { it.mediaId }
        assertTrue("no Continue listening shelf in $ids", ids.contains(MediaLibrary.CONTINUE))
        assertTrue("no Shows shelf in $ids", ids.contains(MediaLibrary.SHOWS))
        assertTrue("no Playlists shelf in $ids", ids.contains(MediaLibrary.PLAYLISTS))
    }

    @Test
    fun showsCanBeBrowsedDownToEpisodes() {
        val shows = childrenOf(MediaLibrary.SHOWS)
        assertTrue("the library should have at least one show", shows.isNotEmpty())

        val show = shows.first { it.mediaId == MediaLibrary.showId("audit-browse-show") }
        val episodes = childrenOf(show.mediaId)
        assertTrue("a show should offer its episodes", episodes.isNotEmpty())

        val episode = episodes.first()
        assertTrue("an episode should be playable", episode.mediaMetadata.isPlayable == true)
        // The address is deliberately not in a browsed item: Media3 strips it
        // so a controller cannot play an arbitrary URI. What the car gets is
        // the id, and the session looks the episode up again when it is
        // played -- which is the next test.
        assertTrue("an episode should carry its id", episode.mediaId.isNotBlank())
        assertNotNull("an episode should have a title", episode.mediaMetadata.title)
    }

    @Test
    fun tappingAnEpisodeInACarResolvesItToSomethingPlayable() {
        val shows = childrenOf(MediaLibrary.SHOWS)
        val show = shows.first { it.mediaId == MediaLibrary.showId("audit-browse-show") }
        val episode = childrenOf(show.mediaId).first()

        // This is what the car does when a row is tapped: it hands back the
        // item it was given -- an id and no URI -- and expects the real,
        // playable one in its place.
        val resolved = CountDownLatch(1)
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem?.mediaId == "audit-browse-episode" && mediaItem.localConfiguration?.uri != null) resolved.countDown()
            }
        }
        onMain {
            PlaybackService.player!!.addListener(listener)
            browser.setMediaItems(listOf(episode))
        }
        try {
            assertTrue("the session should resolve the ID to a playable source", resolved.await(5, TimeUnit.SECONDS))
            var current: MediaItem? = null
            onMain { current = PlaybackService.player?.currentMediaItem }
            assertEquals("audit-browse-episode", current?.mediaId)
            // Media3 deliberately strips local configuration from browser-side items.
            // Verify the service-side player received the resolved source instead.
            assertEquals("https://example.invalid/audit.mp3", current?.localConfiguration?.uri.toString())
        } finally {
            onMain { PlaybackService.player?.removeListener(listener) }
        }
    }
}
