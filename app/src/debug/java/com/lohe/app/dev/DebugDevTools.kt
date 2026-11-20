package com.lohe.app.dev

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lohe.app.data.importer.PodcastAppImporter
import com.lohe.app.data.local.LoheDatabase
import com.lohe.app.data.local.LohePreferencesRepository
import com.lohe.app.data.repository.LoheRepository
import com.lohe.app.edition.DevTools
import com.lohe.app.playback.LohePlayerController
import com.lohe.app.ui.theme.LoheCyanAccent
import com.lohe.app.ui.theme.LoheElectricBlue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class DebugDevTools(
    context: Context,
    autoStartServer: Boolean = true
) : DevTools {

    private val appContext: Context = context.applicationContext ?: context

    @Volatile
    private var attachedDatabase: LoheDatabase? = null

    @Volatile
    private var attachedPreferences: LohePreferencesRepository? = null

    @Volatile
    private var attachedPlayerController: LohePlayerController? = null

    @Volatile
    private var statusNoticeCallback: ((String) -> Unit)? = null

    val reportManager = DiagnosticsReportManager(appContext)

    val apiServer = LocalDebugApiServer(
        context = appContext,
        databaseProvider = { attachedDatabase ?: LoheDatabase.getInstance(appContext) },
        preferencesProvider = { attachedPreferences ?: LohePreferencesRepository(appContext) },
        playerControllerProvider = { attachedPlayerController },
        eventRecorder = { cat, msg -> reportManager.recordEvent(cat, msg) }
    )

    override val isAvailable: Boolean = true

    // Diagnostics toggle is OFF by default
    private val _diagnosticsEnabled = MutableStateFlow(false)
    override val diagnosticsEnabled: StateFlow<Boolean> = _diagnosticsEnabled.asStateFlow()

    private val _reportFoldersCount = MutableStateFlow(reportManager.listReportFolders().size)
    override val reportFoldersCount: StateFlow<Int> = _reportFoldersCount.asStateFlow()

    private val _lastReportSummary = MutableStateFlow<String?>(null)
    override val lastReportSummary: StateFlow<String?> = _lastReportSummary.asStateFlow()

    override val debugApiPort: Int
        get() = apiServer.boundPort

    init {
        reportManager.recordEvent("startup", "DebugDevTools initialized")
        if (autoStartServer) {
            runCatching { apiServer.start() }
        }
    }

    override fun attachRuntime(
        context: Context,
        scope: CoroutineScope,
        database: LoheDatabase,
        repository: LoheRepository,
        preferencesRepository: LohePreferencesRepository,
        playerController: LohePlayerController,
        podcastAppImporter: PodcastAppImporter,
        onStatusNotice: (String) -> Unit
    ) {
        attachedDatabase = database
        attachedPreferences = preferencesRepository
        attachedPlayerController = playerController
        statusNoticeCallback = onStatusNotice
        _reportFoldersCount.value = reportManager.listReportFolders().size
        if (!apiServer.isRunning) {
            runCatching { apiServer.start() }
        }
    }

    override fun setDiagnosticsEnabled(enabled: Boolean) {
        _diagnosticsEnabled.value = enabled
        reportManager.recordEvent(
            "diagnostics",
            if (enabled) "Diagnostics floating BUG button enabled" else "Diagnostics floating BUG button disabled"
        )
    }

    override fun recordEvent(category: String, message: String) {
        reportManager.recordEvent(category, message)
    }

    override fun captureBugReport(): File {
        val playbackSnapshot = attachedPlayerController?.state?.value
        val dir = reportManager.recordBugReport(playbackSnapshot)
        val folders = reportManager.listReportFolders()
        _reportFoldersCount.value = folders.size
        _lastReportSummary.value = dir.name
        statusNoticeCallback?.invoke("Saved local bug report: ${dir.name} (${folders.size}/5 kept)")
        return dir
    }

    override fun listReportFolders(): List<File> {
        val folders = reportManager.listReportFolders()
        _reportFoldersCount.value = folders.size
        return folders
    }

    @Composable
    override fun DiagnosticsSettingsScreen(modifier: Modifier) {
        val enabled by diagnosticsEnabled.collectAsStateWithLifecycle()
        val reportCount by reportFoldersCount.collectAsStateWithLifecycle()
        val lastSummary by lastReportSummary.collectAsStateWithLifecycle()
        val tokenPath = LocalDebugApiServer.tokenFile(appContext).absolutePath

        LazyColumn(
            contentPadding = PaddingValues(bottom = 172.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.testTag("diagnostics_settings_list")
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Diagnostics & Local Bug Recorder (Debug Only)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Show Floating BUG Button",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Displays a floating BUG button to record recent app events, playback state, and device info with all URLs redacted.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = enabled,
                                onCheckedChange = { setDiagnosticsEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = LoheElectricBlue
                                ),
                                modifier = Modifier.testTag("diagnostics_toggle_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Report folders retained: $reportCount / ${DiagnosticsReportManager.MAX_REPORT_FOLDERS} (local only, never uploaded)",
                            style = MaterialTheme.typography.labelLarge,
                            color = LoheCyanAccent,
                            modifier = Modifier.testTag("diagnostics_report_count_text")
                        )
                        if (!lastSummary.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Last report: $lastSummary",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("diagnostics_last_report_text")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { captureBugReport() },
                            colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("diagnostics_record_report_now_button")
                        ) {
                            Icon(Icons.Filled.BugReport, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Record Local Bug Report Now", color = Color.White)
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Local Debug API (127.0.0.1)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Bound to: 127.0.0.1:${apiServer.boundPort}",
                            style = MaterialTheme.typography.labelLarge,
                            color = LoheCyanAccent,
                            modifier = Modifier.testTag("diagnostics_debug_api_address")
                        )
                        Text(
                            text = "Token file: $tokenPath",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("diagnostics_debug_api_token_path")
                        )
                        Text(
                            text = "• REST: GET /library, GET /queue, GET /playback, GET /settings, POST /playback, POST /import/pocketcasts\n" +
                                "• MCP: POST /mcp (JSON-RPC 2.0; GET returns 405 Method Not Allowed)\n" +
                                "• Max request body size: 1 MiB • Token authenticated before reading request body",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    @Composable
    override fun FloatingBugButtonOverlay(modifier: Modifier) {
        val enabled by diagnosticsEnabled.collectAsStateWithLifecycle()
        if (!enabled) return

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFDC2626),
            shadowElevation = 6.dp,
            modifier = modifier
                .defaultMinSize(minWidth = 76.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.5.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                .clickable { captureBugReport() }
                .testTag("diagnostics_floating_bug_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = "Record Bug Report",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "BUG",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
