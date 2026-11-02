package com.lohe.app.ui.navigation

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lohe.app.MainActivity
import com.lohe.app.data.QueueViewModel
import com.lohe.app.data.SubscriptionsViewModel
import com.lohe.app.data.SubscriptionRepository
import com.lohe.app.data.db.AppDatabase
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
class PodcastSwipeActionsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var db: AppDatabase
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var subscriptions: SubscriptionsViewModel

    @Before fun setup() {
        assertTrue("Use the isolated audit package", instrumentation.targetContext.packageName.endsWith(".audit"))
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        runBlocking {
            db.podcastDao().upsert(PodcastEntity("show", "Swipe fixture", feedUrl = "https://example.invalid/feed"))
        }
        val repository = SubscriptionRepository(db.podcastDao(), db.episodeDao(), db.listeningHistoryDao(),
            RssPodcastFetcher(podcastDao = db.podcastDao(), episodeDao = db.episodeDao()))
        subscriptions = SubscriptionsViewModel(repository)
        val queue = QueueViewModel(repository)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                LoheTheme {
                    SwipeActionsHost {
                        PodcastsScreen(subscriptions, queue, {}, {}, onManageStorage = {},
                            onPickOpmlToImport = {}, onPickExportDestination = {})
                    }
                }
            }
        }
        waitForText("Swipe fixture")
    }

    @After fun teardown() {
        if (::scenario.isInitialized) scenario.close()
        if (::db.isInitialized) db.close()
    }

    @Test fun settledListAndGridDoNotExposeCancel() {
        assertCancelAbsent()
        tap(bounds("Switch to grid view"))
        waitForText("Switch to list view")
        assertCancelAbsent()
    }

    @Test fun settledSwipeWrapperDrawsNoActionBackground() {
        scenario.onActivity { activity ->
            activity.setContent {
                LoheTheme {
                    val scope = rememberCoroutineScope()
                    Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                        SwipeActionsHost {
                            SwipeablePodcastCard(
                                podcast = PodcastEntity("show", "Swipe fixture", feedUrl = "https://example.invalid/feed"),
                                folderName = "", inSelectionMode = false, isSelected = false,
                                onOpen = {}, onAssignFolder = {}, onUnsubscribe = {},
                                onShowSettings = {}, onQueue = {}, onTogglePlayed = {},
                                onAddToPlaylist = {}, scope = scope, viewModel = subscriptions,
                                onShowMessage = {},
                            ) {
                                Box(Modifier.fillMaxWidth().height(180.dp)
                                    .semantics { contentDescription = "Transparent swipe foreground" })
                            }
                        }
                    }
                }
            }
        }
        val foreground = bounds("Transparent swipe foreground")
        SystemClock.sleep(500)
        instrumentation.waitForIdleSync()
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            // Keep the sample away from Cancel and the rounded tray corners.
            val pixel = screenshot.getPixel(foreground.centerX(), foreground.top + foreground.height() / 3)
            assertEquals("A settled swipe wrapper must leave the canvas untouched", android.graphics.Color.MAGENTA, pixel)
        } finally {
            screenshot.recycle()
        }
    }

    @Test fun swipeRevealsCancelAndCancelHidesTray() {
        assertCancelAbsent()
        val title = bounds("Swipe fixture")
        val display = instrumentation.targetContext.resources.displayMetrics
        drag(display.widthPixels / 3, title.centerY(), display.widthPixels - 25, title.centerY())
        tap(bounds("Cancel"))
        assertCancelAbsent()
        assertNotNull(find("Swipe fixture"))
    }

    private fun assertCancelAbsent() {
        // Wait beyond the swipe animation so reset must actually remove the tray.
        SystemClock.sleep(500)
        instrumentation.waitForIdleSync()
        assertNull("Settled cards must not render their swipe action background", find("Cancel"))
    }

    private fun tap(rect: Rect) {
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, rect.centerX().toFloat(), rect.centerY().toFloat(), 0)
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
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
