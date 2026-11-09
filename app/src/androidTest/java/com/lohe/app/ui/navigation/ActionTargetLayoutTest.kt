package com.lohe.app.ui.navigation

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.lohe.app.MainActivity
import com.lohe.app.ui.theme.LoheTheme
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ActionTargetLayoutTest {
    @Test fun denseActionsReserveSeparateTargetsAndWrap() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertTrue(instrumentation.targetContext.packageName.endsWith(".audit"))
        val bounds = arrayOfNulls<Rect>(4)
        val laidOut = CountDownLatch(4)
        var minimum = 0f
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    LoheTheme {
                        minimum = with(LocalDensity.current) { 48.dp.toPx() }
                        FlowRow(Modifier.width(120.dp)) {
                            repeat(4) { index ->
                                TouchTargetIconButton(
                                    onClick = {}, layoutSize = 26.dp,
                                    modifier = Modifier.onGloballyPositioned {
                                        val firstLayout = bounds[index] == null
                                        bounds[index] = it.boundsInRoot()
                                        if (firstLayout) laidOut.countDown()
                                    },
                                ) { Text(index.toString()) }
                            }
                        }
                    }
                }
            }
            assertTrue("Wait for the first Compose layout", laidOut.await(10, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val targets = bounds.map { requireNotNull(it) }
                targets.forEach {
                    assertTrue("Full target width", it.width >= minimum - 1)
                    assertTrue("Full target height", it.height >= minimum - 1)
                }
                targets.forEachIndexed { index, rect ->
                    targets.drop(index + 1).forEach { other ->
                        assertFalse("Targets must not overlap", rect.overlaps(other))
                    }
                }
                assertTrue("Actions wrap on narrow layouts", targets[2].top >= targets[0].bottom)
            }
        }
    }
}
