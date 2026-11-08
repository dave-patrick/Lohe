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
        // Write the phone's own values back. Unfreezing alone leaves auto-rotate
        // off on some phones (seen on the Galaxy S948U), so it is not enough.
        automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        shell("settings put system user_rotation $userRotation")
        shell("settings put system accelerometer_rotation ${if (autoRotate) 1 else 0}")
    }

    /** Runs a shell command and waits for it to finish (the call itself returns at once). */
    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
    }
}
