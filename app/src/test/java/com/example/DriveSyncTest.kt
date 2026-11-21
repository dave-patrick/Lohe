package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AutoQueueMode
import com.example.data.local.CloudSyncProvider
import com.example.data.local.EpisodeBookmarkEntity
import com.example.data.local.EpisodeEntity
import com.example.data.local.LoheDatabase
import com.example.data.local.LohePreferencesRepository
import com.example.data.local.LoheUserSettings
import com.example.data.local.PlaylistEntity
import com.example.data.local.PodcastEntity
import com.example.data.network.SafDriveSyncClient
import com.example.data.repository.LoheRepository
import com.example.data.sync.DriveSyncEngine
import com.example.data.sync.DriveSyncLocalJournal
import com.example.edition.LoheBillingState
import com.example.ui.SettingsSubScreen
import com.example.ui.screens.SettingsContainerScreen
import com.example.ui.theme.LoheTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
class DriveSyncTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun samplePodcast(
        id: String,
        title: String = "Show $id",
        feedUrl: String = "https://feeds.example.org/$id.xml",
        autoQueueMode: String = AutoQueueMode.OFF.name,
        useCustomAudio: Boolean = false,
        customSpeed: Float = 1.0f
    ) = PodcastEntity(
        id = id,
        title = title,
        author = "Author $id",
        description = "Desc $id",
        feedUrl = feedUrl,
        artworkUrl = "",
        category = "Tech",
        autoQueueMode = autoQueueMode,
        useCustomAudio = useCustomAudio,
        customSpeed = customSpeed,
        isSubscribed = true,
        lastSyncedAt = 1_000L
    )

    private fun sampleEpisode(
        id: String,
        podcastId: String,
        guid: String = "guid_$id",
        title: String = "Episode $id",
        durationMs: Long = 1_000_000L,
        resumePositionMs: Long = 0L,
        isPlayed: Boolean = false,
        playedAt: Long = 0L,
        isStarred: Boolean = false,
        isArchived: Boolean = false,
        inUpNext: Boolean = false,
        upNextOrder: Int = 0
    ) = EpisodeEntity(
        id = id,
        guid = guid,
        podcastId = podcastId,
        podcastTitle = "Show $podcastId",
        title = title,
        descriptionHtml = "<p>$title</p>",
        audioUrl = "https://cdn.example.org/$guid.mp3",
        artworkUrl = "",
        publishedAt = 1_700_000_000_000L,
        durationMs = durationMs,
        resumePositionMs = resumePositionMs,
        isPlayed = isPlayed,
        playedAt = playedAt,
        isStarred = isStarred,
        isArchived = isArchived,
        inUpNext = inUpNext,
        upNextOrder = upNextOrder
    )

    @Test
    fun driveSyncEngine_mergesPlaylistsAcrossDevices_withIdRemappingAndDeletions() {
        val podA = samplePodcast("pod_a1", title = "Systems Weekly", feedUrl = "https://feeds.example.org/systems.xml")
        val epA1 = sampleEpisode("ep_a1", podcastId = "pod_a1", guid = "ep-guid-1")
        val plMorning = PlaylistEntity(
            id = "pl_morning",
            name = "Morning Commute",
            folderName = "Commute",
            assignedPodcastIdsCsv = "pod_a1",
            episodeIdsCsv = "ep_a1",
            isPinned = true,
            autoDownload = true,
            createdAt = 2_000L
        )
        val journalA = DriveSyncLocalJournal(
            playlistUpdatedAt = mapOf("pl_morning" to 2_500L),
            deletedPlaylists = mapOf("pl_old" to 3_000L)
        )
        val remotePayloadFromA = DriveSyncEngine.buildSyncPayload(
            podcasts = listOf(podA),
            episodes = listOf(epA1),
            playlists = listOf(plMorning),
            bookmarks = emptyList(),
            journal = journalA,
            nowMs = 3_100L
        )

        // Device B has same podcast & episode under different local Room IDs, plus stale pl_old
        val podB = samplePodcast("pod_b9", title = "Systems Weekly", feedUrl = "https://feeds.example.org/systems.xml")
        val epB1 = sampleEpisode("ep_b9", podcastId = "pod_b9", guid = "ep-guid-1")
        val plOldOnB = PlaylistEntity(
            id = "pl_old",
            name = "Old List",
            folderName = "General",
            createdAt = 1_000L
        )

        val mergeOnB = DriveSyncEngine.mergeSnapshots(
            localPodcasts = listOf(podB),
            localEpisodes = listOf(epB1),
            localPlaylists = listOf(plOldOnB),
            localBookmarks = emptyList(),
            localJournal = DriveSyncLocalJournal(lastSyncedAt = 1_500L),
            remoteRawJson = remotePayloadFromA,
            pullOnly = false,
            nowMs = 4_000L
        )

        assertTrue("pl_old deleted at 3000 should be removed from B", "pl_old" in mergeOnB.deletedPlaylistIds)
        assertFalse(mergeOnB.mergedPlaylists.any { it.id == "pl_old" })
        val syncedMorning = mergeOnB.mergedPlaylists.firstOrNull { it.id == "pl_morning" }
        assertNotNull(syncedMorning)
        assertEquals(listOf("ep_b9"), syncedMorning!!.episodeIdList())
        assertEquals(listOf("pod_b9"), syncedMorning.assignedPodcastIdList())
        assertTrue(syncedMorning.isPinned)
        assertTrue(syncedMorning.autoDownload)
    }

    @Test
    fun driveSyncEngine_mergesUpNextQueueOrderAndRemovalTombstones_hidingPlayed() {
        val pod = samplePodcast("pod_1", feedUrl = "https://feeds.example.org/q.xml")
        val ep1 = sampleEpisode("ep1", "pod_1", guid = "g1", inUpNext = true, upNextOrder = 1)
        val ep2 = sampleEpisode("ep2", "pod_1", guid = "g2", inUpNext = false, upNextOrder = 0) // removed on A
        val ep3 = sampleEpisode("ep3", "pod_1", guid = "g3", inUpNext = true, upNextOrder = 0) // moved to top on A

        val ep2Key = DriveSyncEngine.episodeCanonicalKey(ep2, pod.feedUrl)
        val ep1Key = DriveSyncEngine.episodeCanonicalKey(ep1, pod.feedUrl)
        val ep3Key = DriveSyncEngine.episodeCanonicalKey(ep3, pod.feedUrl)

        val remoteFromA = DriveSyncEngine.buildSyncPayload(
            podcasts = listOf(pod),
            episodes = listOf(ep1, ep2, ep3),
            playlists = emptyList(),
            bookmarks = emptyList(),
            journal = DriveSyncLocalJournal(
                upNextUpdatedAt = 5_000L,
                addedToUpNext = mapOf(ep1Key to 1_000L, ep3Key to 1_000L),
                removedFromUpNext = mapOf(ep2Key to 4_500L)
            ),
            nowMs = 5_100L
        )

        // Device B still has ep1 -> ep2 -> ep3 queued from t=1000, and added ep4 (already played -> auto-hidden)
        val ep1B = sampleEpisode("ep1", "pod_1", guid = "g1", inUpNext = true, upNextOrder = 0)
        val ep2B = sampleEpisode("ep2", "pod_1", guid = "g2", inUpNext = true, upNextOrder = 1)
        val ep3B = sampleEpisode("ep3", "pod_1", guid = "g3", inUpNext = true, upNextOrder = 2)
        val ep4B = sampleEpisode("ep4", "pod_1", guid = "g4", inUpNext = true, upNextOrder = 3, isPlayed = true)

        val mergeOnB = DriveSyncEngine.mergeSnapshots(
            localPodcasts = listOf(pod),
            localEpisodes = listOf(ep1B, ep2B, ep3B, ep4B),
            localPlaylists = emptyList(),
            localBookmarks = emptyList(),
            localJournal = DriveSyncLocalJournal(
                upNextUpdatedAt = 2_000L,
                addedToUpNext = mapOf(ep1Key to 1_000L, ep2Key to 1_000L, ep3Key to 1_000L)
            ),
            remoteRawJson = remoteFromA,
            nowMs = 6_000L
        )

        val mergedQueue = mergeOnB.mergedEpisodes
            .filter { it.inUpNext }
            .sortedBy { it.upNextOrder }
            .map { it.id }

        // A's queue order is newer (5000 > 2000), ep2 stays removed by tombstone, ep4 is played so excluded
        assertEquals(listOf("ep3", "ep1"), mergedQueue)
    }

    @Test
    fun driveSyncEngine_mergesPodcastSubscriptionsOverridesAndUnsubscribeTombstones() {
        val podKeepRemote = samplePodcast(
            id = "pod_keep",
            feedUrl = "https://feeds.example.org/keep.xml",
            autoQueueMode = AutoQueueMode.BEGINNING.name,
            useCustomAudio = true,
            customSpeed = 1.75f
        ).copy(notifyNewEpisodes = true, lastSyncedAt = 5_000L)
        val droppedKey = DriveSyncEngine.normalizeFeedKey("https://feeds.example.org/drop.xml", "pod_drop", "Dropped")

        val remoteJson = DriveSyncEngine.buildSyncPayload(
            podcasts = listOf(podKeepRemote),
            episodes = emptyList(),
            playlists = emptyList(),
            bookmarks = emptyList(),
            journal = DriveSyncLocalJournal(
                podcastUpdatedAt = mapOf(
                    DriveSyncEngine.normalizeFeedKey(podKeepRemote.feedUrl) to 5_000L
                ),
                unsubscribedPodcasts = mapOf(droppedKey to 6_000L)
            ),
            nowMs = 6_100L
        )

        val podKeepLocal = samplePodcast(
            id = "pod_keep",
            feedUrl = "https://feeds.example.org/keep.xml",
            autoQueueMode = AutoQueueMode.OFF.name,
            useCustomAudio = false,
            customSpeed = 1.0f
        ).copy(lastSyncedAt = 1_000L)
        val podDropLocal = samplePodcast(
            id = "pod_drop",
            title = "Dropped",
            feedUrl = "https://feeds.example.org/drop.xml"
        ).copy(lastSyncedAt = 1_000L)

        val merge = DriveSyncEngine.mergeSnapshots(
            localPodcasts = listOf(podKeepLocal, podDropLocal),
            localEpisodes = emptyList(),
            localPlaylists = emptyList(),
            localBookmarks = emptyList(),
            localJournal = DriveSyncLocalJournal(
                podcastUpdatedAt = mapOf(
                    DriveSyncEngine.normalizeFeedKey(podKeepLocal.feedUrl) to 1_000L,
                    droppedKey to 1_000L
                )
            ),
            remoteRawJson = remoteJson,
            nowMs = 7_000L
        )

        assertTrue("pod_drop" in merge.deletedPodcastIds)
        assertEquals(1, merge.mergedPodcasts.size)
        val kept = merge.mergedPodcasts.single()
        assertEquals(AutoQueueMode.BEGINNING.name, kept.autoQueueMode)
        assertTrue(kept.useCustomAudio)
        assertEquals(1.75f, kept.customSpeed, 0.001f)
    }

    @Test
    fun driveSyncEngine_mergesEpisodeStatus_progress98PercentUnplayResetStarAndLastPlaying() {
        val pod = samplePodcast("pod_1", feedUrl = "https://feeds.example.org/status.xml")
        val epProgressLocal = sampleEpisode("ep_prog", "pod_1", guid = "prog", resumePositionMs = 200_000L)
        val epNearCompleteLocal = sampleEpisode("ep_98", "pod_1", guid = "ep98", resumePositionMs = 100_000L)
        val epResetLocal = sampleEpisode("ep_reset", "pod_1", guid = "reset", isPlayed = false, playedAt = 0L)
        val epStarLocal = sampleEpisode("ep_star", "pod_1", guid = "star", isStarred = false, isArchived = false)

        val resetKey = DriveSyncEngine.episodeCanonicalKey(epResetLocal, pod.feedUrl)
        val starKey = DriveSyncEngine.episodeCanonicalKey(epStarLocal, pod.feedUrl)
        val progKey = DriveSyncEngine.episodeCanonicalKey(epProgressLocal, pod.feedUrl)

        val epProgressRemote = epProgressLocal.copy(id = "r_prog", resumePositionMs = 650_000L)
        val epNearCompleteRemote = epNearCompleteLocal.copy(id = "r_98", resumePositionMs = 985_000L) // >= 98%
        val epResetRemote = epResetLocal.copy(id = "r_reset", isPlayed = true, playedAt = 3_000L)
        val epStarRemote = epStarLocal.copy(id = "r_star", isStarred = true, isArchived = true)

        val remoteJson = DriveSyncEngine.buildSyncPayload(
            podcasts = listOf(pod),
            episodes = listOf(epProgressRemote, epNearCompleteRemote, epResetRemote, epStarRemote),
            playlists = emptyList(),
            bookmarks = listOf(
                EpisodeBookmarkEntity(id = "bm_1", episodeId = "r_prog", positionMs = 650_000L, label = "Key insight")
            ),
            journal = DriveSyncLocalJournal(
                lastPlayingEpisodeKey = progKey,
                lastPlayingUpdatedAt = 8_000L,
                episodeStatusUpdatedAt = mapOf(
                    resetKey to 3_000L,
                    starKey to 7_000L
                )
            ),
            nowMs = 8_500L
        )

        val localJournal = DriveSyncLocalJournal(
            lastPlayingEpisodeKey = "",
            lastPlayingUpdatedAt = 1_000L,
            episodeStatusUpdatedAt = mapOf(
                resetKey to 6_000L, // local explicitly reset ep_reset AFTER remote marked it played at 3_000
                starKey to 1_000L
            )
        )

        val merge = DriveSyncEngine.mergeSnapshots(
            localPodcasts = listOf(pod),
            localEpisodes = listOf(epProgressLocal, epNearCompleteLocal, epResetLocal, epStarLocal),
            localPlaylists = emptyList(),
            localBookmarks = emptyList(),
            localJournal = localJournal,
            remoteRawJson = remoteJson,
            nowMs = 9_000L
        )

        val byId = merge.mergedEpisodes.associateBy { it.id }
        assertEquals(650_000L, byId["ep_prog"]?.resumePositionMs)
        assertTrue("98.5% completion should auto-mark played", byId["ep_98"]!!.isPlayed)
        assertEquals(0L, byId["ep_98"]!!.resumePositionMs)
        assertFalse("Explicit local unplay reset at 6000 > remote 3000 should stay unplayed", byId["ep_reset"]!!.isPlayed)
        assertTrue(byId["ep_star"]!!.isStarred)
        assertTrue(byId["ep_star"]!!.isArchived)
        assertEquals("ep_prog", merge.resolvedLastPlayingEpisodeId)
        assertEquals("ep_prog", merge.mergedBookmarks.single().episodeId)
    }

    @Test
    fun repository_twoWayDriveSyncAndSafClient_persistsAcrossAllDomains() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LoheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefs = LohePreferencesRepository(context)
        val tempSyncFile = File(context.cacheDir, "test_drive_sync_${System.currentTimeMillis()}.json")

        try {
            prefs.setDriveSyncJournal(DriveSyncLocalJournal())
            val repo = LoheRepository(db.loheDao(), preferencesRepository = prefs)
            val dao = db.loheDao()

            val pod = samplePodcast("pod_repo_1", feedUrl = "https://feeds.example.org/repo1.xml")
            val ep1 = sampleEpisode("ep_r1", "pod_repo_1", guid = "r1", resumePositionMs = 120_000L, inUpNext = true, upNextOrder = 0)
            dao.insertPodcast(pod)
            dao.insertEpisode(ep1)
            repo.createCustomPlaylist("My Drive Playlist", "Favorites", listOf("ep_r1"))

            // Remote snapshot has an extra show + playlist + updated ep1 progress + starred
            val remotePod2 = samplePodcast("pod_remote_2", feedUrl = "https://feeds.example.org/repo2.xml")
            val remoteEp1 = ep1.copy(id = "remote_ep1", resumePositionMs = 450_000L, isStarred = true)
            val remoteEp2 = sampleEpisode("remote_ep2", "pod_remote_2", guid = "r2", inUpNext = true, upNextOrder = 0)
            val remotePayload = DriveSyncEngine.buildSyncPayload(
                podcasts = listOf(pod, remotePod2),
                episodes = listOf(remoteEp1, remoteEp2),
                playlists = listOf(
                    PlaylistEntity(
                        id = "pl_remote_mix",
                        name = "Remote Mix",
                        folderName = "Drive",
                        episodeIdsCsv = "remote_ep2",
                        createdAt = 5_000L
                    )
                ),
                bookmarks = emptyList(),
                journal = DriveSyncLocalJournal(
                    upNextUpdatedAt = 9_000L,
                    episodeStatusUpdatedAt = mapOf(
                        DriveSyncEngine.episodeCanonicalKey(remoteEp1, pod.feedUrl) to 9_000L
                    )
                ),
                nowMs = 9_500L
            )

            // Write remote snapshot to a file URI and test SafDriveSyncClient read -> merge -> write
            val safClient = SafDriveSyncClient(context)
            val fileUri = Uri.fromFile(tempSyncFile)
            assertTrue(safClient.writeSyncFile(fileUri, remotePayload).isSuccess)
            val readBack = safClient.readSyncFile(fileUri).getOrThrow()
            assertNotNull(readBack)

            val mergeRes = repo.syncWithDriveRemotePayload(readBack, pullOnly = false)
            assertTrue(safClient.writeSyncFile(fileUri, mergeRes.canonicalJsonPayload).isSuccess)

            val localPodsAfter = dao.getAllPodcasts().first()
            val localEpsAfter = dao.getAllEpisodes().first().associateBy { it.id }
            val localPlsAfter = dao.getCustomPlaylists().first()

            assertEquals(2, localPodsAfter.size)
            assertEquals(450_000L, localEpsAfter["ep_r1"]?.resumePositionMs)
            assertTrue(localEpsAfter["ep_r1"]?.isStarred == true)
            assertTrue(localEpsAfter["remote_ep2"]?.inUpNext == true)
            assertTrue(localPlsAfter.any { it.name == "My Drive Playlist" })
            assertTrue(localPlsAfter.any { it.id == "pl_remote_mix" })

            // Verify persisted journal reflects sync timestamp
            assertTrue(prefs.getDriveSyncJournal().lastSyncedAt > 0L)
        } finally {
            tempSyncFile.delete()
            db.close()
            prefs.setDriveSyncJournal(DriveSyncLocalJournal())
            prefs.clearSafSyncFile()
            prefs.setLastPlayingEpisodeId("")
        }
    }

    @Test
    fun cloudSyncSettingsUi_showsDriveOnlyDomainsAndSafProviderControls() {
        val currentProvider = androidx.compose.runtime.mutableStateOf(CloudSyncProvider.GOOGLE_DRIVE)
        val safUri = androidx.compose.runtime.mutableStateOf("")

        composeTestRule.setContent {
            LoheTheme {
                SettingsContainerScreen(
                    currentSubScreen = SettingsSubScreen.CLOUD_SYNC,
                    settings = LoheUserSettings(
                        isProUnlocked = true,
                        cloudSyncProvider = currentProvider.value,
                        safSyncUri = safUri.value,
                        safSyncProviderName = currentProvider.value.name,
                        safSyncDisplayName = "lohe_cloud_sync.json"
                    ),
                    billingState = LoheBillingState(),
                    isCloudSyncBusy = false,
                    downloadedEpisodesCount = 0,
                    onNavigateSubScreen = {},
                    onBack = {},
                    onThemeChange = {},
                    onReducedMotionChange = {},
                    onUpNextIndicatorEnabledChange = {},
                    onIndicatorStyleChange = {},
                    onIndicatorSizeChange = {},
                    onPresetColorChange = {},
                    onCustomHexColorChange = { _, _ -> },
                    onSpeedChange = {},
                    onSkipDurationsChange = { _, _ -> },
                    onSkipSilenceChange = {},
                    onVolumeBoostChange = {},
                    onContinuousPlaybackChange = {},
                    onPlaybackExtraOptionsChange = { _, _, _ -> },
                    onFloatingPlayerChange = {},
                    onFloatingAppearanceChange = { _, _ -> },
                    onAutoArchivePolicyChange = {},
                    onKeepStarredChange = {},
                    onEpisodeLimitChange = {},
                    onArchivePlayedNow = {},
                    onAutoDownloadChange = { _, _, _ -> },
                    onClearDownloads = {},
                    onRefreshFeedsNow = {},
                    onImportOpmlOrJson = {},
                    onExportOpml = {},
                    onExportJsonBackup = {},
                    onSyncToGoogleDrive = {},
                    onRestoreFromGoogleDrive = {},
                    onCloudProviderChange = { currentProvider.value = it },
                    onToggleCloudAutoBackup = {},
                    onOpenProUpgradeModal = {},
                    onRestoreProPurchases = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("cloud_sync_domains_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Playlists").assertIsDisplayed()
        composeTestRule.onNodeWithText("Up Next").assertIsDisplayed()
        composeTestRule.onNodeWithText("Podcasts").assertIsDisplayed()
        composeTestRule.onNodeWithText("Episode Status").assertIsDisplayed()

        // Switch to OneDrive -> auto switch + link/create file controls appear
        currentProvider.value = CloudSyncProvider.ONEDRIVE
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cloud_sync_settings_list")
            .performScrollToNode(hasTestTag("cloud_sync_auto_switch"))
        composeTestRule.onNodeWithTag("cloud_sync_auto_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_settings_list")
            .performScrollToNode(hasTestTag("cloud_sync_link_file_button"))
        composeTestRule.onNodeWithTag("cloud_sync_link_file_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_create_file_button").assertIsDisplayed()

        // Link a SAF URI -> unlink button appears
        safUri.value = "content://com.microsoft.skydrive/lohe_cloud_sync.json"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cloud_sync_settings_list")
            .performScrollToNode(hasTestTag("cloud_sync_unlink_file_button"))
        composeTestRule.onNodeWithTag("cloud_sync_unlink_file_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_settings_list")
            .performScrollToNode(hasTestTag("cloud_sync_upload_button"))
        composeTestRule.onNodeWithTag("cloud_sync_upload_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cloud_sync_restore_button").assertIsDisplayed()
    }
}
