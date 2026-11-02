package com.lohe.app.ui.navigation

import android.graphics.Rect
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
import com.lohe.app.data.*
import com.lohe.app.data.db.AppDatabase
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.fetch.RssPodcastFetcher
import com.lohe.app.ui.theme.LoheTheme
import com.lohe.app.ui.theme.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Actual playlist UI with isolated Room data; never touches the public app. */
@RunWith(AndroidJUnit4::class)
class PlaylistEpisodeSortingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var db: AppDatabase
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var playlists: PlaylistRepository
    private lateinit var queue: QueueViewModel
    private lateinit var settings: SettingsStore
    private lateinit var firstId: String
    private lateinit var secondId: String
    private var originalSmartSort = PlaylistEpisodeSort()
    private val manual = listOf("c", "a", "d", "b")
    private val titles = mapOf("a" to "Sort Alpha", "b" to "Sort Bravo", "c" to "Sort Charlie", "d" to "Sort Delta")
    @Volatile private var played = 0

    @Before fun setup() {
        assertTrue("Only run against the isolated audit app", instrumentation.targetContext.packageName.endsWith(".audit"))
        settings = SettingsStore(instrumentation.targetContext.applicationContext)
        originalSmartSort = runBlocking { settings.playlistEpisodeSort(UNPLAYED_ID).first() }
        runBlocking { settings.setPlaylistEpisodeSort(UNPLAYED_ID, PlaylistEpisodeSort()) }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        runBlocking {
            db.podcastDao().upsert(PodcastEntity("sort-z", " zulu show ", feedUrl = "https://example.invalid/z"))
            db.podcastDao().upsert(PodcastEntity("sort-a", "Alpha show", feedUrl = "https://example.invalid/a"))
            db.episodeDao().upsertAll(listOf(
                EpisodeEntity("a", "sort-z", titles.getValue("a"), "https://example.invalid/a", 300, durationSeconds = 20, sizeBytes = 300),
                EpisodeEntity("b", "sort-a", titles.getValue("b"), "https://example.invalid/b", 100, durationSeconds = 30, sizeBytes = 100),
                EpisodeEntity("c", "sort-z", titles.getValue("c"), "https://example.invalid/c", 200, durationSeconds = 10, sizeBytes = 200),
                EpisodeEntity("d", "sort-a", titles.getValue("d"), "https://example.invalid/d", 0, durationSeconds = 0, sizeBytes = null),
            ))
        }
        playlists = PlaylistRepository()
        firstId = playlists.createPlaylist("Sort fixture one", initialEpisodes = manual, autoDownload = false).id
        secondId = playlists.createPlaylist("Sort fixture two", initialEpisodes = manual, autoDownload = false).id
        runBlocking {
            settings.setPlaylistEpisodeSort(firstId, PlaylistEpisodeSort())
            settings.setPlaylistEpisodeSort(secondId, PlaylistEpisodeSort())
        }
        val repository = SubscriptionRepository(db.podcastDao(), db.episodeDao(), db.listeningHistoryDao(),
            RssPodcastFetcher(podcastDao = db.podcastDao(), episodeDao = db.episodeDao()))
        queue = QueueViewModel(repository, playlists)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity -> activity.setContent {
            LoheTheme { Box(Modifier.safeDrawingPadding()) { SwipeActionsHost { PlaylistsScreen(queue, onPlay = { played++ }) } } }
        } }
        revealOverviewItem("Sort fixture one")
    }

    @After fun teardown() {
        if (::scenario.isInitialized) scenario.close()
        if (::settings.isInitialized) runBlocking {
            if (::firstId.isInitialized) settings.setPlaylistEpisodeSort(firstId, PlaylistEpisodeSort())
            if (::secondId.isInitialized) settings.setPlaylistEpisodeSort(secondId, PlaylistEpisodeSort())
            settings.setPlaylistEpisodeSort(UNPLAYED_ID, originalSmartSort)
        }
        if (::db.isInitialized) db.close()
    }

    @Test fun eachComputedFieldAndDirectionMatchesVisibleRowsAndManualIsPreserved() {
        tap("Sort fixture one")
        assertOrder(manual)
        val expectations = listOf(
            Triple("File size", listOf("b", "c", "a", "d"), listOf("a", "c", "b", "d")),
            Triple("Length", listOf("c", "a", "b", "d"), listOf("b", "a", "c", "d")),
            Triple("Publish date", listOf("b", "c", "a", "d"), listOf("a", "c", "b", "d")),
            Triple("Podcast title", listOf("b", "d", "a", "c"), listOf("a", "c", "b", "d")),
        )
        expectations.forEach { (field, ascending, descending) ->
            choose(field)
            choose("Ascending")
            assertOrder(ascending)
            choose("Descending")
            assertOrder(descending)
            assertEquals(manual, playlists.playlists.value.single { it.id == firstId }.episodeUuids)
        }
        choose("Manual")
        assertOrder(manual)
        assertEquals(manual, playlists.playlists.value.single { it.id == firstId }.episodeUuids)
    }

    @Test fun sortSurvivesReopenAndNewStoreAndIsIndependentForCustomAndSmartPlaylists() {
        tap("Sort fixture one")
        choose("Length"); choose("Descending")
        backToOverview()
        tap("Sort fixture two")
        assertOrder(manual)
        choose("File size"); choose("Ascending")
        backToOverview()
        tap("Unplayed")
        choose("Publish date"); choose("Ascending")
        assertOrder(listOf("b", "c", "a", "d"))
        backToOverview()
        tap("Sort fixture one")
        assertOrder(listOf("b", "a", "c", "d"))
        val recreated = SettingsStore(instrumentation.targetContext.applicationContext)
        assertEquals(PlaylistEpisodeSort(PlaylistEpisodeSortField.LENGTH, PlaylistEpisodeSortDirection.DESCENDING),
            runBlocking { recreated.playlistEpisodeSort(firstId).first() })
        backToOverview()
        tap("Sort fixture two")
        assertOrder(listOf("b", "c", "a", "d"))
        assertEquals(PlaylistEpisodeSort(PlaylistEpisodeSortField.FILE_SIZE, PlaylistEpisodeSortDirection.ASCENDING),
            runBlocking { recreated.playlistEpisodeSort(secondId).first() })
    }

    @Test fun headerPlayAndUpNextBottomFollowDisplayedOrder() {
        tap("Sort fixture one")
        choose("Length"); choose("Descending")
        runBlocking { db.episodeDao().upsertAll(listOf(requireNotNull(db.episodeDao().get("c")).copy(playingStatus = 2))) }
        waitUntil("played episode filtered out") { find(titles.getValue("c")) == null }
        val expected = listOf("b", "a", "d")
        assertOrder(expected)
        top()
        tap("Up Next"); tap("Bottom")
        waitUntil("sorted Up Next queue") { queuedIds() == expected }
        runBlocking { expected.forEach { db.episodeDao().dequeue(it) } }
        top(); tap("Play")
        waitUntil("sorted Play queue") { queuedIds() == expected && played == 1 }
        assertEquals(manual, playlists.playlists.value.single { it.id == firstId }.episodeUuids)
    }

    private fun queuedIds() = runBlocking { db.episodeDao().queueSnapshot().map { it.uuid } }
    private fun revealOverviewItem(text: String) {
        fun visible() = find(text)?.let { it.isVisibleToUser && !Rect().also(it::getBoundsInScreen).isEmpty } == true
        repeat(12) {
            if (visible()) return
            scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        }
        repeat(12) {
            if (visible()) return
            scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
        throw AssertionError("Could not find playlist $text in the overview")
    }
    @Test fun manualDragWorksAfterReturningFromComputedSort() {
        tap("Sort fixture one")
        tap("Show all episodes")
        choose("Length"); choose("Descending")
        assertOrder(listOf("b", "a", "c", "d"))
        assertTrue("Computed sort must hide reorder grips", findAll("Drag to reorder").isEmpty())
        choose("Manual")
        assertOrder(manual)
        top()
        repeat(12) {
            if (find(titles.getValue("c"))?.isVisibleToUser != true || find(titles.getValue("a"))?.isVisibleToUser != true) {
                scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
        }
        val first = Rect().also(waitFor(titles.getValue("c"))::getBoundsInScreen)
        val second = Rect().also(waitFor(titles.getValue("a"))::getBoundsInScreen)
        val grips = findAll("Drag to reorder").filter { it.isVisibleToUser }
        assertTrue("Manual sort must restore reorder grips", grips.isNotEmpty())
        val grip = grips.minBy { kotlin.math.abs(Rect().also(it::getBoundsInScreen).centerY() - first.centerY()) }
        val bounds = Rect().also(grip::getBoundsInScreen)
        val down = SystemClock.uptimeMillis()
        fun inject(action: Int, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.centerX().toFloat(), y, 0)
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        val start = bounds.centerY().toFloat()
        val end = start + second.centerY() - first.centerY()
        inject(MotionEvent.ACTION_DOWN, start)
        SystemClock.sleep(650)
        for (step in 1..20) {
            inject(MotionEvent.ACTION_MOVE, start + (end - start) * step / 20f)
            SystemClock.sleep(25)
        }
        inject(MotionEvent.ACTION_UP, end)
        waitUntil("manual drag persisted after changing sort") {
            playlists.playlists.value.single { it.id == firstId }.episodeUuids == listOf("a", "c", "d", "b")
        }
        assertOrder(listOf("a", "c", "d", "b"))
    }

    private fun findAll(text: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.text?.toString() == text || node.contentDescription?.toString() == text) result.add(node)
            for (i in 0 until node.childCount) visit(node.getChild(i))
        }
        visit(instrumentation.uiAutomation.rootInActiveWindow)
        return result
    }

    private fun choose(label: String) {
        top(); tap("Sort playlist episodes"); tap(label)
        tap("Sort playlist episodes")
        waitUntil("$label selected indicator") {
            var node: AccessibilityNodeInfo? = find(label)
            var selected = false
            repeat(3) { selected = selected || node?.isSelected == true; node = node?.parent }
            selected
        }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        waitUntil("sort menu dismissed") { find("Manual") == null }
    }
    private fun backToOverview() { top(); tap("Back to Playlists"); waitUntil("overview") { find("Back to Playlists") == null } }

    private fun assertOrder(expected: List<String>) {
        // Collect visible titles by screen Y as the real lazy list scrolls.
        top()
        val seen = linkedSetOf<String>()
        repeat(12) {
            instrumentation.waitForIdleSync()
            titles.keys.mapNotNull { id -> find(titles.getValue(id))?.takeIf { it.isVisibleToUser }?.let { node ->
                val bounds = Rect().also(node::getBoundsInScreen)
                if (bounds.isEmpty) null else id to bounds.centerY()
            } }.sortedBy { it.second }.forEach { seen.add(it.first) }
            if (seen.size == expected.size) {
                assertEquals("Rendered episode order", expected, seen.toList())
                return
            }
            scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
        fail("Not all fixture rows appeared: $seen")
    }

    private fun top() {
        repeat(12) {
            if (find("Sort playlist episodes")?.isVisibleToUser == true) return
            scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        }
        fail("Could not return to playlist header")
    }
    private fun scroll(action: Int) {
        fun search(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isScrollable && node.actionList.any { it.id == action }) return node
            for (i in 0 until node.childCount) search(node.getChild(i))?.let { return it }
            return null
        }
        search(instrumentation.uiAutomation.rootInActiveWindow)?.performAction(action)
        SystemClock.sleep(300)
    }
    private fun find(text: String, root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.text?.toString() == text || root.contentDescription?.toString() == text) return root
        for (i in 0 until root.childCount) find(text, root.getChild(i))?.let { return it }
        return null
    }
    private fun waitFor(text: String): AccessibilityNodeInfo {
        var node: AccessibilityNodeInfo? = null
        waitUntil(text) { node = find(text); node != null }
        return requireNotNull(node)
    }
    private fun waitUntil(message: String, condition: () -> Boolean) {
        repeat(200) { instrumentation.waitForIdleSync(); if (condition()) return; SystemClock.sleep(100) }
        throw AssertionError("Timed out waiting for $message")
    }
    private fun tap(text: String) {
        if (text == "Sort fixture one" || text == "Sort fixture two" || text == "Unplayed") revealOverviewItem(text)
        val bounds = Rect().also(waitFor(text)::getBoundsInScreen)
        assertFalse("$text has empty bounds", bounds.isEmpty)
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
            instrumentation.uiAutomation.injectInputEvent(event, true); event.recycle()
        }
        instrumentation.waitForIdleSync(); SystemClock.sleep(350)
    }
}

