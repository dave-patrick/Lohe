package com.lohe.app.ui.navigation

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lohe.app.MainActivity
import com.lohe.app.data.EpisodesViewModel
import com.lohe.app.data.QueueViewModel
import com.lohe.app.data.SubscriptionRepository
import com.lohe.app.data.db.AppDatabase
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.fetch.PodcastFetcher
import com.lohe.app.ui.theme.LoheTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real detail screen and refresh orchestration without network access. */
@RunWith(AndroidJUnit4::class)
class PodcastAutoQueueTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var db: AppDatabase
    private lateinit var repository: SubscriptionRepository
    private var scenario: ActivityScenario<MainActivity>? = null
    private val incoming = mutableMapOf<String, List<EpisodeEntity>>()
    private val toggle = "Automatically add to Up Next"

    @Before fun setup() = runBlocking {
        assertTrue("Use the isolated audit application", instrumentation.targetContext.packageName.endsWith(".audit"))
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        db.podcastDao().upsert(show("show"))
        db.podcastDao().upsert(show("other"))
        db.episodeDao().upsertAll(listOf(episode("old"), episode("queued", "other").copy(queuePosition = 0)))
        val fetcher = object : PodcastFetcher {
            override suspend fun fetch(feedUrl: String) = db.podcastDao().byFeedUrl(feedUrl)!!
            override suspend fun fetchEpisodes(feedUrl: String) = incoming[fetch(feedUrl).uuid].orEmpty()
            override suspend fun importFeed(feedUrl: String): Result<PodcastEntity> = runCatching {
                val podcast = fetch(feedUrl)
                // Write only new rows, as the real importer preserves listener state.
                db.episodeDao().upsertAll(fetchEpisodes(feedUrl).filter { db.episodeDao().get(it.uuid) == null })
                podcast
            }
        }
        repository = SubscriptionRepository(db.podcastDao(), db.episodeDao(), db.listeningHistoryDao(), fetcher,
            transaction = { block -> db.withTransaction { block() } })
    }

    @After fun teardown() {
        scenario?.close()
        if (::db.isInitialized) db.close()
    }

    @Test fun toggleBackfillsOnlyEligibleSelectedPodcastAndDisablePreservesQueue() {
        runBlocking {
            db.episodeDao().upsertAll(listOf(episode("played").copy(playingStatus = EpisodeEntity.PLAYED),
                episode("archived").copy(isArchived = true), episode("deleted").copy(isDeleted = true),
                episode("other-old", "other")))
        }
        openScreen()
        tap(toggle)
        await { runBlocking { db.podcastDao().get("show")!!.autoAddNewEpisodesToUpNext } }
        await { runBlocking { queueIds() == listOf("queued", "old") } }
        runBlocking {
            assertFalse(db.podcastDao().get("other")!!.autoAddNewEpisodesToUpNext)
            assertEquals(listOf("queued", "old"), queueIds())
        }
        waitFor("Beginning")
        tap(toggle)
        await { runBlocking { !db.podcastDao().get("show")!!.autoAddNewEpisodesToUpNext } }
        waitFor("Beginning")
        waitFor("End")
        runBlocking { assertEquals(listOf("queued", "old"), queueIds()) }
    }

    @Test fun beginningAndEndPersistWhenScreenAndViewModelAreRecreated() {
        openScreen()
        tap("Beginning")
        await { runBlocking { db.podcastDao().get("show")!!.autoAddNewEpisodesAtTop } }
        tap(toggle)
        await { runBlocking { queueIds() == listOf("old", "queued") } }
        openScreen()
        waitFor("Beginning")
        runBlocking { assertTrue(db.podcastDao().get("show")!!.autoAddNewEpisodesAtTop) }
        tap("End")
        await { runBlocking { !db.podcastDao().get("show")!!.autoAddNewEpisodesAtTop } }
        openScreen()
        waitFor("End")
        runBlocking {
            assertTrue(db.podcastDao().get("show")!!.autoAddNewEpisodesToUpNext)
            assertFalse(db.podcastDao().get("show")!!.autoAddNewEpisodesAtTop)
        }
    }

    @Test fun selectionModeHidesAutomaticQueueControls() {
        openScreen()
        val bounds = Rect().also(waitFor("Fixture old")::getBoundsInScreen)
        val down = SystemClock.uptimeMillis()
        val press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN,
            bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
        instrumentation.uiAutomation.injectInputEvent(press, true)
        press.recycle()
        SystemClock.sleep(650)
        val release = MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
            bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
        instrumentation.uiAutomation.injectInputEvent(release, true)
        release.recycle()
        waitFor("1 selected")
        await { find(toggle) == null }
        assertNull("Unsubscribe must be hidden while selecting episodes", find("Unsubscribe"))
        runBlocking { assertFalse(db.podcastDao().get("show")!!.autoAddNewEpisodesToUpNext) }
    }

    @Test fun existingEpisodesAppendNewestFirstAndRepeatedEnableKeepsQueuePositions() = runBlocking {
        db.episodeDao().upsertAll(listOf(episode("newest", published = 30), episode("older", published = 20),
            episode("already-queued").copy(queuePosition = 5)))
        repository.setPodcastAutoQueueWithExisting("show", true)
        assertEquals(listOf("queued", "already-queued", "newest", "older", "old"), queueIds())
        val positions = repository.queueSnapshot().associate { it.uuid to it.queuePosition }
        repository.setPodcastAutoQueueWithExisting("show", true)
        assertEquals(positions, repository.queueSnapshot().associate { it.uuid to it.queuePosition })
        repository.setPodcastAutoQueueWithExisting("show", false)
        assertEquals(positions, repository.queueSnapshot().associate { it.uuid to it.queuePosition })
    }

    @Test fun existingEpisodesPrependAsBatchWithoutMovingAlreadyQueuedEpisodes() = runBlocking {
        db.episodeDao().upsertAll(listOf(episode("newest", published = 30), episode("older", published = 20),
            episode("already-queued").copy(queuePosition = 5)))
        repository.setAutoAddNewEpisodesAtTop("show", true)
        repository.setPodcastAutoQueueWithExisting("show", true)
        assertEquals(listOf("newest", "older", "old", "queued", "already-queued"), queueIds())
        assertEquals(queueIds().size, queueIds().toSet().size)
    }

    @Test fun autoQueueSettersPreserveOtherSettingsAndFeedMetadata() = runBlocking {
        val original = db.podcastDao().get("show")!!.copy(author = "Author", description = "Description", autoDownload = true)
        db.podcastDao().update(original)
        repository.setAutoAddNewEpisodesAtTop("show", true)
        repository.setAutoAddNewEpisodesToUpNext("show", true)
        assertEquals(original.copy(autoAddNewEpisodesAtTop = true, autoAddNewEpisodesToUpNext = true),
            db.podcastDao().get("show"))
        repository.setAutoAddNewEpisodesAtTop("show", false)
        assertEquals(original.copy(autoAddNewEpisodesToUpNext = true), db.podcastDao().get("show"))
    }

    @Test fun cancellationRollsBackEnableAndPartialBackfillSoRetryAddsEveryEpisode() = runBlocking {
        db.episodeDao().upsertAll(listOf(episode("newest", published = 30), episode("older", published = 20)))
        repository.setAutoAddNewEpisodesAtTop("show", true)
        val before = repository.queueSnapshot()
        var failOnce = true
        val interruptedEpisodes = object : com.lohe.app.data.db.dao.EpisodeDao by db.episodeDao() {
            override suspend fun enqueue(uuid: String) {
                db.episodeDao().enqueue(uuid)
                if (uuid == "older" && failOnce) {
                    failOnce = false
                    throw CancellationException("Interrupted existing episode backfill")
                }
            }
        }
        val unusedFetcher = object : PodcastFetcher {
            override suspend fun fetch(feedUrl: String) = error("No feed fetch expected")
            override suspend fun fetchEpisodes(feedUrl: String) = error("No feed fetch expected")
            override suspend fun importFeed(feedUrl: String): Result<PodcastEntity> = error("No feed fetch expected")
        }
        val interruptedRepository = SubscriptionRepository(db.podcastDao(), interruptedEpisodes,
            db.listeningHistoryDao(), unusedFetcher, transaction = { block -> db.withTransaction { block() } })
        try {
            interruptedRepository.setPodcastAutoQueueWithExisting("show", true)
            fail("Expected backfill cancellation")
        } catch (_: CancellationException) {
            // The flag and every queue mutation belong to the same transaction.
        }
        assertFalse(db.podcastDao().get("show")!!.autoAddNewEpisodesToUpNext)
        assertEquals(before, repository.queueSnapshot())
        interruptedRepository.setPodcastAutoQueueWithExisting("show", true)
        assertTrue(db.podcastDao().get("show")!!.autoAddNewEpisodesToUpNext)
        assertEquals(listOf("newest", "older", "old", "queued"), queueIds())
    }

    @Test fun refreshQueuesOnlyNewEligibleEpisodesNewestFirstAtEndWithoutDuplicates() = runBlocking {
        repository.setAutoAddNewEpisodesToUpNext("show", true)
        incoming["show"] = listOf(episode("older", published = 20), episode("newest", published = 30),
            episode("played").copy(playingStatus = EpisodeEntity.PLAYED),
            episode("deleted").copy(isDeleted = true), episode("archived").copy(isArchived = true), episode("old"))
        repository.refreshAllFeeds()
        assertEquals(listOf("queued", "newest", "older"), queueIds())
        repository.refreshAllFeeds()
        assertEquals(listOf("queued", "newest", "older"), queueIds())
    }

    @Test fun refreshAtBeginningPreservesExistingQueueAndDisabledShowQueuesNothing() = runBlocking {
        repository.setAutoAddNewEpisodesToUpNext("show", true)
        repository.setAutoAddNewEpisodesAtTop("show", true)
        incoming["show"] = listOf(episode("older", published = 20), episode("newest", published = 30))
        incoming["other"] = listOf(episode("disabled-new", "other"))
        repository.refreshAllFeeds()
        assertEquals(listOf("newest", "older", "queued"), queueIds())
        repository.setAutoAddNewEpisodesToUpNext("show", false)
        incoming["show"] = listOf(episode("after-disable", published = 40))
        repository.refreshAllFeeds()
        assertEquals(listOf("newest", "older", "queued"), queueIds())
    }

    @Test fun firstFetchOfEmptyShowDoesNotQueueBackCatalogueButNextDiscoveryDoes() = runBlocking {
        repository.setAutoAddNewEpisodesToUpNext("other", true)
        // A separate empty subscription avoids treating an existing queued row as first fetch.
        db.podcastDao().upsert(show("empty").copy(autoAddNewEpisodesToUpNext = true))
        incoming["empty"] = listOf(episode("catalogue", "empty"))
        repository.refreshAllFeeds()
        assertEquals(listOf("queued"), queueIds())
        incoming["empty"] = listOf(episode("next", "empty", 40))
        repository.refreshAllFeeds()
        assertEquals(listOf("queued", "next"), queueIds())
    }

    private fun show(id: String) = PodcastEntity(id, "Auto queue $id", feedUrl = "https://example.invalid/$id")
    private fun episode(id: String, show: String = "show", published: Long = 1) =
        EpisodeEntity(id, show, "Fixture $id", "https://example.invalid/audio/$id", published)
    private suspend fun queueIds() = repository.queueSnapshot().map { it.uuid }

    private fun openScreen() {
        scenario?.close()
        val episodes = EpisodesViewModel(repository, "show")
        val queue = QueueViewModel(repository)
        scenario = ActivityScenario.launch(MainActivity::class.java).also { active ->
            active.onActivity { activity -> activity.setContent {
                LoheTheme { SwipeActionsHost { EpisodesScreen(episodes, queue, {}, {}, {}, {}, {}, {}, {}) } }
            } }
        }
        waitFor(toggle)
    }

    private fun find(text: String): AccessibilityNodeInfo? {
        fun search(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
            for (i in 0 until node.childCount) search(node.getChild(i))?.let { return it }
            return null
        }
        return search(instrumentation.uiAutomation.rootInActiveWindow)
    }
    private fun await(predicate: () -> Boolean) {
        repeat(100) {
            instrumentation.waitForIdleSync()
            if (predicate()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for auto queue state")
    }
    private fun waitFor(text: String): AccessibilityNodeInfo {
        await { find(text) != null }
        return find(text)!!
    }
    private fun tap(text: String) {
        val bounds = Rect().also(waitFor(text)::getBoundsInScreen)
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
    }
}
