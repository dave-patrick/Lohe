package com.example.ui

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.download.EpisodeDownloadManager
import com.example.data.download.PodcastStorageUsage
import com.example.data.importer.ImportProgressState
import com.example.data.importer.ImportResultSummary
import com.example.data.importer.PodcastAppImporter
import com.example.data.local.AutoQueueMode
import com.example.data.local.EpisodeBookmarkEntity
import com.example.data.local.EpisodeEntity
import com.example.data.local.EpisodeSwipeAction
import com.example.data.local.HomeCardType
import com.example.data.local.ListeningHistoryRange
import com.example.data.local.LoheDatabase
import com.example.data.local.LohePreferencesRepository
import com.example.data.local.LoheUserSettings
import com.example.data.local.PlaylistEntity
import com.example.data.local.PodcastEntity
import com.example.data.local.PodcastFilterOption
import com.example.data.local.PodcastSortOption
import com.example.data.local.QueueIndicatorSize
import com.example.data.local.QueueIndicatorStyle
import com.example.data.local.SmartPlaylistType
import com.example.data.local.ToastDurationOption
import com.example.data.local.ToastPlacement
import com.example.data.local.ToastVisualStyle
import com.example.data.network.EpisodeChapter
import com.example.data.network.SafDriveSyncClient
import com.example.data.network.TranscriptCue
import com.example.data.repository.DiscoverCatalogItem
import com.example.data.repository.LoheRepository
import com.example.data.sync.DriveSyncEngine
import com.example.edition.DevTools
import com.example.edition.DriveAuthOutcome
import com.example.edition.Edition
import com.example.edition.LoheBillingState
import com.example.edition.ProFeatures
import com.example.edition.ProFeaturesRegistry
import com.example.notifications.LoheNotificationHelper
import com.example.playback.LohePlaybackService
import com.example.playback.LohePlayerController
import com.example.playback.PlaybackUiState
import com.example.ui.components.EpisodeBatchAction
import com.example.ui.components.PlaylistBatchAction
import com.example.ui.components.PodcastBatchAction
import com.example.ui.theme.QueuePresetColor
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PrimaryTab(val label: String, val testTag: String) {
    HOME("Home", "tab_home"),
    PODCASTS("Podcasts", "tab_podcasts"),
    DISCOVER("Discover", "tab_discover"),
    PLAYLISTS("Playlists", "tab_playlists"),
    UP_NEXT("Up Next", "tab_up_next")
}

enum class SettingsSubScreen(val title: String, val subtitle: String, val isProFeatured: Boolean = false) {
    OVERVIEW("Settings", "Configure Lohe v0.12.0"),
    APPEARANCE("Appearance", "Dark or light theme, Up Next indicator style, color & size"),
    TOAST_NOTIFICATIONS("Notifications", "Turn notifications on or off, duration, look & placement"),
    HOME_CARDS("Home screen", "Choose Home dashboard cards, Pro insights & history length"),
    PLAYBACK("Playback", "Speed, skip buttons, Sleep Shake-to-Extend, Boost & Battery setup"),
    FLOATING_PLAYER("Floating player", "Cross-app controls, size, transparency & battery setup", isProFeatured = true),
    ARCHIVING("Archiving", "Archive delay, retention rules & starred episode protection"),
    DOWNLOADS("Downloads", "Offline cache limits, auto-cleanup, background sync & OPML"),
    IMPORT_FROM_APP("Import from another app", "OPML, AntennaPod (.db), Podcast Addict (.backup) & Lohe JSON"),
    CLOUD_SYNC("Cloud Sync", "Google Drive, OneDrive, Dropbox, Nextcloud / WebDAV & cloud drives"),
    ABOUT("About", "Kani mascot, version 0.12.0 & Credits"),
    DIAGNOSTICS("Diagnostics", "Local bug reports, event logs & 127.0.0.1 debug API")
}

class LoheViewModel(application: Application) : AndroidViewModel(application) {

    private val database = LoheDatabase.getInstance(application)
    val downloadManager = EpisodeDownloadManager(application)
    val preferencesRepository = LohePreferencesRepository(application)
    val repository = LoheRepository(
        dao = database.loheDao(),
        downloadManager = downloadManager,
        preferencesRepository = preferencesRepository
    )
    val podcastAppImporter = PodcastAppImporter(application, database)
    private val webDavSyncClient = com.example.data.network.WebDavCloudSyncClient()
    private val safDriveSyncClient = SafDriveSyncClient(application)

    private val _importProgressState = MutableStateFlow(ImportProgressState())
    val importProgressState: StateFlow<ImportProgressState> = _importProgressState.asStateFlow()

    private val _lastImportSummary = MutableStateFlow<ImportResultSummary?>(null)
    val lastImportSummary: StateFlow<ImportResultSummary?> = _lastImportSummary.asStateFlow()

    val devTools: DevTools
        get() = DevTools.current

    val proFeatures: ProFeatures = ProFeaturesRegistry.create(
        context = application,
        scope = viewModelScope,
        onEntitlementChanged = { unlocked ->
            viewModelScope.launch {
                val effectiveUnlocked = unlocked || (devTools.isAvailable && devTools.debugProPreviewEnabled.value)
                preferencesRepository.setProUnlocked(effectiveUnlocked)
            }
        },
        onStatusNotice = ::postStatusMessage
    )

    val downloadingEpisodeIds: StateFlow<Set<String>> = downloadManager.downloadingIds

    private val _pendingDriveAuthIntent = MutableStateFlow<PendingIntent?>(null)
    val pendingDriveAuthIntent: StateFlow<PendingIntent?> = _pendingDriveAuthIntent.asStateFlow()

    private val _isCloudSyncBusy = MutableStateFlow(false)
    val isCloudSyncBusy: StateFlow<Boolean> = _isCloudSyncBusy.asStateFlow()

    private var pendingDriveRestoreAfterAuth = false

    val settingsState: StateFlow<LoheUserSettings> = preferencesRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LoheUserSettings()
        )

    val subscribedPodcasts: StateFlow<List<PodcastEntity>> = repository.subscribedPodcasts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeEpisodes: StateFlow<List<EpisodeEntity>> = repository.activeEpisodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEpisodes: StateFlow<List<EpisodeEntity>> = repository.allEpisodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upNextEpisodes: StateFlow<List<EpisodeEntity>> = repository.upNextEpisodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inProgressEpisodes: StateFlow<List<EpisodeEntity>> = repository.inProgressEpisodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customPlaylists: StateFlow<List<PlaylistEntity>> = repository.customPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTab = MutableStateFlow(PrimaryTab.HOME)
    val selectedTab: StateFlow<PrimaryTab> = _selectedTab.asStateFlow()

    private val _selectedPodcastId = MutableStateFlow<String?>(null)
    val selectedPodcastId: StateFlow<String?> = _selectedPodcastId.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylistId: StateFlow<String?> = _selectedPlaylistId.asStateFlow()

    private val _activeSettingsScreen = MutableStateFlow<SettingsSubScreen?>(null)
    val activeSettingsScreen: StateFlow<SettingsSubScreen?> = _activeSettingsScreen.asStateFlow()

    private val _isFullPlayerOpen = MutableStateFlow(false)
    val isFullPlayerOpen: StateFlow<Boolean> = _isFullPlayerOpen.asStateFlow()

    private val _episodeNotesSheetTarget = MutableStateFlow<EpisodeEntity?>(null)
    val episodeNotesSheetTarget: StateFlow<EpisodeEntity?> = _episodeNotesSheetTarget.asStateFlow()

    private val _showProUpgradeSheet = MutableStateFlow(false)
    val showProUpgradeSheet: StateFlow<Boolean> = _showProUpgradeSheet.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _selectedFolderFilter = MutableStateFlow("All")
    val selectedFolderFilter: StateFlow<String> = _selectedFolderFilter.asStateFlow()

    // Discover Live iTunes Search + Preview state
    private val _discoverQuery = MutableStateFlow("")
    val discoverQuery: StateFlow<String> = _discoverQuery.asStateFlow()

    private val _discoverResults = MutableStateFlow<List<DiscoverCatalogItem>>(repository.publicDiscoveryCatalog)
    val discoverResults: StateFlow<List<DiscoverCatalogItem>> = _discoverResults.asStateFlow()

    private val _isDiscoverSearching = MutableStateFlow(false)
    val isDiscoverSearching: StateFlow<Boolean> = _isDiscoverSearching.asStateFlow()

    private val _previewTargetItem = MutableStateFlow<DiscoverCatalogItem?>(null)
    val previewTargetItem: StateFlow<DiscoverCatalogItem?> = _previewTargetItem.asStateFlow()

    private val _previewEpisodes = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    val previewEpisodes: StateFlow<List<EpisodeEntity>> = _previewEpisodes.asStateFlow()

    private val _isPreviewLoading = MutableStateFlow(false)
    val isPreviewLoading: StateFlow<Boolean> = _isPreviewLoading.asStateFlow()

    // Podcasting 2.0 Chapters, Transcripts & Bookmarks
    private val _activeEpisodeChapters = MutableStateFlow<List<EpisodeChapter>>(emptyList())
    val activeEpisodeChapters: StateFlow<List<EpisodeChapter>> = _activeEpisodeChapters.asStateFlow()

    private val _activeEpisodeTranscriptCues = MutableStateFlow<List<TranscriptCue>>(emptyList())
    val activeEpisodeTranscriptCues: StateFlow<List<TranscriptCue>> = _activeEpisodeTranscriptCues.asStateFlow()

    private val _activeEpisodeBookmarks = MutableStateFlow<List<EpisodeBookmarkEntity>>(emptyList())
    val activeEpisodeBookmarks: StateFlow<List<EpisodeBookmarkEntity>> = _activeEpisodeBookmarks.asStateFlow()

    private val _notesChapters = MutableStateFlow<List<EpisodeChapter>>(emptyList())
    val notesChapters: StateFlow<List<EpisodeChapter>> = _notesChapters.asStateFlow()

    private val _notesTranscriptCues = MutableStateFlow<List<TranscriptCue>>(emptyList())
    val notesTranscriptCues: StateFlow<List<TranscriptCue>> = _notesTranscriptCues.asStateFlow()

    private val _notesBookmarks = MutableStateFlow<List<EpisodeBookmarkEntity>>(emptyList())
    val notesBookmarks: StateFlow<List<EpisodeBookmarkEntity>> = _notesBookmarks.asStateFlow()

    private val _isNotesExtrasLoading = MutableStateFlow(false)
    val isNotesExtrasLoading: StateFlow<Boolean> = _isNotesExtrasLoading.asStateFlow()

    private var notesBookmarksJob: Job? = null

    val storageBreakdown: StateFlow<Pair<Long, List<PodcastStorageUsage>>> =
        combine(subscribedPodcasts, activeEpisodes) { pods, eps ->
            downloadManager.computeStorageBreakdown(pods, eps)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L to emptyList())

    private var accumulatedPlayMsSinceMinute = 0L
    private var lastTrackedPosMs = 0L
    private var lastTrackedEpisodeId: String? = null

    val playerController: LohePlayerController = LohePlayerController(
        context = application,
        scope = viewModelScope,
        onProgressPersist = { episodeId, posMs, durMs ->
            viewModelScope.launch {
                if (posMs <= 0L) return@launch
                val autoMarkedPlayed = repository.updatePlaybackPosition(episodeId, posMs, durMs)
                if (autoMarkedPlayed) {
                    if (settingsState.value.autoArchivePolicy == "IMMEDIATELY") {
                        repository.archiveAllPlayed()
                    }
                    repository.cleanupPlayedAndOversizeDownloads(
                        autoDeletePolicy = settingsState.value.autoDeletePlayedPolicy,
                        maxCacheMb = settingsState.value.maxCacheSizeMb
                    )
                }
                if (lastTrackedEpisodeId != episodeId) {
                    lastTrackedEpisodeId = episodeId
                    lastTrackedPosMs = posMs
                    return@launch
                }
                val delta = (posMs - lastTrackedPosMs).coerceIn(0L, 5_000L)
                lastTrackedPosMs = posMs
                if (delta > 0L) {
                    val speed = settingsState.value.playbackSpeed
                    val savedBySpeed = if (speed > 1.0f) ((speed - 1.0f) * delta).toLong() else 0L
                    val savedBySilence = if (settingsState.value.skipSilenceEnabled) delta / 12L else 0L
                    accumulatedPlayMsSinceMinute += delta
                    val addMinute = accumulatedPlayMsSinceMinute >= 60_000L
                    if (addMinute) accumulatedPlayMsSinceMinute -= 60_000L
                    if (savedBySpeed + savedBySilence > 0L || addMinute) {
                        preferencesRepository.recordListeningTick(savedBySpeed + savedBySilence, addMinute)
                    }
                }
            }
        },
        onEpisodeCompleted = { completedEpisodeId ->
            viewModelScope.launch {
                if (settingsState.value.markPlayedWhenFinished) {
                    repository.markEpisodePlayed(completedEpisodeId, true)
                }
                if (settingsState.value.autoArchivePolicy == "IMMEDIATELY") {
                    repository.archiveAllPlayed()
                }
                repository.cleanupPlayedAndOversizeDownloads(
                    autoDeletePolicy = settingsState.value.autoDeletePlayedPolicy,
                    maxCacheMb = settingsState.value.maxCacheSizeMb
                )
                if (playerController.consumeSleepAtEndOfEpisode()) {
                    postStatusMessage("Sleep timer: paused at end of episode")
                    return@launch
                }
                if (settingsState.value.continuousPlayback) {
                    val nextInQueue = upNextEpisodes.value.firstOrNull { it.id != completedEpisodeId }
                    if (nextInQueue != null) {
                        playEpisode(nextInQueue)
                    }
                }
            }
        }
    )

    val playbackState: StateFlow<PlaybackUiState> = playerController.state

    val billingState: StateFlow<LoheBillingState> = proFeatures.billingState

    init {
        if (Edition.HAS_PRO) {
            proFeatures.startBillingConnection()
        }
        LoheNotificationHelper.ensureChannels(application)
        devTools.attachRuntime(
            context = application,
            scope = viewModelScope,
            database = database,
            repository = repository,
            preferencesRepository = preferencesRepository,
            playerController = playerController,
            podcastAppImporter = podcastAppImporter,
            onStatusNotice = ::postStatusMessage,
            onRefreshProEntitlement = {
                if (Edition.HAS_PRO) {
                    proFeatures.restorePurchases(silent = true)
                }
            }
        )
        devTools.recordEvent("lifecycle", "LoheViewModel initialized")

        viewModelScope.launch {
            repository.seedPublicSampleShowsIfEmpty()
        }

        // Quietly hydrate live Apple Top Charts for Discover on launch
        viewModelScope.launch {
            runCatching {
                val live = repository.browseDiscoverCategory("Trending")
                if (live.isNotEmpty() && _discoverQuery.value.isBlank()) {
                    _discoverResults.value = live
                }
            }
        }

        // Observe active episode bookmarks
        viewModelScope.launch {
            playbackState
                .map { it.currentEpisode?.id }
                .distinctUntilChanged()
                .collectLatest { epId ->
                    if (epId.isNullOrBlank()) {
                        _activeEpisodeBookmarks.value = emptyList()
                    } else {
                        repository.getBookmarksForEpisode(epId).collect { _activeEpisodeBookmarks.value = it }
                    }
                }
        }

        // Keep player controller audio, shake-to-extend, & periodic sync aligned with preferences
        viewModelScope.launch {
            combine(settingsState, subscribedPodcasts) { prefs, pods -> prefs to pods }
                .collectLatest { (prefs, pods) ->
                    playerController.configureShakeToExtend(prefs.sleepShakeToExtendEnabled) { updatedSec ->
                        postStatusMessage("Sleep timer extended +5m (${updatedSec / 60}m left)")
                    }
                    proFeatures.schedulePeriodicSync(
                        context = getApplication(),
                        intervalHours = prefs.backgroundSyncIntervalHours,
                        wifiOnly = prefs.autoDownloadWifi,
                        enabled = prefs.backgroundSyncEnabled
                    )
                    val currentPod = playbackState.value.currentEpisode?.let { ep ->
                        pods.find { it.id == ep.podcastId }
                    }
                    if (currentPod?.useCustomAudio == true) {
                        playerController.setPlaybackSpeed(currentPod.customSpeed)
                        playerController.setSkipSilence(currentPod.customSkipSilence)
                        playerController.setVolumeBoostDb(currentPod.customVolumeBoostDb)
                    } else {
                        playerController.setPlaybackSpeed(prefs.playbackSpeed)
                        playerController.setSkipSilence(prefs.skipSilenceEnabled)
                        playerController.setVolumeBoostDb(prefs.volumeBoostDb)
                    }
                }
        }

        // Pre-populate mini-player honoring persisted lastPlayingEpisodeId, then in-progress or Up Next on launch (paused)
        viewModelScope.launch {
            val savedLastId = runCatching {
                preferencesRepository.settingsFlow.first().lastPlayingEpisodeId
            }.getOrDefault("")

            combine(inProgressEpisodes, upNextEpisodes) { inProg, queue ->
                inProg to queue
            }.collectLatest { (inProg, queue) ->
                if (playbackState.value.currentEpisode != null) return@collectLatest
                val lastEp = savedLastId.takeIf { it.isNotBlank() }
                    ?.let { repository.getEpisodeById(it) }
                    ?.takeIf { !it.isPlayed && !it.isArchived }
                val initialEp = lastEp ?: inProg.firstOrNull() ?: queue.firstOrNull()
                if (playbackState.value.currentEpisode == null && initialEp != null) {
                    lastTrackedPosMs = initialEp.resumePositionMs
                    applyEffectiveAudioForEpisode(initialEp)
                    playerController.loadAndPlayEpisode(initialEp, autoPlay = false)
                    refreshChaptersForActiveEpisode(initialEp)
                }
            }
        }
    }

    fun selectTab(tab: PrimaryTab) {
        _selectedTab.value = tab
        _activeSettingsScreen.value = null
        if (tab != PrimaryTab.PODCASTS) _selectedPodcastId.value = null
        if (tab != PrimaryTab.PLAYLISTS) _selectedPlaylistId.value = null
        devTools.recordEvent("navigation", "Selected tab ${tab.name}")
    }

    fun openPodcastDetail(podcastId: String?) {
        _selectedPodcastId.value = podcastId
        if (podcastId != null) {
            _selectedTab.value = PrimaryTab.PODCASTS
            _activeSettingsScreen.value = null
            devTools.recordEvent("navigation", "Opened podcast detail $podcastId")
        }
    }

    fun openPlaylistDetail(playlistId: String?) {
        _selectedPlaylistId.value = playlistId
        if (playlistId != null) {
            _selectedTab.value = PrimaryTab.PLAYLISTS
            _activeSettingsScreen.value = null
            devTools.recordEvent("navigation", "Opened playlist detail $playlistId")
        }
    }

    fun openSettings(subScreen: SettingsSubScreen = SettingsSubScreen.OVERVIEW) {
        if (_activeSettingsScreen.value == null) {
            clearStatusBanner()
        }
        _activeSettingsScreen.value = subScreen
        devTools.recordEvent("navigation", "Opened settings ${subScreen.name}")
    }

    fun navigateBackInSettings(): Boolean {
        val current = _activeSettingsScreen.value ?: return false
        _activeSettingsScreen.value = if (current == SettingsSubScreen.OVERVIEW) {
            null
        } else {
            SettingsSubScreen.OVERVIEW
        }
        return true
    }

    fun closeSettings() {
        _activeSettingsScreen.value = null
    }

    fun setFullPlayerOpen(open: Boolean) {
        _isFullPlayerOpen.value = open
    }

    fun openEpisodeNotes(episode: EpisodeEntity?) {
        _episodeNotesSheetTarget.value = episode
        notesBookmarksJob?.cancel()
        if (episode == null) {
            _notesChapters.value = emptyList()
            _notesTranscriptCues.value = emptyList()
            _notesBookmarks.value = emptyList()
            _isNotesExtrasLoading.value = false
            return
        }
        notesBookmarksJob = viewModelScope.launch {
            repository.getBookmarksForEpisode(episode.id).collect { _notesBookmarks.value = it }
        }
        viewModelScope.launch {
            _isNotesExtrasLoading.value = true
            val (chapters, cues) = repository.loadChaptersAndTranscript(episode)
            _notesChapters.value = chapters
            _notesTranscriptCues.value = cues
            _isNotesExtrasLoading.value = false
        }
    }

    fun setShowProUpgradeSheet(show: Boolean) {
        _showProUpgradeSheet.value = show && Edition.HAS_PRO
    }

    private var statusBannerJob: Job? = null

    fun clearStatusBanner() {
        statusBannerJob?.cancel()
        statusBannerJob = null
        _statusBannerMessage.value = null
    }

    fun postStatusMessage(msg: String) {
        if (!settingsState.value.toastNotificationsEnabled) {
            clearStatusBanner()
            return
        }
        showToastMessage(msg)
    }

    fun previewToastNotification() {
        val current = settingsState.value
        val msg = "Preview notification • ${current.toastStyle.label} (${current.toastPlacement.label}, ${current.toastDuration.shortLabel})"
        showToastMessage(msg)
    }

    private fun showToastMessage(msg: String) {
        statusBannerJob?.cancel()
        _statusBannerMessage.value = msg
        val durationMs = settingsState.value.toastDuration.durationMs
        if (durationMs > 0L) {
            statusBannerJob = viewModelScope.launch {
                delay(durationMs)
                if (_statusBannerMessage.value == msg) {
                    _statusBannerMessage.value = null
                }
            }
        }
    }

    fun setPodcastSortOption(sort: PodcastSortOption) {
        viewModelScope.launch {
            preferencesRepository.setPodcastSortOption(sort)
        }
    }

    fun setPodcastFilterOption(filter: PodcastFilterOption) {
        viewModelScope.launch {
            preferencesRepository.setPodcastFilterOption(filter)
        }
    }

    fun setToastSettings(
        enabled: Boolean,
        duration: ToastDurationOption,
        style: ToastVisualStyle,
        placement: ToastPlacement
    ) {
        if (!enabled) {
            clearStatusBanner()
        }
        viewModelScope.launch {
            preferencesRepository.setToastSettings(enabled, duration, style, placement)
        }
    }

    fun selectFolderFilter(folder: String) {
        _selectedFolderFilter.value = folder
    }

    // Discover actions
    fun searchDiscover(query: String) {
        _discoverQuery.value = query
        viewModelScope.launch {
            _isDiscoverSearching.value = true
            _discoverResults.value = repository.searchDiscoverPodcasts(query)
            _isDiscoverSearching.value = false
        }
    }

    fun browseDiscoverCategory(category: String) {
        _discoverQuery.value = category
        viewModelScope.launch {
            _isDiscoverSearching.value = true
            _discoverResults.value = repository.browseDiscoverCategory(category)
            _isDiscoverSearching.value = false
        }
    }

    fun openDiscoverPreview(item: DiscoverCatalogItem?) {
        _previewTargetItem.value = item
        if (item == null) {
            _previewEpisodes.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isPreviewLoading.value = true
            val (_, eps) = repository.previewDiscoverShow(item)
            _previewEpisodes.value = eps
            _isPreviewLoading.value = false
        }
    }

    private fun applyEffectiveAudioForEpisode(episode: EpisodeEntity) {
        val show = subscribedPodcasts.value.find { it.id == episode.podcastId }
        if (show?.useCustomAudio == true) {
            playerController.setPlaybackSpeed(show.customSpeed)
            playerController.setSkipSilence(show.customSkipSilence)
            playerController.setVolumeBoostDb(show.customVolumeBoostDb)
        } else {
            val prefs = settingsState.value
            playerController.setPlaybackSpeed(prefs.playbackSpeed)
            playerController.setSkipSilence(prefs.skipSilenceEnabled)
            playerController.setVolumeBoostDb(prefs.volumeBoostDb)
        }
    }

    private fun refreshChaptersForActiveEpisode(episode: EpisodeEntity) {
        val inlineChapters = com.example.data.network.PodcastingChaptersAndTranscripts
            .parsePodcastChaptersJson(episode.chaptersJson)
            .ifEmpty {
                com.example.data.network.PodcastingChaptersAndTranscripts
                    .parseTimestampChaptersFromHtml(episode.descriptionHtml)
            }
        val inlineCues = com.example.data.network.PodcastingChaptersAndTranscripts
            .parseTranscriptContent(episode.transcriptText, episode.transcriptMimeType)
        _activeEpisodeChapters.value = inlineChapters
        _activeEpisodeTranscriptCues.value = inlineCues

        viewModelScope.launch {
            val (chapters, cues) = repository.loadChaptersAndTranscript(episode)
            if (playbackState.value.currentEpisode?.id == episode.id) {
                _activeEpisodeChapters.value = chapters
                _activeEpisodeTranscriptCues.value = cues
            }
        }
    }

    fun playEpisode(episode: EpisodeEntity) {
        lastTrackedPosMs = episode.resumePositionMs
        startMediaSessionServiceIfNeeded()
        applyEffectiveAudioForEpisode(episode)
        playerController.loadAndPlayEpisode(episode, autoPlay = true)
        refreshChaptersForActiveEpisode(episode)
        devTools.recordEvent("playback", "Play episode ${episode.id} (${episode.title}) stream=${episode.audioUrl}")
        viewModelScope.launch {
            preferencesRepository.setLastPlayingEpisodeId(episode.id)
            val feedUrl = subscribedPodcasts.value.firstOrNull { it.id == episode.podcastId }?.feedUrl.orEmpty()
            val epKey = DriveSyncEngine.episodeCanonicalKey(episode, feedUrl)
            val now = System.currentTimeMillis()
            preferencesRepository.updateDriveSyncJournal { journal ->
                journal.copy(
                    lastPlayingEpisodeKey = epKey,
                    lastPlayingUpdatedAt = now
                )
            }
        }
    }

    fun togglePlayPause() {
        startMediaSessionServiceIfNeeded()
        playerController.togglePlayPause()
        devTools.recordEvent("playback", "Toggled play/pause")
    }

    private fun startMediaSessionServiceIfNeeded() {
        try {
            val app = getApplication<Application>()
            val intent = Intent(app, LohePlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startService(intent)
            } else {
                app.startService(intent)
            }
        } catch (_: Exception) {
            // Safe ignore in unit tests or background restrictions
        }
    }

    fun skipBack() {
        playerController.skipBySeconds(-settingsState.value.skipBackSeconds)
    }

    fun skipForward() {
        playerController.skipBySeconds(settingsState.value.skipForwardSeconds)
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun setSleepTimerMinutes(minutes: Int) {
        playerController.setSleepTimerMinutes(minutes)
        postStatusMessage(
            if (minutes > 0) "Sleep timer set for $minutes min" else "Sleep timer canceled"
        )
    }

    fun setSleepAtEndOfEpisode(enabled: Boolean) {
        playerController.setSleepAtEndOfEpisode(enabled)
        postStatusMessage(
            if (enabled) "Sleep timer set for end of episode" else "Sleep timer canceled"
        )
    }

    fun setSleepShakeToExtend(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSleepShakeToExtend(enabled) }
    }

    fun addBookmarkAtCurrentPosition(label: String) {
        val current = playbackState.value.currentEpisode ?: return
        val posMs = playbackState.value.positionMs
        viewModelScope.launch {
            repository.addBookmark(current.id, posMs, label)
            postStatusMessage("Saved bookmark at ${com.example.data.network.PodcastingChaptersAndTranscripts.formatTimestamp(posMs)}")
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmarkId)
            postStatusMessage("Removed bookmark")
        }
    }

    fun playEpisodeNext(episode: EpisodeEntity) {
        viewModelScope.launch {
            repository.setEpisodeUpNextPosition(episode, toBeginning = true)
            postStatusMessage("Queued \"${episode.title}\" to play next")
        }
    }

    fun toggleEpisodeUpNext(episode: EpisodeEntity, addAtBeginning: Boolean = false) {
        viewModelScope.launch {
            val wasQueued = episode.inUpNext
            repository.toggleEpisodeUpNext(episode, addAtBeginning)
            postStatusMessage(
                if (wasQueued) "Removed from Up Next"
                else if (addAtBeginning) "Queued to play next"
                else "Added to end of Up Next"
            )
            if (!wasQueued && settingsState.value.autoDownloadUpNext) {
                repository.enforceAutoDownloadRules(
                    autoDownloadWifi = settingsState.value.autoDownloadWifi,
                    autoDownloadUpNext = true,
                    queueDepth = settingsState.value.autoDownloadQueueDepth
                )
            }
        }
    }

    fun addEpisodeToUpNext(episode: EpisodeEntity, toBeginning: Boolean) {
        viewModelScope.launch {
            repository.setEpisodeUpNextPosition(episode, toBeginning)
            postStatusMessage(
                if (toBeginning) "Added \"${episode.title}\" to beginning of Up Next"
                else "Added \"${episode.title}\" to end of Up Next"
            )
            if (settingsState.value.autoDownloadUpNext) {
                repository.enforceAutoDownloadRules(
                    autoDownloadWifi = settingsState.value.autoDownloadWifi,
                    autoDownloadUpNext = true,
                    queueDepth = settingsState.value.autoDownloadQueueDepth
                )
            }
        }
    }

    fun executeEpisodeSwipeAction(episode: EpisodeEntity, action: EpisodeSwipeAction) {
        when (action) {
            EpisodeSwipeAction.UP_NEXT_CHOOSE,
            EpisodeSwipeAction.PLAY_NEXT -> addEpisodeToUpNext(episode, toBeginning = true)
            EpisodeSwipeAction.PLAY_LAST -> addEpisodeToUpNext(episode, toBeginning = false)
            EpisodeSwipeAction.MARK_PLAYED -> markEpisodePlayed(episode, !episode.isPlayed)
            EpisodeSwipeAction.DOWNLOAD -> toggleDownloaded(episode)
            EpisodeSwipeAction.BOOKMARK -> {
                toggleStarred(episode)
                postStatusMessage(
                    if (!episode.isStarred) "Bookmarked \"${episode.title}\""
                    else "Removed bookmark"
                )
            }
            EpisodeSwipeAction.NONE -> Unit
        }
    }

    fun moveEpisodeToQueueEdge(episode: EpisodeEntity, toBeginning: Boolean) {
        viewModelScope.launch {
            repository.setEpisodeUpNextPosition(episode, toBeginning)
            postStatusMessage(if (toBeginning) "Moved to top of Up Next" else "Moved to end of Up Next")
        }
    }

    fun queueAllEpisodes(episodes: List<EpisodeEntity>, toBeginning: Boolean = false) {
        viewModelScope.launch {
            repository.queueEpisodes(episodes, toBeginning)
            postStatusMessage("Added ${episodes.size} episodes to Up Next")
        }
    }

    fun executeBatchEpisodeAction(episodes: List<EpisodeEntity>, action: EpisodeBatchAction) {
        if (episodes.isEmpty()) return
        val count = episodes.size
        viewModelScope.launch {
            when (action) {
                EpisodeBatchAction.ADD_UP_NEXT_TOP -> {
                    repository.queueEpisodes(episodes, toBeginning = true)
                    postStatusMessage("Added $count episode${if (count == 1) "" else "s"} to top of Up Next")
                }
                EpisodeBatchAction.ADD_UP_NEXT_BOTTOM -> {
                    repository.queueEpisodes(episodes, toBeginning = false)
                    postStatusMessage("Added $count episode${if (count == 1) "" else "s"} to end of Up Next")
                }
                EpisodeBatchAction.MOVE_UP_NEXT_TOP -> {
                    repository.moveEpisodesToQueueEdge(episodes, toBeginning = true)
                    postStatusMessage("Moved $count episode${if (count == 1) "" else "s"} to top of Up Next")
                }
                EpisodeBatchAction.MOVE_UP_NEXT_BOTTOM -> {
                    repository.moveEpisodesToQueueEdge(episodes, toBeginning = false)
                    postStatusMessage("Moved $count episode${if (count == 1) "" else "s"} to end of Up Next")
                }
                EpisodeBatchAction.REMOVE_FROM_UP_NEXT -> {
                    repository.removeEpisodesFromUpNext(episodes)
                    postStatusMessage("Removed $count episode${if (count == 1) "" else "s"} from Up Next")
                }
                EpisodeBatchAction.MARK_PLAYED -> {
                    repository.markEpisodesPlayed(episodes, played = true)
                    postStatusMessage("Marked $count episode${if (count == 1) "" else "s"} as played")
                }
                EpisodeBatchAction.MARK_UNPLAYED -> {
                    repository.markEpisodesPlayed(episodes, played = false)
                    postStatusMessage("Marked $count episode${if (count == 1) "" else "s"} as unplayed")
                }
                EpisodeBatchAction.DOWNLOAD -> {
                    postStatusMessage("Downloading $count episode${if (count == 1) "" else "s"}...")
                    val (ok, failed) = repository.downloadEpisodes(episodes)
                    postStatusMessage(
                        if (failed > 0) "Downloaded $ok episode(s) ($failed failed)"
                        else "Downloaded $ok episode${if (ok == 1) "" else "s"}"
                    )
                }
                EpisodeBatchAction.REMOVE_DOWNLOAD -> {
                    val removed = repository.removeDownloadsForEpisodes(episodes)
                    postStatusMessage("Removed $removed offline download${if (removed == 1) "" else "s"}")
                }
                EpisodeBatchAction.BOOKMARK -> {
                    repository.setEpisodesStarred(episodes, starred = true)
                    postStatusMessage("Starred $count episode${if (count == 1) "" else "s"}")
                }
                EpisodeBatchAction.UNBOOKMARK -> {
                    repository.setEpisodesStarred(episodes, starred = false)
                    postStatusMessage("Removed star from $count episode${if (count == 1) "" else "s"}")
                }
                EpisodeBatchAction.ARCHIVE -> {
                    repository.archiveEpisodes(episodes)
                    postStatusMessage("Archived $count episode${if (count == 1) "" else "s"}")
                }
            }
        }
    }

    fun executeBatchPodcastAction(podcasts: List<PodcastEntity>, action: PodcastBatchAction) {
        if (podcasts.isEmpty()) return
        val count = podcasts.size
        viewModelScope.launch {
            when (action) {
                PodcastBatchAction.MARK_ALL_PLAYED -> {
                    repository.markAllEpisodesPlayedForPodcasts(podcasts)
                    postStatusMessage("Marked all episodes played for $count show${if (count == 1) "" else "s"}")
                }
                PodcastBatchAction.AUTO_QUEUE_ON -> {
                    repository.updatePodcastsAutoQueue(podcasts, AutoQueueMode.END)
                    postStatusMessage("Auto-Queue enabled for $count show${if (count == 1) "" else "s"}")
                }
                PodcastBatchAction.AUTO_QUEUE_OFF -> {
                    repository.updatePodcastsAutoQueue(podcasts, AutoQueueMode.OFF)
                    postStatusMessage("Auto-Queue disabled for $count show${if (count == 1) "" else "s"}")
                }
                PodcastBatchAction.REFRESH -> {
                    refreshSubscribedFeedsNow()
                }
                PodcastBatchAction.UNSUBSCRIBE -> {
                    val removedIds = podcasts.map { it.id }.toSet()
                    repository.unsubscribePodcasts(podcasts)
                    if (_selectedPodcastId.value in removedIds) _selectedPodcastId.value = null
                    postStatusMessage("Unsubscribed from $count show${if (count == 1) "" else "s"}")
                }
            }
        }
    }

    fun executeBatchPlaylistAction(playlists: List<PlaylistEntity>, action: PlaylistBatchAction) {
        if (playlists.isEmpty()) return
        val count = playlists.size
        viewModelScope.launch {
            when (action) {
                PlaylistBatchAction.QUEUE_EPISODES -> {
                    val allEps = activeEpisodes.value
                    val resolved = playlists
                        .flatMap { pl -> resolvePlaylistEpisodes(pl, allEps) }
                        .distinctBy { it.id }
                    if (resolved.isNotEmpty()) {
                        repository.queueEpisodes(resolved, toBeginning = false)
                        postStatusMessage("Queued ${resolved.size} episode${if (resolved.size == 1) "" else "s"} from $count playlist${if (count == 1) "" else "s"}")
                    } else {
                        postStatusMessage("No unplayed episodes in selected playlists")
                    }
                }
                PlaylistBatchAction.PIN -> {
                    repository.setPlaylistsPinned(playlists, pinned = true)
                    postStatusMessage("Pinned $count playlist${if (count == 1) "" else "s"}")
                }
                PlaylistBatchAction.UNPIN -> {
                    repository.setPlaylistsPinned(playlists, pinned = false)
                    postStatusMessage("Unpinned $count playlist${if (count == 1) "" else "s"}")
                }
                PlaylistBatchAction.AUTO_DOWNLOAD_ON -> {
                    repository.setPlaylistsAutoDownload(playlists, enabled = true)
                    postStatusMessage("Auto-download enabled for $count playlist${if (count == 1) "" else "s"}")
                }
                PlaylistBatchAction.AUTO_DOWNLOAD_OFF -> {
                    repository.setPlaylistsAutoDownload(playlists, enabled = false)
                    postStatusMessage("Auto-download disabled for $count playlist${if (count == 1) "" else "s"}")
                }
                PlaylistBatchAction.DELETE -> {
                    val customIds = playlists.filter { it.smartRuleType.isEmpty() }.map { it.id }.toSet()
                    val deleted = repository.deleteCustomPlaylists(playlists)
                    if (_selectedPlaylistId.value in customIds) _selectedPlaylistId.value = null
                    postStatusMessage(
                        if (deleted > 0) "Deleted $deleted custom playlist${if (deleted == 1) "" else "s"}"
                        else "Smart playlists cannot be deleted"
                    )
                }
            }
        }
    }

    fun moveEpisodeInQueue(episode: EpisodeEntity, moveUp: Boolean) {
        viewModelScope.launch {
            repository.moveEpisodeInQueue(episode, moveUp)
        }
    }

    fun reorderUpNextQueue(orderedEpisodeIds: List<String>) {
        viewModelScope.launch {
            repository.reorderUpNextQueue(orderedEpisodeIds)
        }
    }

    fun clearUpNextQueue() {
        viewModelScope.launch {
            repository.clearQueue()
            postStatusMessage("Up Next queue cleared")
        }
    }

    fun toggleStarred(episode: EpisodeEntity) {
        viewModelScope.launch {
            repository.toggleEpisodeStarred(episode)
        }
    }

    fun toggleDownloaded(episode: EpisodeEntity) {
        viewModelScope.launch {
            if (!episode.isDownloaded) {
                postStatusMessage("Downloading \"${episode.title}\" for offline playback...")
            }
            val result = repository.toggleEpisodeDownloaded(episode)
            result.onSuccess { isNowDownloaded ->
                postStatusMessage(
                    if (isNowDownloaded) "Downloaded \"${episode.title}\" for offline playback"
                    else "Removed offline audio cache"
                )
            }.onFailure { err ->
                postStatusMessage("Download failed: ${err.localizedMessage ?: "Network error"}")
            }
        }
    }

    fun refreshSubscribedFeedsNow() {
        viewModelScope.launch {
            postStatusMessage("Checking subscribed RSS feeds for new episodes...")
            val (added, alerts) = repository.refreshAllSubscribedFeedsWithAlerts()
            if (settingsState.value.newEpisodeNotificationsEnabled && alerts.isNotEmpty()) {
                LoheNotificationHelper.postNewEpisodesSummary(getApplication(), alerts)
            }
            repository.cleanupPlayedAndOversizeDownloads(
                autoDeletePolicy = settingsState.value.autoDeletePlayedPolicy,
                maxCacheMb = settingsState.value.maxCacheSizeMb
            )
            repository.enforceAutoDownloadRules(
                autoDownloadWifi = settingsState.value.autoDownloadWifi,
                autoDownloadUpNext = settingsState.value.autoDownloadUpNext,
                queueDepth = settingsState.value.autoDownloadQueueDepth
            )
            postStatusMessage(
                if (added > 0) "Synced feeds • $added new episode(s) added"
                else "All subscribed feeds are up to date"
            )
        }
    }

    fun updatePodcastNotifications(podcast: PodcastEntity, notify: Boolean) {
        viewModelScope.launch {
            repository.updatePodcastNotifications(podcast, notify)
            postStatusMessage(
                if (notify) "${podcast.title}: New episode alerts ON"
                else "${podcast.title}: Alerts muted"
            )
        }
    }

    fun markEpisodePlayed(episode: EpisodeEntity, played: Boolean) {
        viewModelScope.launch {
            repository.markEpisodePlayed(episode.id, played)
            postStatusMessage(if (played) "Marked as played" else "Marked as unplayed")
        }
    }

    fun resetEpisodeProgress(episode: EpisodeEntity) {
        viewModelScope.launch {
            repository.resetEpisodeProgress(episode)
            postStatusMessage("Episode progress reset")
        }
    }

    fun archiveEpisode(episode: EpisodeEntity) {
        viewModelScope.launch {
            repository.archiveEpisodes(listOf(episode))
            postStatusMessage("Archived \"${episode.title}\"")
        }
    }

    fun toggleEpisodeInPlaylist(playlist: PlaylistEntity, episode: EpisodeEntity) {
        viewModelScope.launch {
            val wasInPlaylist = episode.id in playlist.episodeIdList()
            repository.toggleEpisodeInPlaylist(playlist, episode.id)
            postStatusMessage(
                if (wasInPlaylist) "Removed from \"${playlist.name}\""
                else "Added to \"${playlist.name}\""
            )
        }
    }

    fun updatePodcastAutoQueue(podcast: PodcastEntity, mode: AutoQueueMode) {
        viewModelScope.launch {
            repository.updatePodcastAutoQueue(podcast, mode)
            postStatusMessage("${podcast.title}: Auto-queue set to ${mode.label}")
        }
    }

    fun updatePodcastAudioOverrides(
        podcast: PodcastEntity,
        useCustomAudio: Boolean,
        customSpeed: Float,
        customSkipSilence: Boolean,
        customVolumeBoostDb: Int
    ) {
        viewModelScope.launch {
            repository.updatePodcastAudioOverrides(
                podcast = podcast,
                useCustomAudio = useCustomAudio,
                customSpeed = customSpeed,
                customSkipSilence = customSkipSilence,
                customVolumeBoostDb = customVolumeBoostDb
            )
            val currentEp = playbackState.value.currentEpisode
            if (currentEp?.podcastId == podcast.id) {
                if (useCustomAudio) {
                    playerController.setPlaybackSpeed(customSpeed)
                    playerController.setSkipSilence(customSkipSilence)
                    playerController.setVolumeBoostDb(customVolumeBoostDb)
                } else {
                    val prefs = settingsState.value
                    playerController.setPlaybackSpeed(prefs.playbackSpeed)
                    playerController.setSkipSilence(prefs.skipSilenceEnabled)
                    playerController.setVolumeBoostDb(prefs.volumeBoostDb)
                }
            }
            postStatusMessage(
                if (useCustomAudio) "${podcast.title}: Custom audio (${customSpeed}x) enabled"
                else "${podcast.title}: Using global audio settings"
            )
        }
    }

    fun unsubscribePodcast(podcastId: String) {
        viewModelScope.launch {
            repository.unsubscribePodcast(podcastId)
            if (_selectedPodcastId.value == podcastId) _selectedPodcastId.value = null
            postStatusMessage("Unsubscribed from podcast")
        }
    }

    fun subscribeToDiscoverItem(item: DiscoverCatalogItem) {
        viewModelScope.launch {
            repository.subscribeToCatalogItem(item)
            _previewTargetItem.value = null
            postStatusMessage("Subscribed to ${item.title}")
        }
    }

    fun subscribeByRssUrl(rssUrl: String) {
        if (Edition.HAS_PRO && !settingsState.value.isProUnlocked) {
            _showProUpgradeSheet.value = true
            return
        }
        viewModelScope.launch {
            val res = repository.addPodcastByRssUrl(rssUrl)
            res.onSuccess { pod ->
                postStatusMessage("Subscribed to ${pod.title}")
            }.onFailure { err ->
                postStatusMessage("RSS error: ${err.localizedMessage ?: "Invalid URL"}")
            }
        }
    }

    fun createPlaylist(name: String, folderName: String, initialEpisodeIds: List<String>) {
        viewModelScope.launch {
            repository.createCustomPlaylist(name, folderName, initialEpisodeIds)
            postStatusMessage("Created playlist \"$name\"")
        }
    }

    fun togglePlaylistPinned(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.updatePlaylist(playlist.copy(isPinned = !playlist.isPinned))
        }
    }

    fun togglePlaylistAutoDownload(playlist: PlaylistEntity) {
        viewModelScope.launch {
            repository.updatePlaylist(playlist.copy(autoDownload = !playlist.autoDownload))
            postStatusMessage(
                if (!playlist.autoDownload) "${playlist.name}: Auto-download enabled"
                else "${playlist.name}: Auto-download disabled"
            )
        }
    }

    fun updatePlaylistFolder(playlist: PlaylistEntity, folderName: String) {
        viewModelScope.launch {
            repository.updatePlaylist(playlist.copy(folderName = folderName.trim().ifBlank { "General" }))
            postStatusMessage("Moved \"${playlist.name}\" to folder")
        }
    }

    fun updatePlaylistAssignedPodcasts(playlist: PlaylistEntity, podcastIds: List<String>) {
        viewModelScope.launch {
            repository.updatePlaylist(playlist.copy(assignedPodcastIdsCsv = podcastIds.joinToString(",")))
            postStatusMessage("Updated assigned podcasts for \"${playlist.name}\"")
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deleteCustomPlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) _selectedPlaylistId.value = null
            postStatusMessage("Playlist deleted")
        }
    }

    fun resolvePlaylistEpisodes(playlist: PlaylistEntity, allEpisodes: List<EpisodeEntity>): List<EpisodeEntity> {
        val unplayedEpisodes = allEpisodes.filter { !it.isPlayed }
        val smart = SmartPlaylistType.entries.find { it.name == playlist.smartRuleType }
        if (smart != null) {
            return when (smart) {
                SmartPlaylistType.EVERYTHING -> unplayedEpisodes.sortedByDescending { it.publishedAt }
                SmartPlaylistType.RECENT_RELEASES -> unplayedEpisodes.sortedByDescending { it.publishedAt }.take(25)
                SmartPlaylistType.IN_PROGRESS -> unplayedEpisodes.filter { it.resumePositionMs > 0L }
                SmartPlaylistType.UNPLAYED -> unplayedEpisodes
                SmartPlaylistType.DOWNLOADED -> unplayedEpisodes.filter { it.isDownloaded }
                SmartPlaylistType.STARRED -> unplayedEpisodes.filter { it.isStarred }
                SmartPlaylistType.SHORT_EPISODES -> unplayedEpisodes.filter { it.durationMs <= 25 * 60 * 1000L }
            }
        }
        val assignedPods = playlist.assignedPodcastIdList()
        val explicitEps = playlist.episodeIdList()
        return unplayedEpisodes.filter { ep ->
            ep.id in explicitEps || ep.podcastId in assignedPods
        }.sortedByDescending { it.publishedAt }
    }

    // Storage Access Framework (SAF) On-Device Multi-App Import & Export
    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = podcastAppImporter.importFromUri(uri) { progress ->
                _importProgressState.value = progress
            }
            result.onSuccess { summary ->
                _lastImportSummary.value = summary
                postStatusMessage(summary.toDisplayBanner())
            }.onFailure { err ->
                _importProgressState.value = ImportProgressState(isImporting = false)
                postStatusMessage("Import failed: ${err.localizedMessage ?: "Unsupported file"}")
            }
        }
    }

    fun clearLastImportSummary() {
        _lastImportSummary.value = null
    }

    fun exportToUri(uri: Uri, asJson: Boolean) {
        viewModelScope.launch {
            try {
                val payload = repository.buildExportPayload(asJson)
                val app = getApplication<Application>()
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(payload)
                    }
                }
                postStatusMessage(
                    if (asJson) "Exported Lohe JSON library backup successfully"
                    else "Exported OPML subscriptions successfully"
                )
            } catch (e: Exception) {
                postStatusMessage("Export failed: ${e.localizedMessage}")
            }
        }
    }

    fun exportDatabaseToUri(uri: Uri) {
        viewModelScope.launch {
            val result = podcastAppImporter.exportLoheDatabaseToUri(uri)
            result.onSuccess { msg ->
                postStatusMessage(msg)
            }.onFailure { err ->
                postStatusMessage("Database export failed: ${err.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    // Settings updates
    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch { preferencesRepository.setThemePreference(theme) }
    }

    fun setReducedMotion(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setReducedMotion(enabled) }
    }

    fun setUpNextIndicatorEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setUpNextIndicatorEnabled(enabled) }
    }

    fun setQueueIndicatorStyle(style: QueueIndicatorStyle) {
        if (style.isPro && (!Edition.HAS_PRO || !settingsState.value.isProUnlocked)) {
            if (Edition.HAS_PRO) _showProUpgradeSheet.value = true
            return
        }
        viewModelScope.launch { preferencesRepository.setQueueIndicatorStyle(style) }
    }

    fun setQueueIndicatorSize(size: QueueIndicatorSize) {
        viewModelScope.launch { preferencesRepository.setQueueIndicatorSize(size) }
    }

    fun setQueuePresetColor(color: QueuePresetColor) {
        viewModelScope.launch { preferencesRepository.setQueuePresetColor(color) }
    }

    fun setCustomHexColor(hex: String, enabled: Boolean) {
        if (enabled && (!Edition.HAS_PRO || !settingsState.value.isProUnlocked)) {
            if (Edition.HAS_PRO) _showProUpgradeSheet.value = true
            return
        }
        viewModelScope.launch { preferencesRepository.setCustomHexColor(hex, enabled) }
    }

    fun setHomeCardEnabled(card: HomeCardType, enabled: Boolean) {
        if (enabled && card.isPro && (!Edition.HAS_PRO || !settingsState.value.isProUnlocked)) {
            if (Edition.HAS_PRO) _showProUpgradeSheet.value = true
            return
        }
        viewModelScope.launch {
            preferencesRepository.setHomeCardEnabled(
                card = card,
                enabled = enabled,
                isProUnlocked = settingsState.value.isProUnlocked
            )
        }
    }

    fun resetHomeCardsToDefault() {
        viewModelScope.launch {
            preferencesRepository.resetHomeCardsToDefault()
            postStatusMessage("Home screen cards reset to default")
        }
    }

    fun setListeningHistoryRange(range: ListeningHistoryRange) {
        viewModelScope.launch {
            preferencesRepository.setListeningHistoryRange(range)
            postStatusMessage("Listening History set to ${range.label}")
        }
    }

    fun openPodcastFromHome(podcastId: String) {
        _selectedTab.value = PrimaryTab.PODCASTS
        _selectedPodcastId.value = podcastId
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { preferencesRepository.setPlaybackSpeed(speed) }
    }

    fun setSkipDurations(backSec: Int, forwardSec: Int) {
        viewModelScope.launch { preferencesRepository.setSkipDurations(backSec, forwardSec) }
    }

    fun setSkipSilence(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSkipSilence(enabled) }
    }

    fun setVolumeBoostDb(db: Int) {
        viewModelScope.launch { preferencesRepository.setVolumeBoostDb(db) }
    }

    fun setContinuousPlayback(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setContinuousPlayback(enabled) }
    }

    fun setPlaybackExtraOptions(markPlayedFinished: Boolean, resumeHeadset: Boolean, autoRewindSec: Int) {
        viewModelScope.launch {
            preferencesRepository.setPlaybackExtraOptions(markPlayedFinished, resumeHeadset, autoRewindSec)
        }
    }

    fun setSwipeActions(rightAction: EpisodeSwipeAction, leftAction: EpisodeSwipeAction) {
        viewModelScope.launch {
            preferencesRepository.setSwipeActions(rightAction, leftAction)
            postStatusMessage("Swipe gestures updated")
        }
    }

    fun setFloatingMiniPlayer(enabled: Boolean) {
        if (enabled && (!Edition.HAS_PRO || !settingsState.value.isProUnlocked)) {
            if (Edition.HAS_PRO) _showProUpgradeSheet.value = true
            return
        }
        viewModelScope.launch { preferencesRepository.setFloatingMiniPlayer(enabled) }
    }

    fun setFloatingAppearance(sizeDp: Int, transparencyPct: Int) {
        if (!Edition.HAS_PRO || !settingsState.value.isProUnlocked) {
            if (Edition.HAS_PRO) _showProUpgradeSheet.value = true
            return
        }
        viewModelScope.launch { preferencesRepository.setFloatingAppearance(sizeDp, transparencyPct) }
    }

    fun setAutoArchivePolicy(policy: String) {
        viewModelScope.launch { preferencesRepository.setAutoArchivePolicy(policy) }
    }

    fun setKeepStarredExcluded(keep: Boolean) {
        viewModelScope.launch { preferencesRepository.setKeepStarredExcluded(keep) }
    }

    fun setEpisodeLimitPerShow(limit: Int) {
        viewModelScope.launch { preferencesRepository.setEpisodeLimitPerShow(limit) }
    }

    fun archiveAllPlayedNow() {
        viewModelScope.launch {
            repository.archiveAllPlayed()
            postStatusMessage("Archived all played non-starred episodes")
        }
    }

    fun setAutoDownloadSettings(wifi: Boolean, upNext: Boolean, depth: Int = 5) {
        viewModelScope.launch { preferencesRepository.setAutoDownloadSettings(wifi, upNext, depth) }
    }

    fun setNewEpisodeNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNewEpisodeNotificationsEnabled(enabled) }
    }

    fun setBackgroundSyncPolicy(enabled: Boolean, intervalHours: Int) {
        viewModelScope.launch { preferencesRepository.setBackgroundSyncPolicy(enabled, intervalHours) }
    }

    fun setStorageCleanupPolicy(autoDeletePlayedPolicy: String, maxCacheSizeMb: Int) {
        viewModelScope.launch {
            preferencesRepository.setStorageCleanupPolicy(autoDeletePlayedPolicy, maxCacheSizeMb)
            repository.cleanupPlayedAndOversizeDownloads(autoDeletePlayedPolicy, maxCacheSizeMb)
        }
    }

    fun clearDownloadsForPodcast(podcastId: String) {
        viewModelScope.launch {
            repository.clearDownloadsForPodcast(podcastId)
            postStatusMessage("Cleared offline cache for show")
        }
    }

    fun setPlaylistsGridView(grid: Boolean) {
        viewModelScope.launch { preferencesRepository.setPlaylistsGridView(grid) }
    }

    fun clearCachedDownloads() {
        viewModelScope.launch {
            repository.clearAllDownloads()
            postStatusMessage("Cleared cached offline audio")
        }
    }

    fun purchaseProWithPlayBilling(activity: Activity) {
        if (!Edition.HAS_PRO) return
        proFeatures.purchasePro(activity)
    }

    fun restoreProPurchases() {
        if (!Edition.HAS_PRO) return
        proFeatures.restorePurchases(silent = false)
    }

    fun setCloudSyncProvider(provider: com.example.data.local.CloudSyncProvider) {
        viewModelScope.launch {
            preferencesRepository.setCloudSyncProvider(provider)
        }
    }

    fun saveWebDavConfig(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            preferencesRepository.setWebDavConfig(serverUrl, username, password)
            postStatusMessage("Saved Nextcloud / WebDAV server settings")
        }
    }

    fun syncWithWebDav(restore: Boolean = false) {
        if (Edition.HAS_PRO && !settingsState.value.isProUnlocked) {
            _showProUpgradeSheet.value = true
            return
        }
        val current = settingsState.value
        if (current.webDavServerUrl.isBlank()) {
            postStatusMessage("Enter your Nextcloud or WebDAV folder URL first")
            return
        }
        viewModelScope.launch {
            _isCloudSyncBusy.value = true
            try {
                val stamp = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date())
                val downloadRes = webDavSyncClient.downloadBackup(
                    serverUrl = current.webDavServerUrl,
                    username = current.webDavUsername,
                    password = current.webDavPassword
                )
                downloadRes.onSuccess { remoteJson ->
                    if (restore) {
                        if (remoteJson.isNullOrBlank()) {
                            postStatusMessage("No WebDAV sync file found at that URL yet")
                        } else {
                            val mergeResult = withContext(Dispatchers.IO) {
                    repository.syncWithDriveRemotePayload(remoteJson, pullOnly = true)
                }
                            val banner = mergeResult.summary.toShortBanner("Nextcloud / WebDAV")
                            preferencesRepository.setCloudSyncStatus(
                                current.cloudSyncAutoBackup,
                                "$banner • $stamp"
                            )
                            postStatusMessage(banner)
                        }
                    } else {
                        val mergeResult = withContext(Dispatchers.IO) {
                    repository.syncWithDriveRemotePayload(remoteJson, pullOnly = false)
                }
                        webDavSyncClient.uploadBackup(
                            serverUrl = current.webDavServerUrl,
                            username = current.webDavUsername,
                            password = current.webDavPassword,
                            jsonPayload = mergeResult.canonicalJsonPayload
                        ).onSuccess {
                            val banner = mergeResult.summary.toShortBanner("Nextcloud / WebDAV")
                            preferencesRepository.setCloudSyncStatus(
                                current.cloudSyncAutoBackup,
                                "$banner • $stamp"
                            )
                            postStatusMessage(banner)
                        }.onFailure { err ->
                            postStatusMessage(err.localizedMessage ?: "WebDAV upload failed")
                        }
                    }
                }.onFailure { err ->
                    postStatusMessage(err.localizedMessage ?: "WebDAV sync failed")
                }
            } catch (e: Exception) {
                postStatusMessage("WebDAV sync error: ${e.localizedMessage ?: "Invalid response"}")
            } finally {
                _isCloudSyncBusy.value = false
            }
        }
    }

    fun linkSafSyncFile(
        uri: Uri,
        provider: com.example.data.local.CloudSyncProvider,
        syncAfterLink: Boolean = true,
        restoreOnly: Boolean = false
    ) {
        viewModelScope.launch {
            val displayName = safDriveSyncClient.queryDisplayName(uri)
            preferencesRepository.setSafSyncFile(
                uri = uri.toString(),
                providerName = provider.name,
                displayName = displayName
            )
            if (syncAfterLink && (!Edition.HAS_PRO || settingsState.value.isProUnlocked)) {
                performSafDriveSyncInternal(
                    uri = uri,
                    provider = provider,
                    restore = restoreOnly
                )
            } else {
                postStatusMessage("Linked ${provider.title} file: $displayName")
            }
        }
    }

    fun unlinkSafSyncFile() {
        viewModelScope.launch {
            preferencesRepository.clearSafSyncFile()
            postStatusMessage("Unlinked cloud drive sync file")
        }
    }

    fun syncWithSafDrive(
        provider: com.example.data.local.CloudSyncProvider,
        restore: Boolean = false
    ) {
        if (Edition.HAS_PRO && !settingsState.value.isProUnlocked) {
            _showProUpgradeSheet.value = true
            return
        }
        val uriString = settingsState.value.safSyncUri
        if (uriString.isBlank()) {
            postStatusMessage("Link or create a ${provider.title} sync file first")
            return
        }
        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
        if (uri == null) {
            postStatusMessage("Invalid linked ${provider.title} file URI")
            return
        }
        viewModelScope.launch {
            performSafDriveSyncInternal(uri = uri, provider = provider, restore = restore)
        }
    }

    private suspend fun performSafDriveSyncInternal(
        uri: Uri,
        provider: com.example.data.local.CloudSyncProvider,
        restore: Boolean
    ) {
        _isCloudSyncBusy.value = true
        try {
            val stamp = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date())
            val readRes = safDriveSyncClient.readSyncFile(uri)
            val remoteJson = readRes.getOrElse { err ->
                postStatusMessage("Cannot read ${provider.title} file: ${err.localizedMessage ?: "Permission lost. Re-link file."}")
                return
            }
            if (restore) {
                if (remoteJson.isNullOrBlank()) {
                    postStatusMessage("${provider.title} sync file is empty — nothing to restore yet")
                    return
                }
                val mergeResult = withContext(Dispatchers.IO) {
                    repository.syncWithDriveRemotePayload(remoteJson, pullOnly = true)
                }
                val banner = mergeResult.summary.toShortBanner(provider.title)
                preferencesRepository.setCloudSyncStatus(
                    settingsState.value.cloudSyncAutoBackup,
                    "$banner • $stamp"
                )
                postStatusMessage(banner)
            } else {
                val mergeResult = withContext(Dispatchers.IO) {
                    repository.syncWithDriveRemotePayload(remoteJson, pullOnly = false)
                }
                safDriveSyncClient.writeSyncFile(uri, mergeResult.canonicalJsonPayload)
                    .onSuccess {
                        val banner = mergeResult.summary.toShortBanner(provider.title)
                        preferencesRepository.setCloudSyncStatus(
                            settingsState.value.cloudSyncAutoBackup,
                            "$banner • $stamp"
                        )
                        postStatusMessage(banner)
                    }
                    .onFailure { err ->
                        postStatusMessage("${provider.title} write failed: ${err.localizedMessage ?: "Re-link file"}")
                    }
            }
        } catch (e: Exception) {
            postStatusMessage("${provider.title} sync error: ${e.localizedMessage ?: "Invalid sync file"}")
        } finally {
            _isCloudSyncBusy.value = false
        }
    }

    fun recordCloudProviderSafSync(provider: com.example.data.local.CloudSyncProvider, restored: Boolean) {
        viewModelScope.launch {
            val stamp = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date())
            val actionVerb = if (restored) "Pulled from" else "Synced with"
            preferencesRepository.setCloudSyncStatus(
                settingsState.value.cloudSyncAutoBackup,
                "$actionVerb ${provider.title} • $stamp"
            )
        }
    }

    fun setCloudSyncAutoBackup(enabled: Boolean) {
        if (enabled && Edition.HAS_PRO && !settingsState.value.isProUnlocked) {
            _showProUpgradeSheet.value = true
            return
        }
        viewModelScope.launch {
            preferencesRepository.setCloudSyncStatus(enabled, settingsState.value.lastCloudSyncSummary)
            if (enabled && Edition.HAS_PRO) {
                proFeatures.enqueueImmediateSync(getApplication())
            }
        }
    }

    fun syncWithGoogleDrive(restore: Boolean = false) {
        if (!Edition.HAS_PRO || !settingsState.value.isProUnlocked) {
            if (Edition.HAS_PRO) _showProUpgradeSheet.value = true
            return
        }
        viewModelScope.launch {
            _isCloudSyncBusy.value = true
            pendingDriveRestoreAfterAuth = restore
            when (val auth = proFeatures.requestDriveAuthorization()) {
                is DriveAuthOutcome.Authorized -> {
                    performDriveSyncWithToken(auth.accessToken, restore)
                }
                is DriveAuthOutcome.NeedsResolution -> {
                    _isCloudSyncBusy.value = false
                    _pendingDriveAuthIntent.value = auth.pendingIntent
                }
                is DriveAuthOutcome.Unavailable -> {
                    _isCloudSyncBusy.value = false
                    postStatusMessage("${auth.reason} • Use SAF Drive Import/Export below anytime.")
                }
            }
        }
    }

    fun clearPendingDriveAuthIntent() {
        _pendingDriveAuthIntent.value = null
    }

    fun onDriveAuthorizationResult(resultCode: Int, data: Intent?) {
        _pendingDriveAuthIntent.value = null
        val token = proFeatures.extractDriveTokenFromResult(resultCode, data)
        if (token.isNullOrBlank()) {
            postStatusMessage("Google Drive OAuth canceled — tap Link Existing or Create Sync File above to sync directly via Google Drive")
            return
        }
        viewModelScope.launch {
            _isCloudSyncBusy.value = true
            performDriveSyncWithToken(token, pendingDriveRestoreAfterAuth)
        }
    }

    private suspend fun performDriveSyncWithToken(accessToken: String, restore: Boolean) {
        try {
            val stamp = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date())
            val downloadRes = proFeatures.downloadBackupFromAppData(accessToken)
            downloadRes.onSuccess { remoteJson ->
                if (restore) {
                    if (remoteJson.isNullOrBlank()) {
                        postStatusMessage("No Google Drive AppData sync file found yet")
                    } else {
                        val mergeResult = withContext(Dispatchers.IO) {
                    repository.syncWithDriveRemotePayload(remoteJson, pullOnly = true)
                }
                        val banner = mergeResult.summary.toShortBanner("Google Drive")
                        preferencesRepository.setCloudSyncStatus(
                            settingsState.value.cloudSyncAutoBackup,
                            "$banner • $stamp"
                        )
                        postStatusMessage(banner)
                    }
                } else {
                    val mergeResult = withContext(Dispatchers.IO) {
                    repository.syncWithDriveRemotePayload(remoteJson, pullOnly = false)
                }
                    proFeatures.uploadBackupToAppData(accessToken, mergeResult.canonicalJsonPayload)
                        .onSuccess {
                            val banner = mergeResult.summary.toShortBanner("Google Drive")
                            preferencesRepository.setCloudSyncStatus(
                                settingsState.value.cloudSyncAutoBackup,
                                "$banner • $stamp"
                            )
                            postStatusMessage(banner)
                        }
                        .onFailure { err ->
                            postStatusMessage("Drive upload failed: ${err.localizedMessage}")
                        }
                }
            }.onFailure { err ->
                postStatusMessage("Drive sync failed: ${err.localizedMessage}")
            }
        } catch (e: Exception) {
            postStatusMessage("Drive sync error: ${e.localizedMessage ?: "Invalid sync file"}")
        } finally {
            _isCloudSyncBusy.value = false
        }
    }

    override fun onCleared() {
        proFeatures.endBillingConnection()
        playerController.release()
        super.onCleared()
    }
}
