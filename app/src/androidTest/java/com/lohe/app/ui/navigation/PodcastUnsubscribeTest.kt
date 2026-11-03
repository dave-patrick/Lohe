package com.lohe.app.ui.navigation

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lohe.app.MainActivity
import com.lohe.app.data.EpisodesViewModel
import com.lohe.app.data.QueueViewModel
import com.lohe.app.data.SubscriptionRepository
import com.lohe.app.data.db.AppDatabase
import com.lohe.app.data.db.dao.PodcastDao
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.fetch.PodcastFetcher
import com.lohe.app.ui.theme.LoheTheme
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastUnsubscribeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var db: AppDatabase
    private lateinit var model: EpisodesViewModel
    private val store = ViewModelStore()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val backCalls = AtomicInteger()
    private val deleteCalls = AtomicInteger()
    private val messages = Collections.synchronizedList(mutableListOf<String>())
    @Volatile private var failDelete = false
    private var deletionGate: CompletableDeferred<Unit>? = null

    @Before fun setUp(): Unit = runBlocking {
        check(instrumentation.targetContext.packageName.endsWith(".audit"))
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        db.podcastDao().upsert(PodcastEntity("selected", "Selected podcast", feedUrl = "https://example.invalid/selected"))
        db.podcastDao().upsert(PodcastEntity("other", "Other podcast", feedUrl = "https://example.invalid/other"))
        db.episodeDao().upsertAll(listOf(
            EpisodeEntity("selected-episode", "selected", "Selected episode", "https://example.invalid/audio", 1,
                playingStatus = EpisodeEntity.IN_PROGRESS, listenedSeconds = 120, playedUpToSeconds = 120),
            EpisodeEntity("other-episode", "other", "Other episode", "https://example.invalid/other-audio", 1),
        ))
        val podcasts = object : PodcastDao by db.podcastDao() {
            override suspend fun deleteByUuid(uuid: String) {
                deleteCalls.incrementAndGet()
                if (failDelete) throw IllegalStateException("Injected deletion failure")
                deletionGate?.await()
                db.podcastDao().deleteByUuid(uuid)
            }
        }
        val fetcher = object : PodcastFetcher {
            override suspend fun fetch(feedUrl: String): PodcastEntity = error("No network expected")
            override suspend fun fetchEpisodes(feedUrl: String): List<EpisodeEntity> = error("No network expected")
            override suspend fun importFeed(feedUrl: String): Result<PodcastEntity> = error("No network expected")
        }
        val repository = SubscriptionRepository(podcasts, db.episodeDao(), db.listeningHistoryDao(), fetcher)
        model = EpisodesViewModel(repository, "selected")
        val queue = QueueViewModel(repository)
        store.put("episodes", model)
        store.put("queue", queue)
        scenario = ActivityScenario.launch(MainActivity::class.java).also { active ->
            active.onActivity { activity -> activity.setContent {
                LoheTheme { SwipeActionsHost {
                    EpisodesScreen(model, queue, { backCalls.incrementAndGet() }, {}, {}, {}, {},
                        onShowMessage = { messages += it })
                } }
            } }
        }
        waitFor("Unsubscribe")
    }

    @After fun tearDown() {
        scenario?.close()
        instrumentation.runOnMainSync { store.clear() }
        if (::db.isInitialized) db.close()
    }

    @Test fun cancelLeavesPodcastAndEpisodesUntouched() {
        tap("Unsubscribe")
        waitFor("Unsubscribe from \"Selected podcast\"?")
        tap("Cancel")
        waitFor("Unsubscribe")
        runBlocking {
            assertNotNull(db.podcastDao().get("selected"))
            assertNotNull(db.episodeDao().get("selected-episode"))
            assertTrue(db.listeningHistoryDao().allOnce().isEmpty())
        }
        assertEquals(0, deleteCalls.get())
        assertEquals(0, backCalls.get())
    }

    @Test fun confirmRemovesOnlySelectedPodcastAndRetainsListeningTime() {
        deletionGate = CompletableDeferred()
        tap("Unsubscribe")
        waitFor("Unsubscribe from \"Selected podcast\"?")
        tap("Unsubscribe")
        waitFor("Unsubscribing…")
        instrumentation.runOnMainSync { model.unsubscribe() }
        assertEquals(1, deleteCalls.get())
        deletionGate!!.complete(Unit)
        await { backCalls.get() == 1 }
        runBlocking {
            assertNull(db.podcastDao().get("selected"))
            assertNull(db.episodeDao().get("selected-episode"))
            assertEquals(120L, db.listeningHistoryDao().get("selected-episode")!!.listenedSeconds)
            assertNotNull(db.podcastDao().get("other"))
            assertNotNull(db.episodeDao().get("other-episode"))
        }
        assertTrue(messages.contains("Unsubscribed from Selected podcast"))
        instrumentation.runOnMainSync { model.unsubscribe() }
        assertEquals(1, deleteCalls.get())
        assertEquals(1, backCalls.get())
    }

    @Test fun failureStaysOnPageAndAllowsRetry() {
        failDelete = true
        tap("Unsubscribe")
        waitFor("Unsubscribe from \"Selected podcast\"?")
        tap("Unsubscribe")
        await { messages.contains("Couldn't unsubscribe. Please try again.") }
        waitFor("Unsubscribe")
        runBlocking { assertNotNull(db.podcastDao().get("selected")) }
        assertEquals(0, backCalls.get())
        failDelete = false
        tap("Unsubscribe")
        waitFor("Unsubscribe from \"Selected podcast\"?")
        tap("Unsubscribe")
        await { backCalls.get() == 1 }
        runBlocking { assertNull(db.podcastDao().get("selected")) }
        assertEquals(2, deleteCalls.get())
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
        throw AssertionError("Timed out waiting for unsubscribe state")
    }
    private fun waitFor(text: String): AccessibilityNodeInfo {
        await { find(text) != null }
        return find(text)!!
    }
    private fun tap(text: String) {
        val bounds = Rect().also(waitFor(text)::getBoundsInScreen)
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
    }
}
