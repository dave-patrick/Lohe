package com.lohe.app.ui.navigation

import android.graphics.Rect
import android.graphics.Bitmap
import android.os.Bundle
import java.io.File
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lohe.app.MainActivity
import com.lohe.app.data.PlaylistRepository
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

/** Exercise assignment through the actual screen; all library and playlist data is in memory. */
@RunWith(AndroidJUnit4::class)
class PlaylistPodcastAssignmentTest {
    @get:org.junit.Rule val portrait = PortraitRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var db: AppDatabase
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var playlists: PlaylistRepository
    private lateinit var queue: QueueViewModel
    private lateinit var playlistId: String
    @Volatile private var played = 0

    @Before fun setup() {
        assertTrue("Only run against the isolated audit app", instrumentation.targetContext.packageName.endsWith(".audit"))
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        runBlocking {
            db.podcastDao().upsert(PodcastEntity("assigned-show", "Assignment fixture show", feedUrl = "https://example.invalid/assigned"))
            db.podcastDao().upsert(PodcastEntity("manual-show", "Manual fixture show", feedUrl = "https://example.invalid/manual"))
            repeat(12) { index ->
                db.podcastDao().upsert(PodcastEntity("extra-$index", "Extra podcast ${index + 1}", feedUrl = "https://example.invalid/extra-$index"))
            }
            db.episodeDao().upsertAll(listOf(
                EpisodeEntity("manual", "manual-show", "Manual fixture episode", "https://example.invalid/manual-episode", 300, description = "Playlist fixture episode notes"),
                EpisodeEntity("unplayed", "assigned-show", "Assigned unplayed fixture", "https://example.invalid/unplayed", 200),
                EpisodeEntity("played", "assigned-show", "Assigned played fixture", "https://example.invalid/played", 100, playingStatus = 2),
            ))
        }
        playlists = PlaylistRepository()
        playlistId = playlists.createPlaylist("Assignment fixture playlist", initialEpisodes = listOf("manual"), autoDownload = false).id
        val repository = SubscriptionRepository(db.podcastDao(), db.episodeDao(), db.listeningHistoryDao(),
            RssPodcastFetcher(podcastDao = db.podcastDao(), episodeDao = db.episodeDao()))
        queue = QueueViewModel(repository, playlists)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                LoheTheme { Box(Modifier.safeDrawingPadding()) { SwipeActionsHost { PlaylistsScreen(queue, onPlay = { played++ }) } } }
            }
        }
        waitFor("Assignment fixture playlist")
    }

    @After fun teardown() {
        if (::scenario.isInitialized) scenario.close()
        if (::db.isInitialized) db.close()
    }

    @Test fun episodeTapOpensNotesWithoutPlayingAndExplicitPlayStillWorks() {
        tap("Assignment fixture playlist")
        waitForEpisode("Manual fixture episode")
        tap("Manual fixture episode")
        waitFor("SHOW NOTES")
        waitFor("Playlist fixture episode notes")
        assertEquals("Opening notes must not play", 0, played)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        waitFor("Assign podcasts")
        tap("Play")
        waitForCondition("Explicit Play should still play") { played == 1 }
    }

    @Test fun playlistOpensUnplayedAndResetsAfterReopening() {
        queue.assignPlaylistPodcasts(playlistId, setOf("assigned-show"))
        tap("Assignment fixture playlist")
        waitFor("Assigned unplayed fixture")
        assertNull("Listened episodes should be hidden on opening", find("Assigned played fixture"))
        tap("Play")
        waitForCondition("Play to queue only unplayed episodes") {
            runBlocking { db.episodeDao().queueSnapshot().map { it.uuid }.toSet() == setOf("manual", "unplayed") }
        }
        tap("Show all episodes")
        assertNull("Played episodes must stay out of assigned playlists even with Show all", find("Assigned played fixture"))
        scrollToTop()
        tap("Back to Playlists")
        waitForCondition("playlist overview after Back") { find("Back to Playlists") == null }
        tap("Assignment fixture playlist")
        waitFor("Back to Playlists")
        waitFor("Assigned unplayed fixture")
        waitForCondition("Reopening should restore unplayed") { find("Assigned played fixture") == null }
        runBlocking { db.episodeDao().dequeue("manual"); db.episodeDao().dequeue("unplayed") }
        tap("Up Next")
        tap("Bottom")
        waitForCondition("Up Next to queue only visible episodes") {
            runBlocking { db.episodeDao().queueSnapshot().map { it.uuid }.toSet() == setOf("manual", "unplayed") }
        }
        screenshot("playlist-unplayed.png")
    }

    @Test fun assigningAndRemovingPodcastPreservesManualPlaylist() {
        tap("Assignment fixture playlist")
        waitFor("Manual fixture episode")
        tap("Assign podcasts")
        tap("Assignment fixture show")
        tap("Cancel")
        assertTrue(playlists.playlists.value.single { it.id == playlistId }.assignedPodcastUuids.isEmpty())
        tap("Assign podcasts")
        screenshot("playlist-picker-full.png")
        tap("Search podcasts")
        fun editable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }) return node
            for (i in 0 until node.childCount) editable(node.getChild(i))?.let { return it }
            return null
        }
        val search = requireNotNull(editable(instrumentation.uiAutomation.rootInActiveWindow))
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Assignment fixture") }
        assertTrue(search.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
        waitFor("Assignment fixture show")
        waitForCondition("search to hide unrelated podcast") { find("Manual fixture show") == null }
        screenshot("playlist-picker.png")
        tap("Assignment fixture show")
        tap("Save")
        waitForCondition("assignment to persist") {
            playlists.playlists.value.single { it.id == playlistId }.assignedPodcastUuids == listOf("assigned-show")
        }
        waitForEpisode("Assigned unplayed fixture")
        assertNull("Assignment must respect the unplayed default", find("Assigned played fixture"))
        tap("Show all episodes")
        assertNull("Played episodes must stay out of assigned playlists even with Show all", find("Assigned played fixture"))

        scrollToTop()
        screenshot("playlist-detail.png")
        tap("Playlist options")
        tap("Select episodes")
        waitFor("0 selected")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        waitFor("Podcasts")
        tap("Playlist options")
        tap("Auto-download")
        waitForCondition("auto-download toggled") { playlists.playlists.value.single { it.id == playlistId }.autoDownload }
        tap("Playlist options")
        tap("Auto-download")
        waitForCondition("auto-download restored") { !playlists.playlists.value.single { it.id == playlistId }.autoDownload }
        tap("Assign podcasts")
        tap("Assignment fixture show")
        tap("Save")
        waitForCondition("assignment to be removed") {
            playlists.playlists.value.single { it.id == playlistId }.assignedPodcastUuids.isEmpty()
        }
        waitForEpisode("Manual fixture episode")
        waitForCondition("resolved playlist to contain only the manual episode") {
            queue.userPlaylists.value.firstOrNull { it.id == playlistId }
                ?.entries?.map { it.episode.uuid } == listOf("manual")
        }
        val restored = playlists.playlists.value.single { it.id == playlistId }
        assertEquals(listOf("manual"), restored.episodeUuids)
        assertTrue(restored.excludedUuids.isEmpty())
        assertFalse(restored.autoDownload)
    }

    @Test fun playedSmartPlaylistExplainsTheUnplayedFilter() {
        waitForEpisode("Played")
        tap("Played")
        waitFor("No unplayed episodes")
        tap("Show all episodes")
        waitForEpisode("Assigned played fixture")
    }

    @Test fun selectionActionsRemainVisibleAfterScrolling() {
        val ids = (1..25).map { "selection-$it" }
        runBlocking {
            db.episodeDao().upsertAll(ids.mapIndexed { index, id ->
                EpisodeEntity(id, "manual-show", "Selection episode ${index + 1}", "https://example.invalid/$id", index.toLong())
            })
        }
        val fixture = playlists.createPlaylist("Selection fixture playlist", initialEpisodes = ids, autoDownload = false)
        tap("Selection fixture playlist")
        waitForEpisode("Selection episode 20")
        val node = waitFor("Selection episode 20")
        val bounds = Rect().also(node::getBoundsInScreen)
        val down = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
        instrumentation.uiAutomation.injectInputEvent(event, true)
        event.recycle()
        SystemClock.sleep(700)
        val up = MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
        instrumentation.uiAutomation.injectInputEvent(up, true)
        up.recycle()
        waitFor("1 selected")
        assertTrue(waitFor("Mark listened").isVisibleToUser)
        assertTrue(waitFor("Exit selection").isVisibleToUser)
        screenshot("playlist-selection.png")
        assertEquals(ids, playlists.playlists.value.single { it.id == fixture.id }.episodeUuids)
    }

    @Test fun playlistSwipeCancellationAndExplicitQueueArchiveActions() {
        tap("Assignment fixture playlist")
        waitForEpisode("Manual fixture episode")
        val original = episode("manual")
        swipeEpisode("Manual fixture episode", right = true)
        assertTray("Up Next", "Archive", "Played", "Remove from queue")
        assertEquals(original, episode("manual"))
        tap("Cancel")
        waitForTrayClosed()
        swipeEpisode("Manual fixture episode", right = false)
        assertTray("Archive", "Up Next", "Played", "Remove from queue")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        waitForTrayClosed()
        swipeEpisode("Manual fixture episode", right = true)
        tap("Assignment fixture playlist") // Outside the row: dismiss without navigation or mutation.
        waitForTrayClosed()
        assertEquals(original, episode("manual"))
        assertTrue(queuedIds().isEmpty())
        assertEquals(0, played)

        repeat(2) {
            swipeEpisode("Manual fixture episode", right = true)
            tapTrayAction("Up Next")
            waitForTrayClosed()
            waitForCondition("only the swiped UUID to be queued once") { queuedIds() == listOf("manual") }
        }
        assertEquals(0, played)
        swipeEpisode("Manual fixture episode", right = false)
        assertEquals(listOf("manual"), queuedIds())
        assertFalse(episode("manual").isArchived)
        tapTrayAction("Archive")
        waitForCondition("archive to dequeue the swiped UUID") { episode("manual").isArchived && queuedIds().isEmpty() }
        assertFalse(episode("unplayed").isArchived)
        assertEquals(0, played)
    }

    @Test fun assignedPlaylistExcludesArchivedEpisodes() {
        runBlocking { db.episodeDao().archive("manual", System.currentTimeMillis()) }
        queue.assignPlaylistPodcasts(playlistId, setOf("manual-show", "assigned-show"))
        tap("Assignment fixture playlist")
        waitForEpisode("Assigned unplayed fixture")
        tap("Show all episodes")
        waitForEpisode("Assigned unplayed fixture")
        assertNull("Archived episodes must stay out even with Show all", find("Manual fixture episode"))
        assertNull("Played episodes must stay out even with Show all", find("Assigned played fixture"))
        assertTrue(episode("manual").isArchived)
        assertTrue(queuedIds().isEmpty())
        assertEquals(0, played)
    }
    @Test fun smartUnplayedSwipeArchivesOnlyAfterConfirmation() {
        tap("Unplayed")
        waitForEpisode("Manual fixture episode")
        swipeEpisode("Manual fixture episode", right = false)
        assertTray("Archive", "Up Next", "Played", "Remove from queue")
        assertFalse(episode("manual").isArchived)
        tapTrayAction("Archive")
        waitForCondition("smart playlist row to archive") { episode("manual").isArchived }
        assertFalse(episode("unplayed").isArchived)
        assertEquals(0, played)
    }

    @Test fun playlistSelectionDisablesBothSwipeDirectionsAndTapSelects() {
        tap("Assignment fixture playlist")
        waitForEpisode("Manual fixture episode")
        tap("Playlist options")
        tap("Select episodes")
        waitFor("0 selected")
        val original = episode("manual")
        swipeEpisode("Manual fixture episode", right = true)
        assertNull(find("Cancel"))
        swipeEpisode("Manual fixture episode", right = false)
        assertNull(find("Cancel"))
        assertEquals(original, episode("manual"))
        tap("Manual fixture episode")
        waitFor("1 selected")
        assertNull(find("SHOW NOTES"))
        assertTrue(queuedIds().isEmpty())
        assertEquals(0, played)
    }

    @Test fun genericQueueKeepsPlayedAndRemoveSwipeActions() {
        runBlocking { db.episodeDao().enqueue("manual") }
        scenario.onActivity { activity ->
            activity.setContent {
                LoheTheme { Box(Modifier.safeDrawingPadding()) { SwipeActionsHost { QueueScreen(queue, onPlay = { played++ }) } } }
            }
        }
        waitForEpisode("Manual fixture episode")
        val original = episode("manual")
        swipeEpisode("Manual fixture episode", right = true)
        assertTray("Played", "Up Next", "Archive", "Remove from queue")
        tap("Cancel")
        waitForTrayClosed()
        swipeEpisode("Manual fixture episode", right = false)
        assertTray("Remove from queue", "Up Next", "Archive", "Played")
        tap("Cancel")
        waitForTrayClosed()
        assertEquals(original, episode("manual"))
        assertEquals(0, played)
    }

    private fun episode(uuid: String) = runBlocking { requireNotNull(db.episodeDao().get(uuid)) }

    private fun queuedIds() = runBlocking { db.episodeDao().queueSnapshot().map { it.uuid } }

    private fun waitForTrayClosed() = waitForCondition("swipe tray to close") { find("Cancel") == null }

    private fun trayAction(text: String): AccessibilityNodeInfo? {
        val cancel = find("Cancel") ?: return null
        val band = Rect().also(cancel::getBoundsInScreen)
        fun search(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            val bounds = Rect().also(node::getBoundsInScreen)
            if (node.isVisibleToUser &&
                (node.text?.toString() == text || node.contentDescription?.toString() == text) &&
                bounds.centerY() in band.top..band.bottom
            ) return node
            for (index in 0 until node.childCount) search(node.getChild(index))?.let { return it }
            return null
        }
        return search(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun assertTray(action: String, vararg absentActions: String) {
        waitFor("Cancel")
        waitForCondition("$action in the swipe tray") { trayAction(action) != null }
        listOf("Up", "Down").forEach { assertNull("Unexpected $it action in swipe tray", trayAction(it)) }
        absentActions.forEach { assertNull("Unexpected $it action in swipe tray", trayAction(it)) }
    }

    private fun tapTrayAction(text: String) {
        waitForCondition("$text action to be ready for a tap") { trayAction(text) != null }
        val node = requireNotNull(trayAction(text)) { "Missing swipe action $text" }
        tapBounds(Rect().also(node::getBoundsInScreen))
    }

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
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(350)
    }

    private fun screenshot(name: String) {
        SystemClock.sleep(600)
        instrumentation.waitForIdleSync()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), name)
        instrumentation.uiAutomation.takeScreenshot()?.let { image ->
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }

    private fun scrollToTop() {
        repeat(12) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (find("Podcasts", root)?.isVisibleToUser == true) return
            fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.isScrollable && node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD }) return node
                for (i in 0 until node.childCount) scrollable(node.getChild(i))?.let { return it }
                return null
            }
            scrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            SystemClock.sleep(300)
        }
        throw AssertionError("Could not return to playlist controls")
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
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(350)
    }
}
