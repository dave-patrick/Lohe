package com.lohe.app.ui.navigation

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
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
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.fetch.RssPodcastFetcher
import com.lohe.app.ui.theme.LoheTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpisodeScrollingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var db: AppDatabase
    private lateinit var scenario: ActivityScenario<MainActivity>
    @Volatile private var queued = 0
    @Volatile private var played = 0

    @Before fun setup() {
        // Never run this UI fixture against the user's installed library.
        assertTrue(instrumentation.targetContext.packageName.endsWith(".audit"))
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        runBlocking {
            db.podcastDao().upsert(PodcastEntity("show", "Scrolling fixture", feedUrl = "https://example.invalid/feed"))
            db.episodeDao().upsertAll((1..30).map {
                EpisodeEntity("episode-$it", "show", "Fixture episode %02d".format(it), "https://example.invalid/$it", 31L - it)
            })
        }
        val repository = SubscriptionRepository(db.podcastDao(), db.episodeDao(), db.listeningHistoryDao(),
            RssPodcastFetcher(podcastDao = db.podcastDao(), episodeDao = db.episodeDao()))
        val episodes = EpisodesViewModel(repository, "show")
        val queue = QueueViewModel(repository)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                LoheTheme {
                    SwipeActionsHost {
                        EpisodesScreen(episodes, queue, {}, { played++ }, { queued++ }, {}, {}, {}, {})
                    }
                }
            }
        }
        waitForText("Fixture episode 01")
    }

    @After fun teardown() {
        if (::scenario.isInitialized) scenario.close()
        if (::db.isInitialized) db.close()
    }

    @Test fun verticalDragOnEpisodeScrollsWithoutOpeningNotes() {
        val before = bounds("Fixture episode 01")
        drag(before.centerX(), before.centerY(), before.centerX(), before.centerY() - 300)
        var after: Rect? = before
        var otherEpisodeVisible = false
        var scrolled = false
        for (attempt in 1..50) {
            instrumentation.waitForIdleSync()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            after = find("Fixture episode 01", root)?.let { Rect().also(it::getBoundsInScreen) }
            otherEpisodeVisible = (2..30).any { index ->
                find("Fixture episode %02d".format(index), root)?.let {
                    it.isVisibleToUser && !Rect().also(it::getBoundsInScreen).isEmpty
                } == true
            }
            scrolled = if (after == null) otherEpisodeVisible else after!!.top < before.top - 30
            if (scrolled) break
            SystemClock.sleep(100)
        }
        assertTrue("Dragging an episode upward should move the list: before=$before after=$after otherVisible=$otherEpisodeVisible", scrolled)
        assertNull("Scrolling must not open episode notes", find("SHOW NOTES"))
        assertEquals(0, played)
    }

    @Test fun horizontalDragRevealsActionWithoutExecutingIt() {
        val row = bounds("Fixture episode 01")
        drag(row.centerX(), row.centerY(), row.centerX() + 350, row.centerY())
        waitForText("Add to Up Next")
        assertEquals(0, queued)
        assertEquals(0, played)
    }

    @Test fun infoButtonOpensNotes() {
        val info = bounds("Show notes")
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, info.centerX().toFloat(), info.centerY().toFloat(), 0)
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        waitForText("SHOW NOTES")
        assertEquals(0, played)
    }

    private fun find(text: String, root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        fun search(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
            for (index in 0 until node.childCount) search(node.getChild(index))?.let { return it }
            return null
        }
        return search(root)
    }

    private fun bounds(text: String): Rect = Rect().also { waitForText(text).getBoundsInScreen(it) }

    private fun waitForText(text: String): AccessibilityNodeInfo {
        repeat(100) {
            instrumentation.waitForIdleSync()
            find(text)?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("Could not find $text")
    }

    private fun drag(startX: Int, startY: Int, endX: Int, endY: Int) {
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, startX.toFloat(), startY.toFloat())
        for (step in 1..20) {
            SystemClock.sleep(12)
            val fraction = step / 20f
            send(MotionEvent.ACTION_MOVE, startX + (endX - startX) * fraction, startY + (endY - startY) * fraction)
        }
        send(MotionEvent.ACTION_UP, endX.toFloat(), endY.toFloat())
    }
}










