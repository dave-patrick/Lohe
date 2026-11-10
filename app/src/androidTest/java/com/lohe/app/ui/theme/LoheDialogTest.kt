package com.lohe.app.ui.theme

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.lohe.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoheDialogTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun largeTextKeepsBothActionsVisibleAndDismissesOnce() {
        assertTrue(
            "Run UI tests only in the isolated .audit app",
            InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".audit"),
        )
        var dismissCount = 0
        var extraActionCount = 0
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                        LoheTheme {
                            LoheDialog(
                                title = "Show controls over other apps?",
                                onDismiss = { dismissCount++ },
                                dismissLabel = "Not now",
                                onDismissAction = { extraActionCount++ },
                                confirmLabel = "Open permission settings",
                                onConfirm = {},
                                body = { Text("Allow optional floating controls.") },
                            )
                        }
                    }
                }
            }
            compose.onNodeWithText("Not now").assertIsDisplayed()
            compose.onNodeWithText("Open permission settings").assertIsDisplayed()
            compose.onNodeWithText("Not now").performClick()
            compose.runOnIdle {
                assertEquals(1, dismissCount)
                assertEquals(1, extraActionCount)
            }
        }
    }
}
