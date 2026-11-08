package com.lohe.app.ui.navigation

import android.app.UiAutomation
import android.provider.Settings
import android.view.Surface
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/**
 * Holds the phone upright for touch tests, then puts rotation back the way it was.
 *
 * The touch tests tap and swipe at positions worked out for a portrait list.
 * With auto-rotate on, a phone lying on its side runs them in landscape, where
 * fewer rows fit and the episode a test needs is off-screen, so whole classes
 * failed only in some runs (found 2026-11-08).
 */
class PortraitRule : ExternalResource() {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private var autoRotate = true
    private var userRotation = Surface.ROTATION_0

    override fun before() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        autoRotate = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
        userRotation = Settings.System.getInt(resolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)
        automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    override fun after() {
        // Unfreezing turns auto-rotate on, so only do that if it was on before.
        automation.setRotation(
            if (autoRotate) UiAutomation.ROTATION_UNFREEZE
            else when (userRotation) {
                Surface.ROTATION_90 -> UiAutomation.ROTATION_FREEZE_90
                Surface.ROTATION_180 -> UiAutomation.ROTATION_FREEZE_180
                Surface.ROTATION_270 -> UiAutomation.ROTATION_FREEZE_270
                else -> UiAutomation.ROTATION_FREEZE_0
            },
        )
    }
}
