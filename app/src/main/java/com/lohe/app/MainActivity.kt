package com.lohe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lohe.app.data.ServiceLocator
import com.lohe.app.data.FeedRefreshScheduler
import com.lohe.app.dev.BugReportOverlay
import com.lohe.app.dev.DevDiagnostics
import com.lohe.app.ui.SplashScreen
import com.lohe.app.ui.navigation.LoheNavHost
import com.lohe.app.ui.theme.Appearance
import com.lohe.app.ui.theme.LoheTheme
import com.lohe.app.ui.theme.SettingsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lohe.app.playback.FloatingPlaybackService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DevDiagnostics.init(this)
        ServiceLocator.init(this)
        com.lohe.app.dev.DevApi.start(this)
        // Check for new episodes whenever the app comes to the front, but not
        // more than once every 15 minutes so flipping between apps stays cheap.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val prefs = getSharedPreferences("feed_refresh", MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val last = prefs.getLong("last_open_refresh", 0L)
                if (now - last >= 15 * 60_000L || now < last) {
                    prefs.edit().putLong("last_open_refresh", now).apply()
                    runCatching { ServiceLocator.subscriptionRepository().refreshAllFeeds() }
                }
            }
        }
        lifecycleScope.launch {
            ServiceLocator.settingsStore().settings.map { it.debugButtonEnabled }.distinctUntilChanged().collect {
                DevDiagnostics.recordingEnabled = BuildConfig.DEBUG || it
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                ServiceLocator.settingsStore().settings.map { it.floatingPlayerEnabled }.distinctUntilChanged().collect {
                    if (it && !android.provider.Settings.canDrawOverlays(this@MainActivity)) {
                        ServiceLocator.settingsStore().setFloatingPlayerEnabled(false)
                    }
                    FloatingPlaybackService.sync(this@MainActivity, it)
                }
            }
        }

        setContent {
            val context = LocalContext.current
            val settings = remember { SettingsStore(context.applicationContext) }
            val scope = rememberCoroutineScope()

            // Dark is the product default and deliberately does not follow the
            // system setting. Held above navigation so the toggle swaps every
            // screen at once, and written through so it survives process death
            // rather than only rotation.
            var appearance by remember { mutableStateOf(Appearance.DARK) }
            val persisted by settings.appearance.collectAsState(initial = Appearance.DARK)
            val playbackSettings by settings.settings.collectAsState(initial = com.lohe.app.ui.theme.PlaybackSettings())

            // Show the branded splash screen briefly before the main app.
            // rememberSaveable (not remember) so the "already shown" flag
            // survives a rotation instead of resetting and replaying it.
            var showSplash by rememberSaveable { mutableStateOf(true) }

            LaunchedEffect(persisted) {
                appearance = persisted
            }

            // A download can go missing without the app being told — cleared
            // storage, a restore that brought back the database but not the
            // files. Left alone, the row still claims the episode is available
            // offline and the player tries to open a path that is not there.
            LaunchedEffect(Unit) {
                runCatching { ServiceLocator.downloadManager().repairMissingDownloads() }
            }

            LaunchedEffect(Unit) {
                settings.settings
                    .map { it.refreshIntervalMinutes }
                    .distinctUntilChanged()
                    .collect { FeedRefreshScheduler.schedule(context, it) }
            }

            LoheTheme(appearance = appearance) {
                BugReportOverlay(enabled = playbackSettings.debugButtonEnabled) {
                    if (showSplash) {
                        SplashScreen(onFinished = {
                            DevDiagnostics.log("UI", "SplashScreen finished")
                            showSplash = false
                        })
                    } else {
                        LoheNavHost(
                            appearance = appearance,
                            onToggleAppearance = {
                                val next = when (appearance) {
                                    Appearance.DARK -> Appearance.LIGHT
                                    Appearance.LIGHT -> Appearance.DARK
                                }
                                DevDiagnostics.recordAction("Toggle appearance -> $next")
                                // Update in memory first so the toggle feels instant,
                                // then persist; a failed write must not block the change.
                                appearance = next
                                scope.launch { settings.setAppearance(next) }
                            },
                        )
                    }
                }
            }
        }
    }
}
