package com.lohe.app.edition

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lohe.app.data.importer.PodcastAppImporter
import com.lohe.app.data.local.LoheDatabase
import com.lohe.app.data.local.LohePreferencesRepository
import com.lohe.app.data.repository.LoheRepository
import com.lohe.app.playback.LohePlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

interface DevTools {
    val isAvailable: Boolean
    val diagnosticsEnabled: StateFlow<Boolean>
    val reportFoldersCount: StateFlow<Int>
    val lastReportSummary: StateFlow<String?>
    val debugApiPort: Int

    fun attachRuntime(
        context: Context,
        scope: CoroutineScope,
        database: LoheDatabase,
        repository: LoheRepository,
        preferencesRepository: LohePreferencesRepository,
        playerController: LohePlayerController,
        podcastAppImporter: PodcastAppImporter,
        onStatusNotice: (String) -> Unit
    )

    fun setDiagnosticsEnabled(enabled: Boolean)
    fun recordEvent(category: String, message: String)
    fun captureBugReport(): File?
    fun listReportFolders(): List<File>

    @Composable
    fun DiagnosticsSettingsScreen(modifier: Modifier = Modifier)

    @Composable
    fun FloatingBugButtonOverlay(modifier: Modifier = Modifier)

    companion object {
        @Volatile
        private var registeredInstance: DevTools = NoOpDevTools

        val current: DevTools
            get() = registeredInstance

        fun register(instance: DevTools) {
            registeredInstance = instance
        }

        fun resetToNoOp() {
            registeredInstance = NoOpDevTools
        }
    }
}

object NoOpDevTools : DevTools {
    private val _diagnosticsEnabled = MutableStateFlow(false)
    override val isAvailable: Boolean = false
    override val diagnosticsEnabled: StateFlow<Boolean> = _diagnosticsEnabled.asStateFlow()

    private val _reportFoldersCount = MutableStateFlow(0)
    override val reportFoldersCount: StateFlow<Int> = _reportFoldersCount.asStateFlow()

    private val _lastReportSummary = MutableStateFlow<String?>(null)
    override val lastReportSummary: StateFlow<String?> = _lastReportSummary.asStateFlow()

    override val debugApiPort: Int = -1

    override fun attachRuntime(
        context: Context,
        scope: CoroutineScope,
        database: LoheDatabase,
        repository: LoheRepository,
        preferencesRepository: LohePreferencesRepository,
        playerController: LohePlayerController,
        podcastAppImporter: PodcastAppImporter,
        onStatusNotice: (String) -> Unit
    ) = Unit

    override fun setDiagnosticsEnabled(enabled: Boolean) = Unit
    override fun recordEvent(category: String, message: String) = Unit
    override fun captureBugReport(): File? = null
    override fun listReportFolders(): List<File> = emptyList()

    @Composable
    override fun DiagnosticsSettingsScreen(modifier: Modifier) = Unit

    @Composable
    override fun FloatingBugButtonOverlay(modifier: Modifier) = Unit
}
