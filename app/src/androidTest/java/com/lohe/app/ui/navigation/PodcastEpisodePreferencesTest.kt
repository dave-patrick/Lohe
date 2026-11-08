package com.lohe.app.ui.navigation

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lohe.app.MainActivity
import com.lohe.app.data.EpisodeFilter
import com.lohe.app.data.EpisodesViewModel
import com.lohe.app.data.QueueViewModel
import com.lohe.app.data.SubscriptionRepository
import com.lohe.app.data.db.AppDatabase
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import com.lohe.app.data.fetch.RssPodcastFetcher
import com.lohe.app.ui.theme.LoheTheme
import com.lohe.app.ui.theme.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PodcastEpisodePreferencesTest {
    @get:org.junit.Rule val portrait = PortraitRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val show = "preferences-${UUID.randomUUID()}"
    private lateinit var db: AppDatabase
    private lateinit var repository: SubscriptionRepository
    private var scenario: ActivityScenario<MainActivity>? = null
    private val scopes = mutableListOf<CoroutineScope>()
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setup() {
        assertTrue("UI fixtures require the isolated audit app", context.packageName.endsWith(".audit"))
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        runBlocking {
            db.podcastDao().upsert(PodcastEntity(show, "Preference fixture", feedUrl = "https://example.invalid/feed"))
            db.episodeDao().upsertAll(listOf(
                EpisodeEntity("$show-old", show, "Older played fixture", "https://example.invalid/old", 1, playingStatus = 2),
                EpisodeEntity("$show-new", show, "Newer played fixture", "https://example.invalid/new", 2, playingStatus = 2),
                EpisodeEntity("$show-unplayed", show, "Unplayed fixture", "https://example.invalid/unplayed", 3),
            ))
        }
        repository = SubscriptionRepository(db.podcastDao(), db.episodeDao(), db.listeningHistoryDao(),
            RssPodcastFetcher(podcastDao = db.podcastDao(), episodeDao = db.episodeDao()))
    }

    @After fun teardown() {
        scenario?.close()
        scopes.forEach { it.cancel() }
        instrumentation.runOnMainSync { stores.forEach { it.clear() } }
        if (::db.isInitialized) db.close()
    }

    @Test fun reopeningStartsUnplayedButPreservesSortOrder() {
        val first = openScreen()
        tap("Played")
        tap("Change sort order")
        tap("Oldest")
        waitUntil { first.filter.value == EpisodeFilter.Filter.PLAYED && first.order.value == EpisodeFilter.Order.OLDEST_FIRST }
        waitUntil { first.episodes.value.map { it.uuid } == listOf("$show-old", "$show-new") }
        scenario!!.close()
        scenario = null
        instrumentation.runOnMainSync { stores.last().clear() }
        val reopened = openScreen()
        waitUntil { reopened.filter.value == EpisodeFilter.Filter.UNPLAYED && reopened.order.value == EpisodeFilter.Order.OLDEST_FIRST }
        waitUntil { reopened.episodes.value.map { it.uuid } == listOf("$show-unplayed") }
        assertNull(find("Older played fixture"))
        assertNull(find("Newer played fixture"))
        tap("All")
        waitUntil { reopened.episodes.value.size == 3 }
        tap("Change sort order")
        waitForText("Oldest")
        val otherId = "other-${UUID.randomUUID()}"
        val other = observedViewModel(otherId, SettingsStore(context))
        waitUntil { other.filter.value == EpisodeFilter.Filter.UNPLAYED && other.order.value == EpisodeFilter.Order.NEWEST_FIRST }
        runBlocking {
            assertEquals(EpisodeFilter.Filter.UNPLAYED, SettingsStore(context).podcastEpisodeFilter(otherId).first())
            assertEquals(EpisodeFilter.Order.NEWEST_FIRST, SettingsStore(context).podcastEpisodeOrder(otherId).first())
        }
    }

    @Test fun savedPlayedFilterDoesNotOverrideNewVisitDefault() {
        runBlocking { SettingsStore(context).setPodcastEpisodeFilter(show, EpisodeFilter.Filter.PLAYED) }
        val vm = observedViewModel(show, SettingsStore(context))
        waitUntil { vm.episodes.value.isNotEmpty() }
        assertEquals(EpisodeFilter.Filter.UNPLAYED, vm.filter.value)
        assertEquals(listOf("$show-unplayed"), vm.episodes.value.map { it.uuid })
    }

    @Test fun noStoreStartsUnplayedImmediately() {
        lateinit var vm: EpisodesViewModel
        instrumentation.runOnMainSync {
            vm = EpisodesViewModel(repository, show)
            stores.add(ViewModelStore().also { it.put("episodes", vm) })
        }
        assertEquals(EpisodeFilter.Filter.UNPLAYED, vm.filter.value)
    }

    @Test fun allPlayedPodcastCanSwitchFromEmptyUnplayedToAll() {
        runBlocking {
            val episode = db.episodeDao().get("$show-unplayed")!!
            db.episodeDao().upsertAll(listOf(episode.copy(playingStatus = EpisodeEntity.PLAYED)))
        }
        val vm = openScreen()
        waitUntil { vm.podcast.value?.uuid == show && vm.filter.value == EpisodeFilter.Filter.UNPLAYED }
        assertTrue(vm.episodes.value.isEmpty())
        tap("All")
        waitUntil { vm.episodes.value.size == 3 }
        waitForText("Older played fixture")
    }

    @Test fun everyFilterAndOrderRoundTripsAcrossStoreInstances() = runBlocking {
        for (filter in EpisodeFilter.Filter.entries) {
            SettingsStore(context).setPodcastEpisodeFilter(show, filter)
            assertEquals(filter, SettingsStore(context).podcastEpisodeFilter(show).first())
        }
        for (order in EpisodeFilter.Order.entries) {
            SettingsStore(context).setPodcastEpisodeOrder(show, order)
            assertEquals(order, SettingsStore(context).podcastEpisodeOrder(show).first())
        }
    }

    private fun observedViewModel(id: String, settings: SettingsStore): EpisodesViewModel {
        lateinit var vm: EpisodesViewModel
        instrumentation.runOnMainSync {
            vm = EpisodesViewModel(repository, id, settings)
            stores.add(ViewModelStore().also { it.put("episodes", vm) })
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scopes.add(scope)
        scope.launch { vm.filter.collect {} }
        scope.launch { vm.order.collect {} }
        scope.launch { vm.episodes.collect {} }
        return vm
    }

    private fun openScreen(): EpisodesViewModel {
        val vm = observedViewModel(show, SettingsStore(context))
        val queue = QueueViewModel(repository)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity ->
            assertFalse("Unlock phone for UI checks", context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.setContent {
                LoheTheme { Box(Modifier.safeDrawingPadding()) { SwipeActionsHost {
                    EpisodesScreen(vm, queue, {}, {}, {}, {}, {}, {}, {})
                } } }
            }
        }
        waitForText("Preference fixture")
        return vm
    }

    private fun find(text: String, node: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
        for (index in 0 until node.childCount) find(text, node.getChild(index))?.let { return it }
        return null
    }

    private fun waitUntil(condition: () -> Boolean) {
        repeat(100) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Preference state did not settle")
    }

    private fun waitForText(text: String): AccessibilityNodeInfo {
        waitUntil { find(text) != null }
        return find(text)!!
    }

    private fun tap(text: String) {
        val bounds = Rect().also { waitForText(text).getBoundsInScreen(it) }
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(event)
            event.recycle()
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(80)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
    }
}
