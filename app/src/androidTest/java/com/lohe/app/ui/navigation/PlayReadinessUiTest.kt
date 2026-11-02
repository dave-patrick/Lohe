package com.lohe.app.ui.navigation

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lohe.app.MainActivity
import com.lohe.app.ui.player.rememberFloatingPlayerPermissionFlow
import com.lohe.app.ui.theme.LoheTheme
import com.lohe.app.ui.theme.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayReadinessUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val store by lazy { SettingsStore(context) }
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setup() {
        assertTrue("Use only the isolated audit app", context.packageName.endsWith(".audit"))
        runBlocking { store.setFloatingPlayerEnabled(false) }
        context.getSharedPreferences("floating-player-setup", 0).edit().remove("notification-prompt-handled").commit()
    }
    @After fun cleanup() {
        runBlocking { store.setFloatingPlayerEnabled(false) }
        scenario?.close()
        shell("appops set " + context.packageName + " SYSTEM_ALERT_WINDOW default")
    }
    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }
    private fun permissionFixture(overlay: Boolean) {
        shell("appops set " + context.packageName + " SYSTEM_ALERT_WINDOW " + if (overlay) "allow" else "deny")
        if (Build.VERSION.SDK_INT >= 33) {
            shell("pm revoke " + context.packageName + " android.permission.POST_NOTIFICATIONS")
            shell("pm clear-permission-flags " + context.packageName + " android.permission.POST_NOTIFICATIONS user-set user-fixed")
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity -> activity.setContent {
            LoheTheme {
                val enable = rememberFloatingPlayerPermissionFlow(store)
                Box(Modifier.padding(top = 64.dp)) {
                    Button(onClick = enable) { Text("Enable floating controls") }
                }
            }
        } }
        waitFor("Enable floating controls")
    }
    @Test fun overlayRationaleCancelLeavesControlsOff() {
        permissionFixture(false)
        tap("Enable floating controls")
        waitFor("Show controls over other apps?")
        tap("Not now")
        await { find("Show controls over other apps?") == null }
        assertFalse(runBlocking { store.settings.first().floatingPlayerEnabled })
    }
    @Test fun notificationNotNowEnablesControlsAndDoesNotPromptAgain() {
        if (Build.VERSION.SDK_INT < 33) return
        permissionFixture(true)
        tap("Enable floating controls")
        waitFor("Allow floating-player notifications?")
        tap("Not now")
        await { runBlocking { store.settings.first().floatingPlayerEnabled } }
        assertTrue(context.getSharedPreferences("floating-player-setup", 0).getBoolean("notification-prompt-handled", false))
        runBlocking { store.setFloatingPlayerEnabled(false) }
        tap("Enable floating controls")
        await { runBlocking { store.settings.first().floatingPlayerEnabled } }
        assertNull(find("Allow floating-player notifications?"))
    }
    @Test fun androidNotificationDenialStillEnablesControls() {
        if (Build.VERSION.SDK_INT < 33) return
        permissionFixture(true)
        tap("Enable floating controls")
        tap("Allow notifications")
        await { find("Don't allow") != null || find("Don’t allow") != null }
        tap(if (find("Don't allow") != null) "Don't allow" else "Don’t allow")
        await { runBlocking { store.settings.first().floatingPlayerEnabled } }
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS))
    }
    @Test fun offlinePrivacyPolicyOpensAndClosesFromAbout() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity -> activity.setContent { LoheTheme { AboutScreen(onBack = {}) } } }
        tap("Privacy policy")
        waitFor("Your library and preferences")
        assertTrue(findContaining("atomic22industries@gmail.com") != null)
        tap("Close")
        await { find("Close") == null }
        waitFor("Privacy policy")
    }
    private fun find(text: String): AccessibilityNodeInfo? = search { it.text?.toString() == text || it.contentDescription?.toString() == text }
    private fun findContaining(text: String): AccessibilityNodeInfo? = search { it.text?.toString()?.contains(text) == true }
    private fun search(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (predicate(node)) return node
            for (index in 0 until node.childCount) visit(node.getChild(index))?.let { return it }
            return null
        }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }
    private fun await(predicate: () -> Boolean) {
        repeat(150) {
            instrumentation.waitForIdleSync()
            if (predicate()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for Play readiness UI")
    }
    private fun waitFor(text: String): AccessibilityNodeInfo {
        await { find(text) != null }
        return find(text)!!
    }
    private fun tap(text: String) {
        val bounds = Rect().also(waitFor(text)::getBoundsInScreen)
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
    }
}
