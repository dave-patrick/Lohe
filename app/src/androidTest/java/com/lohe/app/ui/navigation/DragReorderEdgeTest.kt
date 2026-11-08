package com.lohe.app.ui.navigation

import android.graphics.RectF
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lohe.app.MainActivity
import com.lohe.app.ui.theme.LoheTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DragReorderEdgeTest {
    @get:org.junit.Rule val portrait = PortraitRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Volatile private var handle = RectF()
    @Volatile private var viewport = RectF()
    @Volatile private var order = emptyList<Int>()
    @Volatile private var drops = 0
    @Volatile private var active = false
    @Volatile private var dragState: DragReorderState? = null

    @Test fun stationaryTopHoldReachesFirstDraggableAndStopsOnEnd() = exercise(false, false)
    @Test fun stationaryBottomHoldReachesLastAndStopsOnEnd() = exercise(true, false)
    @Test fun earlyEndStopsEdgeScrollAndDropsOnlyOnce() = exercise(true, true)

    @Test fun rejectedGroupBoundaryCanReturnToAllowedGroup() = exercise(true, false, true)

    private fun exercise(downward: Boolean, earlyEnd: Boolean, boundedGroup: Boolean = false) {
        assertTrue(instrumentation.targetContext.packageName.endsWith(".audit"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            scenario.onActivity { activity -> activity.setContent {
                LoheTheme {
                    val rows = remember { mutableStateListOf<Int>().apply { addAll(0..40) } }
                    val list = rememberLazyListState(initialFirstVisibleItemIndex = 18)
                    val state = rememberDragReorderState(list, { from, to ->
                        rows.add(to, rows.removeAt(from))
                        order = rows.toList()
                    }, { drops++; active = false }, firstDraggableIndex = 1,
                        canMove = { from, to -> !boundedGroup || (from in 18..22 && to in 18..22) })
                    dragState = state
                    order = rows.toList()
                    active = state.isDragging
                    Box(Modifier.safeDrawingPadding().padding(horizontal = 32.dp)) {
                    LazyColumn(Modifier.height(400.dp).onGloballyPositioned {
                        val origin = it.localToScreen(Offset.Zero)
                        viewport = RectF(origin.x, origin.y, origin.x + it.size.width, origin.y + it.size.height)
                    }, state = list) {
                        itemsIndexed(rows, key = { _, id -> id }) { index, id ->
                            Row(Modifier.fillMaxWidth().height(64.dp)) {
                                DragHandle(state, index, id, Modifier.width(48.dp).height(64.dp).onGloballyPositioned {
                                    if (id == 20) {
                                        val origin = it.localToScreen(Offset.Zero)
                                        handle = RectF(origin.x, origin.y, origin.x + it.size.width, origin.y + it.size.height)
                                    }
                                })
                                Text("Fixture $id")
                            }
                        }
                    }
                    }
                }
            } }
            waitUntil { !handle.isEmpty && !viewport.isEmpty }
            // Exercise the helper against a real measured LazyColumn. Actual
            // QueueScreen tests separately cover the handle's real touch gesture.
            val start = handle.centerY()
            val end = if (downward) viewport.bottom - 8 else viewport.top + 8
            var previousPosition = start
            fun moveTo(y: Float) {
                val delta = y - previousPosition
                scenario.onActivity { dragState!!.onDrag(delta) }
                previousPosition = y
            }
            fun endDrag() {
                scenario.onActivity { dragState!!.onDragEnd() }
            }
            scenario.onActivity { dragState!!.onDragStart(20) }
            waitUntil { active }
            repeat(30) { step ->
                moveTo(start + (end - start) * (step + 1) / 30f)
                SystemClock.sleep(16)
            }
            if (boundedGroup) {
                // Hold against the real viewport edge beyond the allowed group.
                // The helper must retain the carried item at the rejected boundary.
                SystemClock.sleep(1000)
                assertTrue("Rejected edge hold must preserve the active drag", active)
                assertEquals("Stationary hold must not cause an early drop", 0, drops)
                assertTrue("Rejected target must not cross the allowed group", order.indexOf(20) in 18..22)
                assertEquals(23, order[23])
                val back = start - viewport.height() * (96f / 400f)
                repeat(30) { step ->
                    moveTo(end + (back - end) * (step + 1) / 30f)
                    SystemClock.sleep(16)
                }
                waitUntil { order.indexOf(20) < 20 }
                assertTrue("Rejection must preserve the active drag", active)
                endDrag()
                waitUntil { drops == 1 && !active }
                return
            }
            if (!earlyEnd) {
                waitUntil(15000) { order.indexOf(20) == if (downward) 40 else 1 }
                assertTrue("Held drag must remain active through multiple viewports", active)
                assertEquals("Reserved header cannot be reordered", 0, order.first())
            } else {
                waitUntil { order.indexOf(20) > 22 }
            }
            endDrag()
            waitUntil { drops == 1 && !active }
            val settled = order
            SystemClock.sleep(500)
            assertEquals("Ending the drag stops reordering", settled, order)
            assertEquals(1, drops)
        }
    }

    private fun waitUntil(timeout: Long = 5000, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(30)
        }
        assertTrue("Condition did not become true within $timeout ms: carriedIndex=${order.indexOf(20)}, active=$active, drops=$drops, viewport=$viewport, handle=$handle, order=$order", predicate())
    }
}
