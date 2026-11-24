package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.edition.Edition
import com.example.ui.LoheViewModel
import com.example.ui.PrimaryTab
import com.example.ui.SettingsSubScreen
import com.example.ui.components.EpisodeNotesBottomSheet
import com.example.ui.components.FloatingMiniPlayerPill
import com.example.ui.components.FullPlayerModalDialog
import com.example.ui.components.LoheFloatingBottomNavBar
import com.example.ui.components.LoheNavigationRail
import com.example.ui.components.LoheToastOverlay
import com.example.ui.components.MiniPlayerBar
import com.example.ui.screens.DiscoverTabScreen
import com.example.ui.screens.HomeTabScreen
import com.example.ui.screens.PlaylistsTabScreen
import com.example.ui.screens.PodcastsTabScreen
import com.example.ui.screens.SettingsContainerScreen
import com.example.ui.screens.UpNextTabScreen
import com.example.ui.theme.LoheBackgroundDark
import com.example.ui.theme.LoheCyanAccent
import com.example.ui.theme.LoheTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: LoheViewModel = viewModel()
            LoheAppRoot(viewModel = viewModel)
        }
    }
}

@Composable
fun LoheAppRoot(viewModel: LoheViewModel) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val podcasts by viewModel.subscribedPodcasts.collectAsStateWithLifecycle()
    val activeEpisodes by viewModel.activeEpisodes.collectAsStateWithLifecycle()
    val allEpisodes by viewModel.allEpisodes.collectAsStateWithLifecycle()
    val upNextEpisodes by viewModel.upNextEpisodes.collectAsStateWithLifecycle()
    val inProgressEpisodes by viewModel.inProgressEpisodes.collectAsStateWithLifecycle()
    val customPlaylists by viewModel.customPlaylists.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedPodcastId by viewModel.selectedPodcastId.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val activeSettingsScreen by viewModel.activeSettingsScreen.collectAsStateWithLifecycle()
    val isFullPlayerOpen by viewModel.isFullPlayerOpen.collectAsStateWithLifecycle()
    val episodeNotesTarget by viewModel.episodeNotesSheetTarget.collectAsStateWithLifecycle()
    val showProUpgradeSheet by viewModel.showProUpgradeSheet.collectAsStateWithLifecycle()
    val statusBannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolderFilter.collectAsStateWithLifecycle()
    val discoverQuery by viewModel.discoverQuery.collectAsStateWithLifecycle()
    val discoverResults by viewModel.discoverResults.collectAsStateWithLifecycle()
    val isDiscoverSearching by viewModel.isDiscoverSearching.collectAsStateWithLifecycle()
    val previewTargetItem by viewModel.previewTargetItem.collectAsStateWithLifecycle()
    val previewEpisodes by viewModel.previewEpisodes.collectAsStateWithLifecycle()
    val isPreviewLoading by viewModel.isPreviewLoading.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val billingState by viewModel.billingState.collectAsStateWithLifecycle()
    val isCloudSyncBusy by viewModel.isCloudSyncBusy.collectAsStateWithLifecycle()
    val pendingDriveAuthIntent by viewModel.pendingDriveAuthIntent.collectAsStateWithLifecycle()
    val activeChapters by viewModel.activeEpisodeChapters.collectAsStateWithLifecycle()
    val activeTranscriptCues by viewModel.activeEpisodeTranscriptCues.collectAsStateWithLifecycle()
    val activeBookmarks by viewModel.activeEpisodeBookmarks.collectAsStateWithLifecycle()
    val notesChapters by viewModel.notesChapters.collectAsStateWithLifecycle()
    val notesTranscriptCues by viewModel.notesTranscriptCues.collectAsStateWithLifecycle()
    val notesBookmarks by viewModel.notesBookmarks.collectAsStateWithLifecycle()
    val isNotesExtrasLoading by viewModel.isNotesExtrasLoading.collectAsStateWithLifecycle()
    val storageBreakdown by viewModel.storageBreakdown.collectAsStateWithLifecycle()
    val importProgressState by viewModel.importProgressState.collectAsStateWithLifecycle()
    val lastImportSummary by viewModel.lastImportSummary.collectAsStateWithLifecycle()
    val downloadingEpisodeIds by viewModel.downloadingEpisodeIds.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Google Drive OAuth AppData Consent Launcher
    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDriveAuthorizationResult(result.resultCode, result.data)
    }

    LaunchedEffect(pendingDriveAuthIntent) {
        val pendingIntent = pendingDriveAuthIntent ?: return@LaunchedEffect
        runCatching {
            driveAuthLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            )
        }.onFailure {
            viewModel.clearPendingDriveAuthIntent()
            viewModel.postStatusMessage("Google Drive authorization UI unavailable here. Use SAF Drive Import/Export.")
        }
    }

    // Android 13+ (API 33+) Runtime Notification Permission for Media3 foreground controls
    var requestedNotificationPerm by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Granted or denied gracefully; playback continues either way */ }

    val ensureNotificationPermissionIfNeeded: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !requestedNotificationPerm) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestedNotificationPerm = true
                runCatching {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    var pendingCloudSafProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSafLinkRestore by rememberSaveable { mutableStateOf(false) }

    // Android Storage Access Framework (SAF) Launchers for Personal OPML & JSON Backup Import/Export
    val importOpmlOrJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val provider = pendingCloudSafProvider?.let { com.example.data.local.CloudSyncProvider.fromName(it) }
        pendingCloudSafProvider = null
        if (uri != null) {
            viewModel.importFromUri(uri)
            if (provider != null) {
                viewModel.recordCloudProviderSafSync(provider, restored = true)
            }
        }
    }

    val linkCloudSyncFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val provider = pendingCloudSafProvider?.let { com.example.data.local.CloudSyncProvider.fromName(it) }
            ?: settings.cloudSyncProvider
        val restoreOnly = pendingSafLinkRestore
        pendingCloudSafProvider = null
        pendingSafLinkRestore = false
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            viewModel.linkSafSyncFile(
                uri = uri,
                provider = provider,
                syncAfterLink = true,
                restoreOnly = restoreOnly
            )
        }
    }

    val createCloudSyncFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val provider = pendingCloudSafProvider?.let { com.example.data.local.CloudSyncProvider.fromName(it) }
            ?: settings.cloudSyncProvider
        pendingCloudSafProvider = null
        pendingSafLinkRestore = false
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            viewModel.linkSafSyncFile(
                uri = uri,
                provider = provider,
                syncAfterLink = true,
                restoreOnly = false
            )
        }
    }

    val exportOpmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-opml")
    ) { uri ->
        if (uri != null) {
            viewModel.exportToUri(uri, asJson = false)
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val provider = pendingCloudSafProvider?.let { com.example.data.local.CloudSyncProvider.fromName(it) }
        pendingCloudSafProvider = null
        if (uri != null) {
            viewModel.exportToUri(uri, asJson = true)
            if (provider != null) {
                viewModel.recordCloudProviderSafSync(provider, restored = false)
            }
        }
    }

    val exportDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-sqlite3")
    ) { uri ->
        if (uri != null) {
            viewModel.exportDatabaseToUri(uri)
        }
    }

    val launchImportPicker: () -> Unit = {
        pendingCloudSafProvider = null
        runCatching {
            importOpmlOrJsonLauncher.launch(
                arrayOf("*/*")
            )
        }.onFailure {
            viewModel.postStatusMessage("Storage picker unavailable in headless environment")
        }
    }

    val launchLinkSafSyncPicker: (com.example.data.local.CloudSyncProvider, Boolean) -> Unit = { provider, restoreOnly ->
        pendingCloudSafProvider = provider.name
        pendingSafLinkRestore = restoreOnly
        runCatching {
            linkCloudSyncFileLauncher.launch(arrayOf("application/json", "*/*"))
        }.onFailure {
            pendingCloudSafProvider = null
            pendingSafLinkRestore = false
            viewModel.postStatusMessage("${provider.title} picker unavailable in headless environment")
        }
    }

    val launchCreateSafSyncPicker: (com.example.data.local.CloudSyncProvider) -> Unit = { provider ->
        pendingCloudSafProvider = provider.name
        pendingSafLinkRestore = false
        runCatching {
            createCloudSyncFileLauncher.launch("lohe_cloud_sync.json")
        }.onFailure {
            pendingCloudSafProvider = null
            viewModel.postStatusMessage("${provider.title} picker unavailable in headless environment")
        }
    }

    val launchExportOpmlPicker: () -> Unit = {
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        runCatching {
            exportOpmlLauncher.launch("lohe_subscriptions_$stamp.opml")
        }.onFailure {
            viewModel.postStatusMessage("Export picker unavailable in headless environment")
        }
    }

    val launchExportJsonPicker: () -> Unit = {
        pendingCloudSafProvider = null
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        runCatching {
            exportJsonLauncher.launch("lohe_library_backup_$stamp.json")
        }.onFailure {
            viewModel.postStatusMessage("Export picker unavailable in headless environment")
        }
    }

    val launchExportDbPicker: () -> Unit = {
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        runCatching {
            exportDbLauncher.launch("lohe_database_$stamp.db")
        }.onFailure {
            viewModel.postStatusMessage("Database export picker unavailable in headless environment")
        }
    }

    // Handle back navigation on secondary tabs when not inside detail or settings screens
    BackHandler(
        enabled = activeSettingsScreen == null &&
            selectedPodcastId == null &&
            selectedPlaylistId == null &&
            selectedTab != PrimaryTab.HOME
    ) {
        viewModel.selectTab(PrimaryTab.HOME)
    }

    val nextUpAfterCurrent = upNextEpisodes.firstOrNull { it.id != playbackState.currentEpisode?.id }
    var hasPlayedHomeFlight by rememberSaveable { mutableStateOf(false) }

    // Cross-App System Overlay Lifecycle: spawn draggable WindowManager pill when leaving app (if granted)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(
        lifecycleOwner,
        settings.floatingMiniPlayerEnabled,
        settings.floatingButtonSizeDp,
        settings.floatingTransparencyPct,
        playbackState.currentEpisode
    ) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    val episode = playbackState.currentEpisode
                    if (
                        Edition.HAS_PRO &&
                        settings.floatingMiniPlayerEnabled &&
                        episode != null &&
                        Settings.canDrawOverlays(context)
                    ) {
                        viewModel.proFeatures.startOrUpdateFloatingOverlay(
                            context = context,
                            sizeDp = settings.floatingButtonSizeDp,
                            transparencyPct = settings.floatingTransparencyPct,
                            skipBackSec = settings.skipBackSeconds,
                            skipForwardSec = settings.skipForwardSeconds,
                            episodeTitle = episode.title,
                            podcastTitle = episode.podcastTitle,
                            isPlaying = playbackState.isPlaying
                        )
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.proFeatures.stopFloatingOverlay(context)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(settings.floatingMiniPlayerEnabled, playbackState.currentEpisode) {
        if (!Edition.HAS_PRO || !settings.floatingMiniPlayerEnabled || playbackState.currentEpisode == null) {
            viewModel.proFeatures.stopFloatingOverlay(context)
        }
    }

    LoheTheme(themePreference = settings.themePreference) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { _ ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                val isExpandedLayout = maxWidth >= 600.dp

                Row(modifier = Modifier.fillMaxSize()) {
                    if (isExpandedLayout && activeSettingsScreen == null) {
                        LoheNavigationRail(
                            selectedTab = selectedTab,
                            isSettingsOpen = false,
                            upNextCount = upNextEpisodes.size,
                            onSelectTab = viewModel::selectTab
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        val effectiveFloatingPlayer = Edition.HAS_PRO && settings.floatingMiniPlayerEnabled
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Main screen content strictly ABOVE docked mini player & bottom nav bar
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                val currentSettings = activeSettingsScreen
                                if (currentSettings != null) {
                                    SettingsContainerScreen(
                                        currentSubScreen = currentSettings,
                                        settings = settings,
                                        billingState = billingState,
                                        isCloudSyncBusy = isCloudSyncBusy,
                                        downloadedEpisodesCount = activeEpisodes.count { it.isDownloaded },
                                        episodes = allEpisodes,
                                        storageBreakdown = storageBreakdown,
                                        importProgressState = importProgressState,
                                        lastImportSummary = lastImportSummary,
                                        onNavigateSubScreen = { viewModel.openSettings(it) },
                                        onBack = { viewModel.navigateBackInSettings() },
                                        onThemeChange = viewModel::setThemePreference,
                                        onReducedMotionChange = viewModel::setReducedMotion,
                                        onUpNextIndicatorEnabledChange = viewModel::setUpNextIndicatorEnabled,
                                        onIndicatorStyleChange = viewModel::setQueueIndicatorStyle,
                                        onIndicatorSizeChange = viewModel::setQueueIndicatorSize,
                                        onPresetColorChange = viewModel::setQueuePresetColor,
                                        onCustomHexColorChange = viewModel::setCustomHexColor,
                                        onToggleHomeCard = viewModel::setHomeCardEnabled,
                                        onResetHomeCards = viewModel::resetHomeCardsToDefault,
                                        onListeningHistoryRangeChange = viewModel::setListeningHistoryRange,
                                        onSpeedChange = viewModel::setPlaybackSpeed,
                                        onSkipDurationsChange = viewModel::setSkipDurations,
                                        onSkipSilenceChange = viewModel::setSkipSilence,
                                        onVolumeBoostChange = viewModel::setVolumeBoostDb,
                                        onContinuousPlaybackChange = viewModel::setContinuousPlayback,
                                        onPlaybackExtraOptionsChange = viewModel::setPlaybackExtraOptions,
                                        onSleepShakeExtendChange = viewModel::setSleepShakeToExtend,
                                        onSwipeActionsChange = viewModel::setSwipeActions,
                                        onFloatingPlayerChange = viewModel::setFloatingMiniPlayer,
                                        onFloatingAppearanceChange = viewModel::setFloatingAppearance,
                                        onAutoArchivePolicyChange = viewModel::setAutoArchivePolicy,
                                        onKeepStarredChange = viewModel::setKeepStarredExcluded,
                                        onEpisodeLimitChange = viewModel::setEpisodeLimitPerShow,
                                        onArchivePlayedNow = viewModel::archiveAllPlayedNow,
                                        onAutoDownloadChange = viewModel::setAutoDownloadSettings,
                                        onNewEpisodeNotificationsChange = viewModel::setNewEpisodeNotificationsEnabled,
                                        onBackgroundSyncPolicyChange = viewModel::setBackgroundSyncPolicy,
                                        onStorageCleanupPolicyChange = viewModel::setStorageCleanupPolicy,
                                        onClearDownloadsForPodcast = viewModel::clearDownloadsForPodcast,
                                        onClearDownloads = viewModel::clearCachedDownloads,
                                        onRefreshFeedsNow = viewModel::refreshSubscribedFeedsNow,
                                        onImportOpmlOrJson = launchImportPicker,
                                        onExportOpml = launchExportOpmlPicker,
                                        onExportJsonBackup = launchExportJsonPicker,
                                        onExportDatabase = launchExportDbPicker,
                                        onSyncToGoogleDrive = { viewModel.syncWithGoogleDrive(restore = false) },
                                        onRestoreFromGoogleDrive = { viewModel.syncWithGoogleDrive(restore = true) },
                                        onCloudProviderChange = viewModel::setCloudSyncProvider,
                                        onSaveWebDavConfig = viewModel::saveWebDavConfig,
                                        onSyncWebDav = { viewModel.syncWithWebDav(restore = false) },
                                        onRestoreWebDav = { viewModel.syncWithWebDav(restore = true) },
                                        onSyncCloudProviderSaf = { provider ->
                                            if (settings.safSyncUri.isNotBlank()) {
                                                viewModel.syncWithSafDrive(provider, restore = false)
                                            } else {
                                                launchCreateSafSyncPicker(provider)
                                            }
                                        },
                                        onRestoreCloudProviderSaf = { provider ->
                                            if (settings.safSyncUri.isNotBlank()) {
                                                viewModel.syncWithSafDrive(provider, restore = true)
                                            } else {
                                                launchLinkSafSyncPicker(provider, true)
                                            }
                                        },
                                        onLinkSafSyncFile = { provider -> launchLinkSafSyncPicker(provider, false) },
                                        onCreateSafSyncFile = { provider -> launchCreateSafSyncPicker(provider) },
                                        onUnlinkSafSyncFile = viewModel::unlinkSafSyncFile,
                                        onToggleCloudAutoBackup = viewModel::setCloudSyncAutoBackup,
                                        onOpenProUpgradeModal = { viewModel.setShowProUpgradeSheet(true) },
                                        onRestoreProPurchases = viewModel::restoreProPurchases,
                                        onToastSettingsChange = viewModel::setToastSettings,
                                        onPreviewToast = viewModel::previewToastNotification
                                    )
                                } else {
                                    when (selectedTab) {
                                        PrimaryTab.HOME -> HomeTabScreen(
                                            settings = settings,
                                            podcasts = podcasts,
                                            playlists = customPlaylists,
                                            inProgressEpisodes = inProgressEpisodes,
                                            activeEpisodes = activeEpisodes,
                                            allEpisodes = allEpisodes,
                                            upNextEpisodes = upNextEpisodes,
                                            playbackState = playbackState,
                                            hasPlayedHomeFlight = hasPlayedHomeFlight,
                                            onHomeFlightFinished = { hasPlayedHomeFlight = true },
                                            onOpenSettings = { viewModel.openSettings(SettingsSubScreen.OVERVIEW) },
                                            onNavigateToDiscover = { viewModel.selectTab(PrimaryTab.DISCOVER) },
                                            onNavigateToPodcasts = { viewModel.selectTab(PrimaryTab.PODCASTS) },
                                            onNavigateToPlaylists = { viewModel.selectTab(PrimaryTab.PLAYLISTS) },
                                            onNavigateToUpNext = { viewModel.selectTab(PrimaryTab.UP_NEXT) },
                                            onSelectPodcast = viewModel::openPodcastFromHome,
                                            onCustomizeHomeCards = { viewModel.openSettings(SettingsSubScreen.HOME_CARDS) },
                                            onPlayEpisode = { ep ->
                                                ensureNotificationPermissionIfNeeded()
                                                viewModel.playEpisode(ep)
                                            },
                                            onToggleUpNext = viewModel::toggleEpisodeUpNext,
                                            onAddToUpNext = viewModel::addEpisodeToUpNext,
                                            onSwipeEpisodeAction = viewModel::executeEpisodeSwipeAction,
                                            onToggleStarred = viewModel::toggleStarred,
                                            onOpenNotes = { viewModel.openEpisodeNotes(it) },
                                            onImportOpmlOrJson = launchImportPicker,
                                            onExportJsonBackup = launchExportJsonPicker
                                        )

                                        PrimaryTab.PODCASTS -> PodcastsTabScreen(
                                            settings = settings,
                                            podcasts = podcasts,
                                            episodes = activeEpisodes,
                                            upNextEpisodes = upNextEpisodes,
                                            selectedPodcastId = selectedPodcastId,
                                            playbackState = playbackState,
                                            onSelectPodcast = viewModel::openPodcastDetail,
                                            onOpenSettings = { viewModel.openSettings(SettingsSubScreen.OVERVIEW) },
                                            onUpdateAutoQueue = viewModel::updatePodcastAutoQueue,
                                            onUnsubscribePodcast = viewModel::unsubscribePodcast,
                                            onImportOpml = launchImportPicker,
                                            onAddRssUrl = viewModel::subscribeByRssUrl,
                                            onPlayEpisode = { ep ->
                                                ensureNotificationPermissionIfNeeded()
                                                viewModel.playEpisode(ep)
                                            },
                                            onToggleUpNext = viewModel::toggleEpisodeUpNext,
                                            onToggleStarred = viewModel::toggleStarred,
                                            onToggleDownloaded = viewModel::toggleDownloaded,
                                            onMarkEpisodePlayed = viewModel::markEpisodePlayed,
                                            onResetEpisodeProgress = viewModel::resetEpisodeProgress,
                                            onOpenNotes = { viewModel.openEpisodeNotes(it) },
                                            onRefreshFeeds = viewModel::refreshSubscribedFeedsNow,
                                            onUpdateAudioOverrides = viewModel::updatePodcastAudioOverrides,
                                            onPlayNext = viewModel::playEpisodeNext,
                                            onToggleNotifications = viewModel::updatePodcastNotifications,
                                            onAddToUpNext = viewModel::addEpisodeToUpNext,
                                            onSwipeEpisodeAction = viewModel::executeEpisodeSwipeAction,
                                            onOpenProUpgradeModal = { viewModel.setShowProUpgradeSheet(true) },
                                            onSortChange = viewModel::setPodcastSortOption,
                                            onFilterChange = viewModel::setPodcastFilterOption,
                                            onBatchEpisodeAction = viewModel::executeBatchEpisodeAction,
                                            onBatchPodcastAction = viewModel::executeBatchPodcastAction
                                        )

                                        PrimaryTab.DISCOVER -> DiscoverTabScreen(
                                            query = discoverQuery,
                                            catalog = discoverResults,
                                            isSearching = isDiscoverSearching,
                                            subscribedPodcasts = podcasts,
                                            previewItem = previewTargetItem,
                                            previewEpisodes = previewEpisodes,
                                            isPreviewLoading = isPreviewLoading,
                                            onSearchDiscover = viewModel::searchDiscover,
                                            onBrowseCategory = viewModel::browseDiscoverCategory,
                                            onOpenPreview = viewModel::openDiscoverPreview,
                                            onSubscribeCatalog = viewModel::subscribeToDiscoverItem,
                                            onSubscribeRssUrl = viewModel::subscribeByRssUrl,
                                            onPlayPreviewEpisode = { ep ->
                                                ensureNotificationPermissionIfNeeded()
                                                viewModel.openDiscoverPreview(null)
                                                viewModel.playEpisode(ep)
                                            },
                                            onOpenNotes = { viewModel.openEpisodeNotes(it) },
                                            onImportOpmlOrJson = launchImportPicker,
                                            onOpenSettings = { viewModel.openSettings(SettingsSubScreen.OVERVIEW) },
                                            isProUnlocked = settings.isProUnlocked,
                                            onOpenProUpgradeModal = { viewModel.setShowProUpgradeSheet(true) }
                                        )

                                        PrimaryTab.PLAYLISTS -> PlaylistsTabScreen(
                                            settings = settings,
                                            playlists = customPlaylists,
                                            podcasts = podcasts,
                                            episodes = activeEpisodes,
                                            upNextEpisodes = upNextEpisodes,
                                            selectedPlaylistId = selectedPlaylistId,
                                            selectedFolder = selectedFolder,
                                            playbackState = playbackState,
                                            resolvePlaylistEpisodes = viewModel::resolvePlaylistEpisodes,
                                            onSelectPlaylist = viewModel::openPlaylistDetail,
                                            onSelectFolder = viewModel::selectFolderFilter,
                                            onToggleGridView = viewModel::setPlaylistsGridView,
                                            onCreatePlaylist = viewModel::createPlaylist,
                                            onDeletePlaylist = viewModel::deletePlaylist,
                                            onTogglePinned = viewModel::togglePlaylistPinned,
                                            onReorderPlaylists = viewModel::reorderPlaylists,
                                            onUpdateFolder = viewModel::updatePlaylistFolder,
                                            onUpdateAssignedPodcasts = viewModel::updatePlaylistAssignedPodcasts,
                                            onQueueEpisodes = { eps -> viewModel.queueAllEpisodes(eps, false) },
                                            onPlayEpisode = { ep ->
                                                ensureNotificationPermissionIfNeeded()
                                                viewModel.playEpisode(ep)
                                            },
                                            onToggleUpNext = viewModel::toggleEpisodeUpNext,
                                            onToggleStarred = viewModel::toggleStarred,
                                            onToggleDownloaded = viewModel::toggleDownloaded,
                                            onMarkPlayed = viewModel::markEpisodePlayed,
                                            onOpenNotes = { viewModel.openEpisodeNotes(it) },
                                            onOpenSettings = { viewModel.openSettings(SettingsSubScreen.OVERVIEW) },
                                            onAddToUpNext = viewModel::addEpisodeToUpNext,
                                            onSwipeEpisodeAction = viewModel::executeEpisodeSwipeAction,
                                            onBatchEpisodeAction = viewModel::executeBatchEpisodeAction,
                                            onBatchPlaylistAction = viewModel::executeBatchPlaylistAction
                                        )

                                        PrimaryTab.UP_NEXT -> UpNextTabScreen(
                                            settings = settings,
                                            upNextEpisodes = upNextEpisodes,
                                            playbackState = playbackState,
                                            onOpenSettings = { viewModel.openSettings(SettingsSubScreen.OVERVIEW) },
                                            onBrowsePodcasts = { viewModel.selectTab(PrimaryTab.PODCASTS) },
                                            onPlayEpisode = { ep ->
                                                ensureNotificationPermissionIfNeeded()
                                                viewModel.playEpisode(ep)
                                            },
                                            onMoveEpisode = viewModel::moveEpisodeInQueue,
                                            onReorderQueue = viewModel::reorderUpNextQueue,
                                            onMoveEpisodeEdge = viewModel::moveEpisodeToQueueEdge,
                                            onMarkPlayed = viewModel::markEpisodePlayed,
                                            onRemoveFromQueue = { viewModel.toggleEpisodeUpNext(it, false) },
                                            onClearQueue = viewModel::clearUpNextQueue,
                                            onOpenNotes = { viewModel.openEpisodeNotes(it) },
                                            onBatchUpNextAction = viewModel::executeBatchEpisodeAction
                                        )
                                    }
                                }

                                // Floating toast notification overlay positioned strictly above bottom dock
                                LoheToastOverlay(
                                    message = statusBannerMessage,
                                    style = settings.toastStyle,
                                    placement = settings.toastPlacement,
                                    reducedMotion = settings.reducedMotion,
                                    onDismiss = viewModel::clearStatusBanner
                                )
                            }

                            // Docked Bottom Chrome (never overlays page content)
                            if (activeSettingsScreen == null) {
                                if (isExpandedLayout) {
                                    if (playbackState.currentEpisode != null && !effectiveFloatingPlayer) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .navigationBarsPadding()
                                        ) {
                                            MiniPlayerBar(
                                                playbackState = playbackState,
                                                nextUpEpisode = nextUpAfterCurrent,
                                                settings = settings,
                                                onTogglePlayPause = viewModel::togglePlayPause,
                                                onSkipBack = viewModel::skipBack,
                                                onSkipForward = viewModel::skipForward,
                                                onOpenFullPlayer = { viewModel.setFullPlayerOpen(true) },
                                                onOpenNotes = { viewModel.openEpisodeNotes(it) }
                                            )
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("bottom_nav_scrim_container")
                                    ) {
                                        if (playbackState.currentEpisode != null && !effectiveFloatingPlayer) {
                                            MiniPlayerBar(
                                                playbackState = playbackState,
                                                nextUpEpisode = nextUpAfterCurrent,
                                                settings = settings,
                                                onTogglePlayPause = viewModel::togglePlayPause,
                                                onSkipBack = viewModel::skipBack,
                                                onSkipForward = viewModel::skipForward,
                                                onOpenFullPlayer = { viewModel.setFullPlayerOpen(true) },
                                                onOpenNotes = { viewModel.openEpisodeNotes(it) }
                                            )
                                        }

                                        LoheFloatingBottomNavBar(
                                            selectedTab = selectedTab,
                                            isSettingsOpen = false,
                                            upNextCount = upNextEpisodes.size,
                                            onSelectTab = viewModel::selectTab
                                        )
                                    }
                                }
                            }
                        }

                        // In-App Draggable Floating Mini-Player Pill (Pro feature, only when explicitly enabled)
                        if (activeSettingsScreen == null && effectiveFloatingPlayer && playbackState.currentEpisode != null) {
                            FloatingMiniPlayerPill(
                                playbackState = playbackState,
                                sizeDp = settings.floatingButtonSizeDp,
                                transparencyPct = settings.floatingTransparencyPct,
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onOpenFullPlayer = { viewModel.setFullPlayerOpen(true) },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(
                                        end = if (isExpandedLayout) 24.dp else 16.dp,
                                        bottom = if (isExpandedLayout) 24.dp else 72.dp
                                    )
                            )
                        }

                        // Developer Diagnostics Floating BUG Button (no-op in release builds)
                        viewModel.devTools.FloatingBugButtonOverlay(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 60.dp, end = 16.dp)
                        )
                    }
                }
            }
        }

        if (isFullPlayerOpen && playbackState.currentEpisode != null) {
            FullPlayerModalDialog(
                playbackState = playbackState,
                settings = settings,
                onDismiss = { viewModel.setFullPlayerOpen(false) },
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeekTo = viewModel::seekTo,
                onSkipBack = viewModel::skipBack,
                onSkipForward = viewModel::skipForward,
                onSpeedChange = viewModel::setPlaybackSpeed,
                onVolumeBoostChange = viewModel::setVolumeBoostDb,
                onSkipSilenceChange = viewModel::setSkipSilence,
                onSleepTimerSelect = viewModel::setSleepTimerMinutes,
                onToggleStarred = viewModel::toggleStarred,
                onOpenNotes = { ep -> viewModel.openEpisodeNotes(ep) },
                activeChapters = activeChapters,
                activeTranscriptCues = activeTranscriptCues,
                activeBookmarks = activeBookmarks,
                sleepShakeToExtend = settings.sleepShakeToExtendEnabled,
                onSetSleepEndOfEpisode = viewModel::setSleepAtEndOfEpisode,
                onToggleSleepShakeExtend = viewModel::setSleepShakeToExtend,
                onSaveBookmark = viewModel::addBookmarkAtCurrentPosition,
                onDeleteBookmark = viewModel::deleteBookmark
            )
        }

        episodeNotesTarget?.let { targetEp ->
            val liveEp = activeEpisodes.find { it.id == targetEp.id }
                ?: allEpisodes.find { it.id == targetEp.id }
                ?: previewEpisodes.find { it.id == targetEp.id }
                ?: targetEp
            val isEpCurrentlyPlaying = playbackState.currentEpisode?.id == liveEp.id && playbackState.isPlaying
            val matchingPodcast = podcasts.find { it.id == liveEp.podcastId }
            EpisodeNotesBottomSheet(
                episode = liveEp,
                chapters = notesChapters,
                transcriptCues = notesTranscriptCues,
                bookmarks = notesBookmarks,
                isLoadingExtras = isNotesExtrasLoading,
                onDismiss = { viewModel.openEpisodeNotes(null) },
                onSeekToMs = { posMs ->
                    ensureNotificationPermissionIfNeeded()
                    viewModel.playEpisode(liveEp)
                    viewModel.seekTo(posMs)
                    viewModel.openEpisodeNotes(null)
                },
                onDeleteBookmark = viewModel::deleteBookmark,
                isPlaying = isEpCurrentlyPlaying,
                isDownloading = liveEp.id in downloadingEpisodeIds,
                onPlayToggle = {
                    if (playbackState.currentEpisode?.id == liveEp.id) {
                        viewModel.togglePlayPause()
                    } else {
                        ensureNotificationPermissionIfNeeded()
                        viewModel.playEpisode(liveEp)
                    }
                },
                onAddToUpNext = { toBeginning ->
                    viewModel.addEpisodeToUpNext(liveEp, toBeginning)
                },
                onRemoveFromUpNext = if (liveEp.inUpNext) {
                    { viewModel.toggleEpisodeUpNext(liveEp, false) }
                } else {
                    null
                },
                onToggleStarred = { viewModel.toggleStarred(liveEp) },
                onToggleDownloaded = { viewModel.toggleDownloaded(liveEp) },
                onMarkPlayed = { viewModel.markEpisodePlayed(liveEp, !liveEp.isPlayed) },
                customPlaylists = customPlaylists,
                onToggleEpisodeInPlaylist = { playlist ->
                    viewModel.toggleEpisodeInPlaylist(playlist, liveEp)
                },
                onCreatePlaylistWithEpisode = { name ->
                    viewModel.createPlaylist(name, "General", listOf(liveEp.id))
                },
                onOpenPodcast = if (matchingPodcast != null) {
                    {
                        viewModel.openEpisodeNotes(null)
                        viewModel.openPodcastDetail(matchingPodcast.id)
                    }
                } else {
                    null
                },
                onResetProgress = { viewModel.resetEpisodeProgress(liveEp) },
                onArchiveEpisode = {
                    viewModel.archiveEpisode(liveEp)
                    viewModel.openEpisodeNotes(null)
                }
            )
        }

        if (Edition.HAS_PRO && showProUpgradeSheet) {
            viewModel.proFeatures.UpgradeDialog(
                isProUnlocked = settings.isProUnlocked,
                billingState = billingState,
                onDismiss = { viewModel.setShowProUpgradeSheet(false) },
                onPurchasePro = {
                    val activity = context as? Activity
                    if (activity != null) {
                        viewModel.purchaseProWithPlayBilling(activity)
                    } else {
                        viewModel.postStatusMessage("Google Play Billing requires an active window.")
                    }
                },
                onRestorePurchases = viewModel::restoreProPurchases
            )
        }
    }
}
