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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lohe.app.data.ServiceLocator
import com.lohe.app.dev.BugReportOverlay
import com.lohe.app.dev.DevDiagnostics
import com.lohe.app.ui.SplashScreen
import com.lohe.app.ui.navigation.LoheNavHost
import com.lohe.app.ui.theme.Appearance
import com.lohe.app.ui.theme.LoheTheme
import com.lohe.app.ui.theme.SettingsStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DevDiagnostics.init(this)
        ServiceLocator.init(this)
        com.lohe.app.dev.DevApi.start(this)

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

            // Show the branded splash screen briefly before the main app.
            var showSplash by remember { mutableStateOf(true) }

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

            LoheTheme(appearance = appearance) {
                BugReportOverlay {
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
