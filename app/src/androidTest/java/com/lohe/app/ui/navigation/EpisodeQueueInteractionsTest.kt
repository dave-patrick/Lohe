package com.lohe.app.ui.navigation

import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lohe.app.MainActivity
import com.lohe.app.data.EpisodesViewModel
import com.lohe.app.data.PlaylistRepository
import com.lohe.app.data.QueueViewModel
import com.lohe.app.data.SubscriptionRepository
import com.lohe.app.data.db.AppDatabase
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.fetch.RssPodcastFetcher
import com.lohe.app.ui.theme.LoheTheme
import com.lohe.app.ui.theme.QueueIndicatorSettings
import com.lohe.app.ui.theme.QueueIndicatorStyle
import com.lohe.app.ui.theme.QueueIndicatorColor
import com.lohe.app.ui.theme.QueueIndicatorSize
import com.lohe.app.ui.theme.SettingsStore
import com.lohe.app.ui.theme.Appearance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real touch interactions, with every episode and queue mutation isolated in memory. */
@RunWith(AndroidJUnit4::class)
class EpisodeQueueInteractionsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var db: AppDatabase
    private lateinit var repository: SubscriptionRepository
    private lateinit var queue: QueueViewModel
    private lateinit var playlists: PlaylistRepository
    private lateinit var scenario: ActivityScenario<MainActivity>
    @Volatile private var played = 0
    private val indicator = mutableStateOf(QueueIndicatorSettings())

    @Before fun setup() {
        assertTrue("Only run against the isolated audit app", instrumentation.targetContext.packageName.endsWith(".audit"))
        assertFalse("Unlock the phone before running touch interaction tests",
            instrumentation.targetContext.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        runBlocking {
            db.podcastDao().upsert(PodcastEntity("show", "Interaction fixture show", feedUrl = "https://example.invalid/show"))
            db.episodeDao().upsertAll((1..3).map { index ->
                EpisodeEntity("episode-$index", "show", "Interaction episode $index", "https://example.invalid/$index",
                    (4 - index).toLong(), description = "Interaction fixture notes $index", queuePosition = index - 1,
                    downloadState = 2, localPath = "/fixture-$index.audio")
            } + EpisodeEntity("unqueued", "show", "Unqueued fixture", "https://example.invalid/unqueued", 0))
        }
        repository = SubscriptionRepository(db.podcastDao(), db.episodeDao(), db.listeningHistoryDao(),
            RssPodcastFetcher(podcastDao = db.podcastDao(), episodeDao = db.episodeDao()))
        playlists = PlaylistRepository()
        queue = QueueViewModel(repository, playlists)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @After fun teardown() {
        if (::scenario.isInitialized) scenario.close()
        if (::db.isInitialized) db.close()
    }

    private fun showEpisodes() {
        val episodes = EpisodesViewModel(repository, "show")
        scenario.onActivity { activity -> activity.setContent {
            LoheTheme { Box(Modifier.safeDrawingPadding()) { SwipeActionsHost {
                EpisodesScreen(episodes, queue, onBack = {}, onPlay = { played++ }, onQueue = {},
                    onDownload = {}, onRemoveDownloads = {}, onShowMessage = {}, queueIndicator = indicator.value)
            } } }
        } }
        waitForEpisode("Interaction episode 1")
    }

    private fun showQueue() {
        scenario.onActivity { activity -> activity.setContent {
            LoheTheme { Box(Modifier.safeDrawingPadding()) { SwipeActionsHost {
                QueueScreen(queue, onPlay = { played++ }, queueIndicator = indicator.value)
            } } }
        } }
        waitFor("Interaction episode 2")
    }

    @Test fun podcastNotesArchiveEpisodeWithoutPlaying() {
        showEpisodes()
        tap("Interaction episode 1")
        tap("Archive")
        waitForCondition("notes archive persisted") { runBlocking { db.episodeDao().get("episode-1")!!.isArchived } }
        val archived = runBlocking { db.episodeDao().get("episode-1")!! }
        assertTrue(archived.isPlayed)
        assertNull(archived.queuePosition)
        assertNull(find("SHOW NOTES"))
        assertEquals(0, played)
    }

    @Test fun upNextNotesArchiveEpisodeAndDismissLeavesOthersUnchanged() {
        showQueue()
        tap("Interaction episode 2")
        waitFor("SHOW NOTES")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitForCondition("notes dismissed") { find("SHOW NOTES") == null }
        assertEquals(listOf("episode-1", "episode-2", "episode-3"), queuedIds())
        assertFalse(runBlocking { db.episodeDao().get("episode-2")!!.isArchived })
        tap("Interaction episode 2")
        tap("Archive")
        waitForCondition("archived episode leaves queue") { queuedIds() == listOf("episode-1", "episode-3") }
        val archived = runBlocking { db.episodeDao().get("episode-2")!! }
        assertTrue(archived.isArchived)
        assertTrue(archived.isPlayed)
        assertEquals(0, played)
    }

    @Test fun playlistNotesArchiveEpisode() {
        val playlist = playlists.createPlaylist("Archive fixture playlist", listOf("episode-1", "episode-2"), autoDownload = false)
        try {
            scenario.onActivity { activity -> activity.setContent {
                LoheTheme { Box(Modifier.safeDrawingPadding()) { SwipeActionsHost {
                    PlaylistsScreen(queue, onPlay = { played++ })
                } } }
            } }
            waitForEpisode("Archive fixture playlist")
            tap("Archive fixture playlist")
            tap("Interaction episode 2")
            tap("Archive")
            waitForCondition("playlist episode archived") { runBlocking { db.episodeDao().get("episode-2")!!.isArchived } }
            assertFalse(queuedIds().contains("episode-2"))
            assertEquals(0, played)
        } finally {
            playlists.deletePlaylist(playlist.id)
        }
    }

    @Test fun queueIndicatorsFollowMembershipAndVisibilityWithoutPlaying() {
        showQueue()
        val original = queuedIds()
        waitForCondition("three queued indicators") { indicatorCount() == 3 }
        scenario.onActivity { indicator.value = indicator.value.copy(enabled = false) }
        waitForCondition("indicators hidden") { indicatorCount() == 0 }
        assertEquals(original, queuedIds())
        scenario.onActivity { indicator.value = indicator.value.copy(enabled = true, style = QueueIndicatorStyle.RING) }
        waitForCondition("indicators restored") { indicatorCount() == 3 }
        runBlocking { repository.dequeue("episode-2") }
        waitForCondition("dequeued indicator removed") { indicatorCount() == 2 }
        runBlocking { repository.enqueue("episode-2") }
        waitForCondition("enqueued indicator added") { indicatorCount() == 3 }
        assertEquals(0, played)
    }

    @Test fun podcastIndicatorsDisappearWhenEpisodesLeaveQueue() {
        showEpisodes()
        waitForCondition("podcast queued indicator visible") { indicatorCount() > 0 }
        runBlocking { (1..3).forEach { repository.dequeue("episode-$it") } }
        waitForCondition("unqueued podcast episodes have no indicators") { indicatorCount() == 0 }
        runBlocking { repository.enqueue("episode-1") }
        waitForCondition("podcast indicator follows enqueue") { indicatorCount() == 1 }
        assertEquals(0, played)
    }

    @Test fun indicatorDesignControlsPersistAndSurviveDisableAndReopen() {
        val store = SettingsStore(instrumentation.targetContext)
        fun saved() = runBlocking { store.settings.first().queueIndicator }
        val original = saved()
        fun showAppearance() {
            scenario.onActivity { activity -> activity.setContent {
                LoheTheme { Box(Modifier.safeDrawingPadding()) {
                    SettingsScreen(Appearance.DARK, {}, {}, {}, store, SettingsCategory.APPEARANCE)
                } }
            } }
            waitFor("Up Next indicator")
        }
        fun setEnabled(value: Boolean) = runBlocking { store.setQueueIndicatorEnabled(value) }
        try {
            setEnabled(true)
            showAppearance()
            tap("Ring")
            tap("Teal")
            tap("Large")
            val design = QueueIndicatorSettings(true, QueueIndicatorStyle.RING, QueueIndicatorColor.TEAL, QueueIndicatorSize.LARGE)
            waitForCondition("design persisted") { saved() == design }
            waitForCondition("preview visible") { indicatorCount() == 1 }
            val toggle = allNodes().first { it.contentDescription?.toString() == "Up Next indicator" }
            tapBounds(Rect().also(toggle::getBoundsInScreen))
            waitForCondition("indicator disabled") { !saved().enabled && indicatorCount() == 0 }
            assertNull(find("Ring"))
            showQueue()
            showAppearance()
            assertEquals(design.copy(enabled = false), SettingsStore(instrumentation.targetContext).settings.let { runBlocking { it.first().queueIndicator } })
            val restoredToggle = allNodes().first { it.contentDescription?.toString() == "Up Next indicator" }
            tapBounds(Rect().also(restoredToggle::getBoundsInScreen))
            waitForCondition("design restored") { saved() == design && indicatorCount() == 1 }
            waitFor("Ring")
        } finally {
            runBlocking {
                store.setQueueIndicatorEnabled(original.enabled)
                store.setQueueIndicatorStyle(original.style)
                store.setQueueIndicatorColor(original.color)
                store.setQueueIndicatorSize(original.size)
            }
        }
    }

    private fun allNodes(): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isVisibleToUser) nodes.add(node)
            for (index in 0 until node.childCount) visit(node.getChild(index))
        }
        visit(instrumentation.uiAutomation.rootInActiveWindow)
        return nodes
    }

    private fun indicatorCount() = allNodes().count { it.contentDescription?.toString() == "In Up Next" }

    @Test fun podcastTitleOpensNotesAndBothExplicitPlayControlsStillPlay() {
        showEpisodes()
        val original = queuedIds()
        tap("Interaction episode 1")
        waitFor("SHOW NOTES")
        waitFor("Interaction fixture notes 1")
        assertEquals(0, played)
        assertEquals(original, queuedIds())
        tap("Play")
        waitForCondition("notes Play callback") { played == 1 }
        waitForCondition("notes dismissed") { find("SHOW NOTES") == null }
        tap("Play episode")
        waitForCondition("episode Play callback") { played == 2 }
        assertEquals(original, queuedIds())
    }

    @Test fun upNextTitleOpensNotesWithoutReorderingAndNotesPlayPromotes() {
        showQueue()
        val original = queuedIds()
        tap("Interaction episode 2")
        waitFor("SHOW NOTES")
        waitFor("Interaction fixture notes 2")
        assertEquals(0, played)
        assertEquals(original, queuedIds())
        tap("Play")
        waitForCondition("notes Play callback") { played == 1 }
        waitForCondition("notes Play promotes episode") {
            queuedIds() == listOf("episode-2", "episode-1", "episode-3")
        }
        waitForCondition("notes dismissed") { find("SHOW NOTES") == null }
    }

    @Test fun selectionTapSelectsWithoutOpeningNotesOrPlaying() {
        showEpisodes()
        val bounds = Rect().also(waitFor("Interaction episode 1")::getBoundsInScreen)
        val down = SystemClock.uptimeMillis()
        val press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
        press.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        instrumentation.uiAutomation.injectInputEvent(press, true)
        press.recycle()
        SystemClock.sleep(700)
        val release = MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
        release.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        instrumentation.uiAutomation.injectInputEvent(release, true)
        release.recycle()
        waitFor("1 selected")
        tap("Interaction episode 2")
        waitFor("2 selected")
        assertNull(find("SHOW NOTES"))
        assertEquals(0, played)
        assertEquals(listOf("episode-1", "episode-2", "episode-3"), queuedIds())
    }

    @Test fun queueSwipeCancellationDoesNotMutateAndTopEndPersistWithoutPlaying() {
        showQueue()
        val original = queuedIds()
        swipeEpisode("Interaction episode 2", right = true)
        waitFor("Top")
        waitFor("End")
        tap("Cancel")
        waitForCondition("tray closed") { find("Cancel") == null }
        assertEquals(original, queuedIds())
        swipeEpisode("Interaction episode 2", right = true)
        tap("Interaction episode 1")
        waitForCondition("outside tap closes tray") { find("Cancel") == null }
        assertEquals(original, queuedIds())
        swipeEpisode("Interaction episode 2", right = true)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitForCondition("Back closes tray") { find("Cancel") == null }
        assertEquals(original, queuedIds())
        swipeEpisode("Interaction episode 2", right = true)
        tap("Top")
        waitForCondition("Top persists order") { queuedIds() == listOf("episode-2", "episode-1", "episode-3") }
        waitForCondition("tray closed") { find("Cancel") == null }
        swipeEpisode("Interaction episode 2", right = true)
        tap("End")
        waitForCondition("End persists order") { queuedIds() == listOf("episode-1", "episode-3", "episode-2") }
        assertEquals(0, played)
    }

    @Test fun moveToEndDoesNotEnqueueMissingOrUnqueuedEpisodesAndIsIdempotent() = runBlocking {
        repository.moveToEndInQueue("episode-1")
        assertEquals(listOf("episode-2", "episode-3", "episode-1"), queuedIds())
        val snapshot = db.episodeDao().queueSnapshot()
        repository.moveToEndInQueue("episode-1")
        assertEquals(snapshot, db.episodeDao().queueSnapshot())
        repository.moveToEndInQueue("missing")
        repository.moveToEndInQueue("unqueued")
        assertEquals(snapshot, db.episodeDao().queueSnapshot())
        assertNull(db.episodeDao().get("unqueued")!!.queuePosition)
    }

    @Test fun actualQueueEdgeDragReachesEndAndPersistsOnRelease() {
        runBlocking {
            db.episodeDao().upsertAll((4..30).map { index ->
                EpisodeEntity("episode-$index", "show", "Interaction episode $index", "https://example.invalid/$index",
                    0, queuePosition = index - 1, downloadState = 2, localPath = "/fixture-$index.audio")
            })
        }
        showQueue()
        val original = queuedIds()
        val row = Rect().also(waitFor("Interaction episode 2")::getBoundsInScreen)
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun collect(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isVisibleToUser) nodes.add(node)
            for (index in 0 until node.childCount) collect(node.getChild(index))
        }
        collect(instrumentation.uiAutomation.rootInActiveWindow)
        val handle = requireNotNull(nodes.filter { it.contentDescription?.toString() == "Drag to reorder" }
            .minByOrNull { kotlin.math.abs(Rect().also(it::getBoundsInScreen).centerY() - row.centerY()) })
        val handleBounds = Rect().also(handle::getBoundsInScreen)
        val viewport = Rect().also(requireNotNull(nodes.firstOrNull { it.isScrollable })::getBoundsInScreen)
        val x = handleBounds.centerX().toFloat()
        val start = handleBounds.centerY().toFloat()
        val end = viewport.bottom - 8f
        val began = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(began, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, start)
        SystemClock.sleep(700)
        try {
            repeat(30) { step ->
                send(MotionEvent.ACTION_MOVE, start + (end - start) * (step + 1) / 30f)
                SystemClock.sleep(16)
            }
            // Keep the finger stationary while edge scrolling carries the real QueueRow.
            waitForCondition("last queue row visible during stationary edge hold") {
                find("Interaction episode 30")?.isVisibleToUser == true
            }
            SystemClock.sleep(1000)
            assertEquals("Queue writes only on drop", original, queuedIds())
        } finally {
            send(MotionEvent.ACTION_UP, end)
        }
        waitForCondition("dragged UUID persisted at queue end") { queuedIds().lastOrNull() == "episode-2" }
        assertEquals(original.filterNot { it == "episode-2" } + "episode-2", queuedIds())
        val settled = queuedIds()
        SystemClock.sleep(500)
        assertEquals("Release stops reordering", settled, queuedIds())
        assertEquals(0, played)
    }
    @Test fun actualQueueEdgeDragReachesTopAndPersistsOnRelease() {
        runBlocking {
            db.episodeDao().upsertAll((4..30).map { index ->
                EpisodeEntity("episode-$index", "show", "Interaction episode $index", "https://example.invalid/$index",
                    0, queuePosition = index - 1, downloadState = 2, localPath = "/fixture-$index.audio")
            })
        }
        showQueue()
        fun queueList(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isScrollable && node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }) return node
            for (index in 0 until node.childCount) queueList(node.getChild(index))?.let { return it }
            return null
        }
        if (find("Interaction episode 15")?.isVisibleToUser != true) {
            val list = requireNotNull(queueList(instrumentation.uiAutomation.rootInActiveWindow))
            assertTrue(list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        }
        waitFor("Interaction episode 15")
        val original = queuedIds()
        val row = Rect().also(waitFor("Interaction episode 15")::getBoundsInScreen)
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun collect(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isVisibleToUser) nodes.add(node)
            for (index in 0 until node.childCount) collect(node.getChild(index))
        }
        collect(instrumentation.uiAutomation.rootInActiveWindow)
        val handle = requireNotNull(nodes.filter { it.contentDescription?.toString() == "Drag to reorder" }
            .minByOrNull { kotlin.math.abs(Rect().also(it::getBoundsInScreen).centerY() - row.centerY()) })
        val handleBounds = Rect().also(handle::getBoundsInScreen)
        val viewport = Rect().also(requireNotNull(nodes.firstOrNull { it.isScrollable })::getBoundsInScreen)
        val x = handleBounds.centerX().toFloat()
        val start = handleBounds.centerY().toFloat()
        val end = viewport.top + 8f
        val began = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(began, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, start)
        SystemClock.sleep(700)
        try {
            repeat(30) { step ->
                send(MotionEvent.ACTION_MOVE, start + (end - start) * (step + 1) / 30f)
                SystemClock.sleep(16)
            }
            // Keep the finger stationary while edge scrolling carries the real QueueRow.
            waitForCondition("first queue row visible during stationary edge hold") {
                find("Interaction episode 1")?.isVisibleToUser == true
            }
            SystemClock.sleep(1000)
            assertEquals("Queue writes only on drop", original, queuedIds())
        } catch (failure: AssertionError) {
            val visible = (1..30).filter { find("Interaction episode $it")?.isVisibleToUser == true }
            throw AssertionError("Top edge drag failed; DB order=${queuedIds()}; visible fixture rows=$visible; handle=$handleBounds; viewport=$viewport", failure)
        } finally {
            send(MotionEvent.ACTION_UP, end)
        }
        waitForCondition("dragged UUID persisted at queue top") { queuedIds().firstOrNull() == "episode-15" }
        assertEquals(listOf("episode-15") + original.filterNot { it == "episode-15" }, queuedIds())
        val settled = queuedIds()
        SystemClock.sleep(500)
        assertEquals("Release stops reordering", settled, queuedIds())
        assertEquals(0, played)
    }
    private fun queuedIds() = runBlocking { db.episodeDao().queueSnapshot().map { it.uuid } }

    private fun swipeEpisode(title: String, right: Boolean) {
        waitForEpisode(title)
        val bounds = Rect().also(waitFor(title)::getBoundsInScreen)
        val screen = Rect().also(instrumentation.uiAutomation.rootInActiveWindow::getBoundsInScreen)
        // Start over text, away from the row's explicit Play icon and drag handle.
        val start = if (right) screen.left + screen.width() * .24f else screen.left + screen.width() * .78f
        val end = if (right) screen.left + screen.width() * .91f else screen.left + screen.width() * .11f
        val down = SystemClock.uptimeMillis()
        for (step in 0..20) {
            val action = when (step) { 0 -> MotionEvent.ACTION_DOWN; 20 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                start + (end - start) * step / 20f, bounds.centerY().toFloat(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
            SystemClock.sleep(16)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(600)
    }

    private fun tapBounds(bounds: Rect) {
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(80)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(350)
    }

    private fun find(text: String, root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.text?.toString() == text || root.contentDescription?.toString() == text) return root
        for (index in 0 until root.childCount) find(text, root.getChild(index))?.let { return it }
        return null
    }

    private fun waitForEpisode(text: String) {
        repeat(12) {
            instrumentation.waitForIdleSync()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val match = find(text, root)
            if (match != null && match.isVisibleToUser && !Rect().also(match::getBoundsInScreen).isEmpty) return
            fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.isScrollable && node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }) return node
                for (i in 0 until node.childCount) scrollable(node.getChild(i))?.let { return it }
                return null
            }
            scrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(300)
        }
        throw AssertionError("Could not scroll to $text")
    }

    private fun waitFor(text: String): AccessibilityNodeInfo {
        var result: AccessibilityNodeInfo? = null
        waitForCondition("$text to appear") { find(text)?.let { result = it; true } == true }
        return requireNotNull(result)
    }

    private fun waitForCondition(message: String, condition: () -> Boolean) {
        repeat(200) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for $message")
    }

    private fun tap(text: String) {
        val node = waitFor(text)
        val bounds = Rect().also(node::getBoundsInScreen)
        assertFalse("$text has empty bounds", bounds.isEmpty)
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(event)
            event.recycle()
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(80)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(350)
    }
}
