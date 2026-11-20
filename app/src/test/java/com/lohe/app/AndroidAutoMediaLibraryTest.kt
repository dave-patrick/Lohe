package com.lohe.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.lohe.app.data.local.LoheDatabase
import com.lohe.app.data.local.SmartPlaylistType
import com.lohe.app.data.repository.LoheRepository
import com.lohe.app.playback.LohePlaybackService
import com.lohe.app.playback.LohePlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidAutoMediaLibraryTest {

    private lateinit var context: Context
    private lateinit var serviceController: ServiceController<LohePlaybackService>
    private lateinit var service: LohePlaybackService
    private lateinit var session: MediaLibraryService.MediaLibrarySession
    private lateinit var controllerInfo: MediaSession.ControllerInfo
    private lateinit var testScope: CoroutineScope

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val db = LoheDatabase.getInstance(context)
        val repo = LoheRepository(db.loheDao())
        repo.seedPublicSampleShowsIfEmpty()

        // Mark sample_ep_2 as downloaded so Downloads section has a deterministic offline item
        val ep2 = db.loheDao().getEpisodeById("sample_ep_2")
        if (ep2 != null) {
            db.loheDao().updateEpisode(
                ep2.copy(
                    isDownloaded = true,
                    localFilePath = "file:///data/user/0/com.lohe.app/files/sample_ep_2.mp3",
                    resumePositionMs = 74_000L
                )
            )
        }

        serviceController = Robolectric.buildService(LohePlaybackService::class.java).create()
        service = serviceController.get()
        shadowOf(Looper.getMainLooper()).idle()

        val remoteUserInfo = androidx.media3.session.legacy.MediaSessionManager.RemoteUserInfo(
            "com.google.android.projection.gearhead",
            1001,
            1001
        )
        val ctor = MediaSession.ControllerInfo::class.java.declaredConstructors.first().apply {
            isAccessible = true
        }
        val args = ctor.parameterTypes.map { type ->
            when {
                type == androidx.media3.session.legacy.MediaSessionManager.RemoteUserInfo::class.java -> remoteUserInfo
                type == Int::class.javaPrimitiveType || type == Int::class.java -> 0
                type == Boolean::class.javaPrimitiveType || type == Boolean::class.java -> false
                type == android.os.Bundle::class.java -> android.os.Bundle.EMPTY
                else -> null
            }
        }.toTypedArray()
        controllerInfo = ctor.newInstance(*args) as MediaSession.ControllerInfo
        session = requireNotNull(service.onGetSession(controllerInfo))
    }

    @After
    fun tearDown() {
        runCatching { serviceController.destroy() }
        testScope.cancel()
    }

    private fun <T> awaitMainFuture(future: com.google.common.util.concurrent.ListenableFuture<T>): T {
        while (!future.isDone) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        return future.get()
    }

    @Test
    fun `verify Android Auto browse tree sections, smart playlists, and never starting audio on browse`() {
        val player = requireNotNull(service.getPlayer())
        assertFalse("Connecting must not start audio", player.isPlaying)
        assertFalse("Connecting must not set playWhenReady", player.playWhenReady)

        val rootResult = awaitMainFuture(
            service.libraryCallback.onGetLibraryRoot(session, controllerInfo, null)
        )
        assertEquals(LohePlaybackService.NODE_ROOT, rootResult.value?.mediaId)

        val rootChildrenResult = awaitMainFuture(
            service.libraryCallback.onGetChildren(
                session,
                controllerInfo,
                LohePlaybackService.NODE_ROOT,
                0,
                20,
                null
            )
        )
        val rootSections = requireNotNull(rootChildrenResult.value)
        val sectionTitles = rootSections.map { it.mediaMetadata.title.toString() }
        assertEquals(
            listOf("Continue listening", "Up Next", "Downloads", "Shows", "Playlists"),
            sectionTitles
        )

        // 1. Continue listening
        val continueListening = awaitMainFuture(
            service.libraryCallback.onGetChildren(
                session,
                controllerInfo,
                LohePlaybackService.NODE_CONTINUE_LISTENING,
                0,
                20,
                null
            )
        ).value.orEmpty()
        assertTrue("Continue listening should list in-progress episodes", continueListening.isNotEmpty())
        assertTrue(continueListening.all { it.mediaMetadata.isPlayable == true })

        // 2. Up Next
        val upNext = awaitMainFuture(
            service.libraryCallback.onGetChildren(
                session,
                controllerInfo,
                LohePlaybackService.NODE_UP_NEXT,
                0,
                20,
                null
            )
        ).value.orEmpty()
        assertTrue("Up Next should list queued episodes", upNext.isNotEmpty())

        // 3. Downloads
        val downloads = awaitMainFuture(
            service.libraryCallback.onGetChildren(
                session,
                controllerInfo,
                LohePlaybackService.NODE_DOWNLOADS,
                0,
                20,
                null
            )
        ).value.orEmpty()
        assertTrue("Downloads should list downloaded episodes", downloads.any { it.mediaId == "sample_ep_2" })

        // 4. Shows -> each show lists its episodes
        val shows = awaitMainFuture(
            service.libraryCallback.onGetChildren(
                session,
                controllerInfo,
                LohePlaybackService.NODE_SHOWS,
                0,
                20,
                null
            )
        ).value.orEmpty()
        assertTrue("Shows should list subscribed podcasts", shows.isNotEmpty())
        val firstShowId = shows.first().mediaId
        val showEpisodes = awaitMainFuture(
            service.libraryCallback.onGetChildren(
                session,
                controllerInfo,
                firstShowId,
                0,
                20,
                null
            )
        ).value.orEmpty()
        assertTrue("Each show should list its playable episodes", showEpisodes.isNotEmpty())
        assertTrue(showEpisodes.all { it.mediaMetadata.isPlayable == true })

        // 5. Playlists (including smart playlists)
        val playlists = awaitMainFuture(
            service.libraryCallback.onGetChildren(
                session,
                controllerInfo,
                LohePlaybackService.NODE_PLAYLISTS,
                0,
                20,
                null
            )
        ).value.orEmpty()
        val playlistIds = playlists.map { it.mediaId }
        SmartPlaylistType.entries.forEach { smart ->
            assertTrue(
                "Smart playlist ${smart.title} should be present under Playlists",
                playlistIds.contains("${LohePlaybackService.PREFIX_PLAYLIST}${smart.id}")
            )
        }
        val smartEverythingChildren = awaitMainFuture(
            service.libraryCallback.onGetChildren(
                session,
                controllerInfo,
                "${LohePlaybackService.PREFIX_PLAYLIST}${SmartPlaylistType.EVERYTHING.id}",
                0,
                20,
                null
            )
        ).value.orEmpty()
        assertTrue("Smart playlist should resolve its playable episodes", smartEverythingChildren.isNotEmpty())

        // Ensure browsing all nodes never started audio
        assertFalse("Browsing must never start audio", player.isPlaying)
        assertFalse("Browsing must never set playWhenReady", player.playWhenReady)
    }

    @Test
    fun `verify text and voice search for shows and episodes, pre-playback resume position, and shared car-phone state`() {
        // Text search
        val searchResults = awaitMainFuture(
            service.libraryCallback.onGetSearchResult(
                session,
                controllerInfo,
                "Kani",
                0,
                20,
                null
            )
        ).value.orEmpty()
        assertTrue("Search should find matching episodes or shows", searchResults.isNotEmpty())

        // Voice search via RequestMetadata.searchQuery
        val voiceItem = MediaItem.Builder()
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setSearchQuery("Kani Dragonfly")
                    .build()
            )
            .build()
        val voiceSelection = awaitMainFuture(
            service.libraryCallback.onSetMediaItems(
                session,
                controllerInfo,
                listOf(voiceItem),
                C.INDEX_UNSET,
                C.TIME_UNSET
            )
        )
        assertTrue("Voice search should resolve playable items", voiceSelection.mediaItems.isNotEmpty())
        assertEquals("sample_ep_1", voiceSelection.mediaItems[voiceSelection.startIndex].mediaId)
        assertEquals("Saved resume position must be supplied before playback starts", 420_000L, voiceSelection.startPositionMs)

        // Direct episode selection supplies saved resume position (74_000L for sample_ep_2) before playback starts
        val ep2Request = MediaItem.Builder().setMediaId("sample_ep_2").build()
        val ep2Selection = awaitMainFuture(
            service.libraryCallback.onSetMediaItems(
                session,
                controllerInfo,
                listOf(ep2Request),
                C.INDEX_UNSET,
                C.TIME_UNSET
            )
        )
        assertEquals(74_000L, ep2Selection.startPositionMs)

        // Verify shared playback state & queue between car (service player) and phone (LohePlayerController)
        val phoneController = LohePlayerController(
            context = context,
            scope = testScope,
            onProgressPersist = { _, _, _ -> },
            onEpisodeCompleted = {}
        )
        shadowOf(Looper.getMainLooper()).idle()

        val player = requireNotNull(service.getPlayer())
        player.setMediaItems(
            ep2Selection.mediaItems,
            ep2Selection.startIndex,
            ep2Selection.startPositionMs
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(74_000L, player.currentPosition)
        assertEquals("sample_ep_2", phoneController.state.value.currentEpisode?.id)
        assertEquals(74_000L, phoneController.state.value.positionMs)
        assertEquals(
            ep2Selection.mediaItems.map { it.mediaId },
            phoneController.state.value.queueEpisodeIds
        )

        phoneController.release()
    }

    @Test
    fun `verify automotive_app_desc meta-data and monochrome car icon resource`() {
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        val metaData = requireNotNull(appInfo.metaData)
        assertEquals(
            R.xml.automotive_app_desc,
            metaData.getInt("com.google.android.gms.car.application")
        )
        assertEquals(
            R.drawable.ic_car_media_notification,
            metaData.getInt("com.google.android.gms.car.notification.SmallIcon")
        )
        val drawable = context.getDrawable(R.drawable.ic_car_media_notification)
        assertNotNull("Monochrome car vector icon must exist", drawable)
    }
}
