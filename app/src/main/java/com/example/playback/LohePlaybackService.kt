package com.example.playback

import android.app.PendingIntent
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.R
import com.example.data.local.EpisodeEntity
import com.example.data.local.LoheDao
import com.example.data.local.LoheDatabase
import com.example.data.local.LohePreferencesRepository
import com.example.data.local.PlaylistEntity
import com.example.data.local.PodcastEntity
import com.example.data.local.SmartPlaylistType
import com.example.widget.LohePlayerWidget
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@OptIn(UnstableApi::class)
class LohePlaybackService : MediaLibraryService() {

    companion object {
        const val ACTION_SET_SKIP_SILENCE = "com.example.playback.SET_SKIP_SILENCE"
        const val ACTION_SET_VOLUME_BOOST = "com.example.playback.SET_VOLUME_BOOST"
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_BOOST_DB = "boost_db"

        const val ACTION_WIDGET_TOGGLE_PLAY_PAUSE = "com.example.playback.WIDGET_TOGGLE_PLAY_PAUSE"
        const val ACTION_WIDGET_SKIP_BACK = "com.example.playback.WIDGET_SKIP_BACK"
        const val ACTION_WIDGET_SKIP_FORWARD = "com.example.playback.WIDGET_SKIP_FORWARD"

        const val EXTRA_RESUME_POSITION_MS = "com.example.playback.EXTRA_RESUME_POSITION_MS"
        const val EXTRA_DURATION_MS = "com.example.playback.EXTRA_DURATION_MS"
        const val EXTRA_PODCAST_ID = "com.example.playback.EXTRA_PODCAST_ID"
        const val EXTRA_IS_DOWNLOADED = "com.example.playback.EXTRA_IS_DOWNLOADED"

        const val NODE_ROOT = "lohe_root"
        const val NODE_RECENT = "lohe_recent"
        const val NODE_CONTINUE_LISTENING = "cat_continue_listening"
        const val NODE_IN_PROGRESS_LEGACY = "cat_in_progress"
        const val NODE_UP_NEXT = "cat_up_next"
        const val NODE_DOWNLOADS = "cat_downloads"
        const val NODE_SHOWS = "cat_shows"
        const val NODE_PODCASTS_LEGACY = "cat_podcasts"
        const val NODE_PLAYLISTS = "cat_playlists"
        const val PREFIX_PODCAST = "pod_"
        const val PREFIX_PLAYLIST = "pl_"

        private val _activeServiceFlow = MutableStateFlow<LohePlaybackService?>(null)
        val activeServiceFlow: StateFlow<LohePlaybackService?> = _activeServiceFlow.asStateFlow()

        private val _sharedQueueIdsFlow = MutableStateFlow<List<String>>(emptyList())
        val sharedQueueIdsFlow: StateFlow<List<String>> = _sharedQueueIdsFlow.asStateFlow()

        private val resumePositionsByMediaId = ConcurrentHashMap<String, Long>()

        fun getCachedResumePositionMs(mediaId: String): Long =
            resumePositionsByMediaId[mediaId] ?: 0L

        fun cacheResumePositionMs(mediaId: String, positionMs: Long) {
            if (mediaId.isNotBlank()) {
                resumePositionsByMediaId[mediaId] = positionMs.coerceAtLeast(0L)
            }
        }

        fun validResumePositionMs(episode: EpisodeEntity): Long {
            return if (!episode.isPlayed && episode.resumePositionMs > 0L &&
                (episode.durationMs <= 0L || episode.resumePositionMs < episode.durationMs)
            ) {
                episode.resumePositionMs
            } else {
                0L
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var exoPlayer: ExoPlayer? = null
    private var sessionPlayer: LoheAutoForwardingPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentBoostDb: Int = 0

    /** Per-podcast skip of the first / last N milliseconds of every episode (0 = off). */
    private var currentIntroMs: Long = 0L
    private var currentOutroMs: Long = 0L
    private var outroHandledId: String? = null

    fun setActiveSkips(introMs: Long, outroMs: Long) {
        currentIntroMs = introMs.coerceAtLeast(0L)
        currentOutroMs = outroMs.coerceAtLeast(0L)
    }
    private var progressPersistJob: Job? = null

    @Volatile
    private var resumeOnHeadsetConnect: Boolean = true
    @Volatile
    private var autoRewindSeconds: Int = 3
    @Volatile
    private var skipBackSeconds: Int = 15
    @Volatile
    private var skipForwardSeconds: Int = 30
    @Volatile
    private var continuousPlayback: Boolean = true
    @Volatile
    private var markPlayedWhenFinished: Boolean = true
    @Volatile
    private var autoArchivePolicy: String = "AFTER_24H"
    @Volatile
    private var globalPlaybackSpeed: Float = 1.0f
    @Volatile
    private var globalSkipSilence: Boolean = false
    @Volatile
    private var globalVolumeBoostDb: Int = 0

    @Volatile
    private var wasPausedAfterActivePlaybackInSession: Boolean = false
    @Volatile
    private var isCarOrExternalBrowserConnected: Boolean = false

    private var serviceCreatedElapsedMs: Long = 0L
    private var lastHeadsetResumeElapsedMs: Long = 0L

    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var headsetPlugReceiver: BroadcastReceiver? = null
    private lateinit var loheDao: LoheDao
    private lateinit var prefsRepo: LohePreferencesRepository

    lateinit var libraryCallback: MediaLibrarySession.Callback
        private set

    fun getPlayer(): Player? = sessionPlayer

    fun setSkipSilenceDirect(enabled: Boolean) {
        exoPlayer?.skipSilenceEnabled = enabled
    }

    fun setVolumeBoostDirect(db: Int) {
        currentBoostDb = db
        exoPlayer?.let { applyLoudnessBoost(it.audioSessionId, db) }
    }

    /**
     * ForwardingPlayer that guarantees every episode's saved resume position is supplied
     * directly in setMediaItem / setMediaItems / track transitions BEFORE playback starts,
     * never by seeking after STATE_READY.
     */
    inner class LoheAutoForwardingPlayer(private val delegate: ExoPlayer) : ForwardingPlayer(delegate) {

        private fun resolveStartPosition(mediaItem: MediaItem?, requestedStartPosMs: Long): Long {
            if (requestedStartPosMs != C.TIME_UNSET && requestedStartPosMs > 0L) {
                return requestedStartPosMs
            }
            if (mediaItem == null) return 0L
            val fromExtras = mediaItem.mediaMetadata.extras?.getLong(EXTRA_RESUME_POSITION_MS, 0L) ?: 0L
            val fromCache = resumePositionsByMediaId[mediaItem.mediaId] ?: 0L
            return maxOf(fromExtras, fromCache, 0L)
        }

        override fun setMediaItem(mediaItem: MediaItem) {
            val startPosMs = resolveStartPosition(mediaItem, C.TIME_UNSET)
            super.setMediaItem(mediaItem, startPosMs)
            publishTimelineQueueIds()
        }

        override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
            val startPosMs = resolveStartPosition(mediaItem, startPositionMs)
            super.setMediaItem(mediaItem, startPosMs)
            publishTimelineQueueIds()
        }

        override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
            if (resetPosition) {
                val startPosMs = resolveStartPosition(mediaItem, C.TIME_UNSET)
                super.setMediaItem(mediaItem, startPosMs)
            } else {
                super.setMediaItem(mediaItem, false)
            }
            publishTimelineQueueIds()
        }

        override fun setMediaItems(mediaItems: List<MediaItem>) {
            if (mediaItems.isEmpty()) {
                super.setMediaItems(mediaItems)
            } else {
                val startPosMs = resolveStartPosition(mediaItems[0], C.TIME_UNSET)
                super.setMediaItems(mediaItems, 0, startPosMs)
            }
            publishTimelineQueueIds()
        }

        override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
            if (resetPosition && mediaItems.isNotEmpty()) {
                val startPosMs = resolveStartPosition(mediaItems[0], C.TIME_UNSET)
                super.setMediaItems(mediaItems, 0, startPosMs)
            } else {
                super.setMediaItems(mediaItems, resetPosition)
            }
            publishTimelineQueueIds()
        }

        override fun setMediaItems(
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ) {
            if (mediaItems.isEmpty()) {
                super.setMediaItems(mediaItems, startIndex, startPositionMs)
            } else {
                val safeIndex = if (startIndex in mediaItems.indices) startIndex else 0
                val startPosMs = resolveStartPosition(mediaItems[safeIndex], startPositionMs)
                super.setMediaItems(mediaItems, safeIndex, startPosMs)
            }
            publishTimelineQueueIds()
        }

        override fun seekToNext() {
            if (hasNextMediaItem()) {
                val nextIdx = nextMediaItemIndex
                val nextItem = getMediaItemAt(nextIdx)
                val startPosMs = resolveStartPosition(nextItem, C.TIME_UNSET)
                super.seekTo(nextIdx, startPosMs)
            } else {
                super.seekToNext()
            }
        }

        override fun seekToNextMediaItem() {
            if (hasNextMediaItem()) {
                val nextIdx = nextMediaItemIndex
                val nextItem = getMediaItemAt(nextIdx)
                val startPosMs = resolveStartPosition(nextItem, C.TIME_UNSET)
                super.seekTo(nextIdx, startPosMs)
            } else {
                super.seekToNextMediaItem()
            }
        }

        override fun seekToPrevious() {
            if (hasPreviousMediaItem() && currentPosition <= maxSeekToPreviousPosition) {
                val prevIdx = previousMediaItemIndex
                val prevItem = getMediaItemAt(prevIdx)
                val startPosMs = resolveStartPosition(prevItem, C.TIME_UNSET)
                super.seekTo(prevIdx, startPosMs)
            } else {
                super.seekToPrevious()
            }
        }

        override fun seekToPreviousMediaItem() {
            if (hasPreviousMediaItem()) {
                val prevIdx = previousMediaItemIndex
                val prevItem = getMediaItemAt(prevIdx)
                val startPosMs = resolveStartPosition(prevItem, C.TIME_UNSET)
                super.seekTo(prevIdx, startPosMs)
            } else {
                super.seekToPreviousMediaItem()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceCreatedElapsedMs = SystemClock.elapsedRealtime()
        loheDao = LoheDatabase.getInstance(applicationContext).loheDao()
        prefsRepo = LohePreferencesRepository(applicationContext)

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .build()
                .apply {
                    setSmallIcon(R.drawable.ic_car_media_notification)
                }
        )

        serviceScope.launch {
            prefsRepo.settingsFlow.collectLatest { settings ->
                resumeOnHeadsetConnect = settings.resumeOnHeadsetConnect
                autoRewindSeconds = settings.autoRewindSeconds
                skipBackSeconds = settings.skipBackSeconds
                skipForwardSeconds = settings.skipForwardSeconds
                continuousPlayback = settings.continuousPlayback
                markPlayedWhenFinished = settings.markPlayedWhenFinished
                autoArchivePolicy = settings.autoArchivePolicy
                globalPlaybackSpeed = settings.playbackSpeed
                globalSkipSilence = settings.skipSilenceEnabled
                globalVolumeBoostDb = settings.volumeBoostDb
            }
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val rawPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(skipBackSeconds * 1000L)
            .setSeekForwardIncrementMs(skipForwardSeconds * 1000L)
            .build()

        val forwardingPlayer = LoheAutoForwardingPlayer(rawPlayer)
        exoPlayer = rawPlayer
        sessionPlayer = forwardingPlayer

        rawPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                applyLoudnessBoost(audioSessionId, currentBoostDb)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    wasPausedAfterActivePlaybackInSession = true
                    startServiceProgressPersistence(rawPlayer)
                } else {
                    persistCurrentPlayerProgress(rawPlayer)
                }
                syncWidgetFromPlayer(rawPlayer)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                publishTimelineQueueIds()
                val mediaId = mediaItem?.mediaId.orEmpty()
                if (mediaId.isNotBlank()) {
                    serviceScope.launch {
                        prefsRepo.setLastPlayingEpisodeId(mediaId)
                        applyEffectiveAudioForEpisodeId(mediaId, rawPlayer)
                    }
                }
                syncWidgetFromPlayer(rawPlayer)
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                publishTimelineQueueIds()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                val mediaId = rawPlayer.currentMediaItem?.mediaId.orEmpty()
                if (mediaId.isNotBlank() && newPosition.positionMs >= 0L) {
                    cacheResumePositionMs(mediaId, newPosition.positionMs)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val finishedId = rawPlayer.currentMediaItem?.mediaId.orEmpty()
                    if (finishedId.isNotBlank()) {
                        handleEpisodeCompletedInService(finishedId, rawPlayer)
                    }
                }
            }
        })

        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callback = object : MediaLibrarySession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                if (controller.packageName != packageName) {
                    isCarOrExternalBrowserConnected = true
                }
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(SessionCommand(ACTION_SET_SKIP_SILENCE, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_SET_VOLUME_BOOST, Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    ACTION_SET_SKIP_SILENCE -> {
                        val enabled = args.getBoolean(EXTRA_ENABLED, false)
                        rawPlayer.skipSilenceEnabled = enabled
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    ACTION_SET_VOLUME_BOOST -> {
                        val db = args.getInt(EXTRA_BOOST_DB, 0)
                        currentBoostDb = db
                        applyLoudnessBoost(rawPlayer.audioSessionId, db)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val rootExtras = Bundle().apply {
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                    )
                    putInt(
                        MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                        MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                    )
                    putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
                }
                val rootParams = LibraryParams.Builder()
                    .setExtras(rootExtras)
                    .setRecent(params?.isRecent == true)
                    .setOffline(params?.isOffline == true)
                    .setSuggested(params?.isSuggested == true)
                    .build()

                val rootId = if (params?.isRecent == true) NODE_RECENT else NODE_ROOT
                val rootItem = buildBrowsableNode(
                    mediaId = rootId,
                    title = "Lohe",
                    subtitle = "Podcasts & Signal Library",
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, rootParams))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                serviceScope.launch {
                    try {
                        val allChildren: List<MediaItem> = when {
                            parentId == NODE_ROOT -> listOf(
                                buildBrowsableNode(
                                    mediaId = NODE_CONTINUE_LISTENING,
                                    title = "Continue listening",
                                    subtitle = "In-progress episodes",
                                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                                ),
                                buildBrowsableNode(
                                    mediaId = NODE_UP_NEXT,
                                    title = "Up Next",
                                    subtitle = "Queued episodes",
                                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                                ),
                                buildBrowsableNode(
                                    mediaId = NODE_DOWNLOADS,
                                    title = "Downloads",
                                    subtitle = "Offline episodes",
                                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                                ),
                                buildBrowsableNode(
                                    mediaId = NODE_SHOWS,
                                    title = "Shows",
                                    subtitle = "Subscribed podcasts",
                                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS
                                ),
                                buildBrowsableNode(
                                    mediaId = NODE_PLAYLISTS,
                                    title = "Playlists",
                                    subtitle = "Smart & custom playlists",
                                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                                )
                            )
                            parentId == NODE_RECENT -> {
                                val recentEp = resolveMostRecentEpisode()
                                if (recentEp != null) listOf(buildPlayableEpisodeItem(recentEp)) else emptyList()
                            }
                            parentId == NODE_CONTINUE_LISTENING || parentId == NODE_IN_PROGRESS_LEGACY -> {
                                loheDao.getInProgressEpisodes().first().map(::buildPlayableEpisodeItem)
                            }
                            parentId == NODE_UP_NEXT -> {
                                loheDao.getUpNextEpisodes().first().map(::buildPlayableEpisodeItem)
                            }
                            parentId == NODE_DOWNLOADS -> {
                                loheDao.getDownloadedEpisodes().first().map(::buildPlayableEpisodeItem)
                            }
                            parentId == NODE_SHOWS || parentId == NODE_PODCASTS_LEGACY -> {
                                loheDao.getSubscribedPodcasts().first().map(::buildPodcastFolderItem)
                            }
                            parentId == NODE_PLAYLISTS -> {
                                getAllPlaylistsIncludingSmart().map(::buildPlaylistFolderItem)
                            }
                            parentId.startsWith(PREFIX_PODCAST) -> {
                                val podId = parentId.removePrefix(PREFIX_PODCAST)
                                loheDao.getEpisodesByPodcastId(podId).first().map(::buildPlayableEpisodeItem)
                            }
                            parentId.startsWith(PREFIX_PLAYLIST) -> {
                                val plId = parentId.removePrefix(PREFIX_PLAYLIST)
                                val pl = findPlaylistByIdOrRule(plId)
                                val allEps = loheDao.getActiveEpisodes().first()
                                if (pl != null) {
                                    resolvePlaylistEpisodes(pl, allEps).map(::buildPlayableEpisodeItem)
                                } else {
                                    emptyList()
                                }
                            }
                            else -> emptyList()
                        }
                        val paged = paginateItems(allChildren, page, pageSize)
                        future.set(LibraryResult.ofItemList(paged, params))
                    } catch (_: Throwable) {
                        future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                    }
                }
                return future
            }

            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val future = SettableFuture.create<LibraryResult<MediaItem>>()
                serviceScope.launch {
                    val item = when {
                        mediaId == NODE_ROOT -> buildBrowsableNode(
                            NODE_ROOT,
                            "Lohe",
                            "Podcasts & Signal Library",
                            MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                        )
                        mediaId == NODE_CONTINUE_LISTENING || mediaId == NODE_IN_PROGRESS_LEGACY -> buildBrowsableNode(
                            NODE_CONTINUE_LISTENING,
                            "Continue listening",
                            "In-progress episodes",
                            MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                        )
                        mediaId == NODE_UP_NEXT -> buildBrowsableNode(
                            NODE_UP_NEXT,
                            "Up Next",
                            "Queued episodes",
                            MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                        )
                        mediaId == NODE_DOWNLOADS -> buildBrowsableNode(
                            NODE_DOWNLOADS,
                            "Downloads",
                            "Offline episodes",
                            MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                        )
                        mediaId == NODE_SHOWS || mediaId == NODE_PODCASTS_LEGACY -> buildBrowsableNode(
                            NODE_SHOWS,
                            "Shows",
                            "Subscribed podcasts",
                            MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS
                        )
                        mediaId == NODE_PLAYLISTS -> buildBrowsableNode(
                            NODE_PLAYLISTS,
                            "Playlists",
                            "Smart & custom playlists",
                            MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                        )
                        mediaId.startsWith(PREFIX_PODCAST) -> {
                            loheDao.getPodcastById(mediaId.removePrefix(PREFIX_PODCAST))
                                ?.let(::buildPodcastFolderItem)
                        }
                        mediaId.startsWith(PREFIX_PLAYLIST) -> {
                            findPlaylistByIdOrRule(mediaId.removePrefix(PREFIX_PLAYLIST))
                                ?.let(::buildPlaylistFolderItem)
                        }
                        else -> loheDao.getEpisodeById(mediaId)?.let(::buildPlayableEpisodeItem)
                    }
                    if (item != null) {
                        future.set(LibraryResult.ofItem(item, null))
                    } else {
                        future.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                    }
                }
                return future
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>
            ): ListenableFuture<List<MediaItem>> {
                val future = SettableFuture.create<List<MediaItem>>()
                serviceScope.launch {
                    val resolved = resolveRequestedMediaItems(mediaItems)
                    future.set(resolved)
                }
                return future
            }

            override fun onSetMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>,
                startIndex: Int,
                startPositionMs: Long
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                serviceScope.launch {
                    val selection = resolveQueueAndStartPosition(
                        requestedItems = mediaItems,
                        requestedStartIndex = startIndex,
                        requestedStartPositionMs = startPositionMs
                    )
                    future.set(selection)
                }
                return future
            }

            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                serviceScope.launch {
                    val recent = resolveMostRecentEpisode()
                    if (recent != null) {
                        val (items, startIdx, startPosMs) = buildSharedQueueForEpisode(recent)
                        future.set(MediaSession.MediaItemsWithStartPosition(items, startIdx, startPosMs))
                    } else {
                        future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                    }
                }
                return future
            }

            override fun onSearch(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<Void>> {
                serviceScope.launch {
                    val matches = searchLibraryItems(query)
                    session.notifySearchResultChanged(browser, query, matches.size, params)
                }
                return Futures.immediateFuture(LibraryResult.ofVoid(params))
            }

            override fun onGetSearchResult(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                serviceScope.launch {
                    val matches = searchLibraryItems(query)
                    future.set(LibraryResult.ofItemList(paginateItems(matches, page, pageSize), params))
                }
                return future
            }
        }

        libraryCallback = callback
        mediaLibrarySession = MediaLibrarySession.Builder(this, forwardingPlayer, callback)
            .setSessionActivity(pendingIntent)
            .build()

        _activeServiceFlow.value = this
        observeDatabaseAndSyncBrowseTree()
        registerHeadsetAutoResumeListeners(rawPlayer)
    }

    private fun observeDatabaseAndSyncBrowseTree() {
        // Keep resumePositionsByMediaId hot in memory whenever active episodes change
        serviceScope.launch {
            loheDao.getActiveEpisodes().collectLatest { episodes ->
                episodes.forEach { ep ->
                    cacheResumePositionMs(ep.id, validResumePositionMs(ep))
                }
            }
        }

        serviceScope.launch {
            loheDao.getInProgressEpisodes().collectLatest { list ->
                mediaLibrarySession?.notifyChildrenChanged(NODE_CONTINUE_LISTENING, list.size, null)
            }
        }

        serviceScope.launch {
            loheDao.getUpNextEpisodes().collectLatest { upNext ->
                mediaLibrarySession?.notifyChildrenChanged(NODE_UP_NEXT, upNext.size, null)
                syncPlayerTimelineWithUpNext(upNext)
            }
        }

        serviceScope.launch {
            loheDao.getDownloadedEpisodes().collectLatest { downloads ->
                mediaLibrarySession?.notifyChildrenChanged(NODE_DOWNLOADS, downloads.size, null)
            }
        }

        serviceScope.launch {
            loheDao.getSubscribedPodcasts().collectLatest { shows ->
                mediaLibrarySession?.notifyChildrenChanged(NODE_SHOWS, shows.size, null)
            }
        }

        serviceScope.launch {
            loheDao.getCustomPlaylists().collectLatest {
                val allPlaylists = getAllPlaylistsIncludingSmart()
                mediaLibrarySession?.notifyChildrenChanged(NODE_PLAYLISTS, allPlaylists.size, null)
            }
        }
    }

    /**
     * Synchronizes ExoPlayer's queued timeline with the phone's Up Next queue without
     * interrupting the currently loaded or playing item and NEVER starting playback.
     */
    fun syncPlayerTimelineWithUpNext(upNext: List<EpisodeEntity>) {
        val player = exoPlayer ?: return
        upNext.forEach { ep ->
            cacheResumePositionMs(ep.id, validResumePositionMs(ep))
        }
        val upNextItems = upNext.map(::buildPlayableEpisodeItem)

        if (player.mediaItemCount == 0) {
            _sharedQueueIdsFlow.value = upNext.map { it.id }
            return
        }

        val currentIdx = player.currentMediaItemIndex.coerceAtLeast(0)
        val currentItem = player.currentMediaItem ?: return
        val currentId = currentItem.mediaId

        val targetIdxInUpNext = upNextItems.indexOfFirst { it.mediaId == currentId }
        val desiredItemsBefore: List<MediaItem>
        val desiredItemsAfter: List<MediaItem>

        if (targetIdxInUpNext >= 0) {
            desiredItemsBefore = upNextItems.subList(0, targetIdxInUpNext)
            desiredItemsAfter = upNextItems.subList(targetIdxInUpNext + 1, upNextItems.size)
        } else {
            desiredItemsBefore = emptyList()
            desiredItemsAfter = upNextItems
        }

        val desiredFullIds = desiredItemsBefore.map { it.mediaId } + listOf(currentId) + desiredItemsAfter.map { it.mediaId }
        val currentFullIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        if (desiredFullIds == currentFullIds) {
            _sharedQueueIdsFlow.value = currentFullIds
            return
        }

        if (currentIdx + 1 < player.mediaItemCount) {
            player.removeMediaItems(currentIdx + 1, player.mediaItemCount)
        }
        if (currentIdx > 0) {
            player.removeMediaItems(0, currentIdx)
        }
        if (desiredItemsBefore.isNotEmpty()) {
            player.addMediaItems(0, desiredItemsBefore)
        }
        if (desiredItemsAfter.isNotEmpty()) {
            player.addMediaItems(desiredItemsAfter)
        }
        publishTimelineQueueIds()
    }

    private fun publishTimelineQueueIds() {
        val player = exoPlayer ?: return
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        if (ids.isNotEmpty()) {
            _sharedQueueIdsFlow.value = ids
        }
    }

    private fun startServiceProgressPersistence(player: ExoPlayer) {
        progressPersistJob?.cancel()
        progressPersistJob = serviceScope.launch {
            var tick = 0
            while (isActive && player.isPlaying) {
                if (tick % 4 == 0) persistCurrentPlayerProgress(player)
                maybeSkipOutro(player)
                tick++
                delay(500L)
            }
        }
    }

    /** Ends the episode early once playback reaches the podcast's "skip last N seconds" point. */
    private fun maybeSkipOutro(player: ExoPlayer) {
        val outroMs = currentOutroMs
        if (outroMs <= 0L) return
        val mediaId = player.currentMediaItem?.mediaId.orEmpty()
        if (mediaId.isBlank() || outroHandledId == mediaId) return
        val dur = player.duration
        if (dur == C.TIME_UNSET || dur <= currentIntroMs + outroMs) return
        if (player.currentPosition < dur - outroMs) return
        outroHandledId = mediaId
        cacheResumePositionMs(mediaId, 0L)
        serviceScope.launch {
            if (markPlayedWhenFinished) {
                loheDao.markEpisodePlayed(mediaId, true)
            }
            if (autoArchivePolicy == "IMMEDIATELY") {
                loheDao.archiveAllPlayedNonStarred()
            }
        }
        player.seekTo(dur)
    }

    private fun persistCurrentPlayerProgress(player: ExoPlayer) {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        if (mediaId.isBlank()) return
        val pos = player.currentPosition.coerceAtLeast(0L)
        if (pos <= 0L) return
        val dur = player.duration.takeIf { it > 0L } ?: 0L
        cacheResumePositionMs(mediaId, pos)
        serviceScope.launch {
            val existing = loheDao.getEpisodeById(mediaId)
            val effectiveDur = if (dur > 0L) dur else (existing?.durationMs ?: 0L)
            if (existing != null && !existing.isPlayed &&
                com.example.data.repository.isEpisodeNearCompletion(pos, effectiveDur)
            ) {
                loheDao.markEpisodePlayed(mediaId, true, System.currentTimeMillis())
                if (autoArchivePolicy == "IMMEDIATELY") {
                    loheDao.archiveAllPlayedNonStarred()
                }
            } else if (existing?.isPlayed != true) {
                loheDao.updatePlaybackPosition(mediaId, pos, dur)
            }
        }
    }

    private suspend fun applyEffectiveAudioForEpisodeId(episodeId: String, player: ExoPlayer) {
        val ep = loheDao.getEpisodeById(episodeId) ?: return
        val pod = loheDao.getPodcastById(ep.podcastId)
        currentIntroMs = (pod?.skipIntroSec ?: 0) * 1000L
        currentOutroMs = (pod?.skipOutroSec ?: 0) * 1000L
        outroHandledId = null
        if (currentIntroMs > 0L && player.currentMediaItem?.mediaId == episodeId &&
            player.currentPosition < currentIntroMs
        ) {
            val dur = player.duration
            if (dur == C.TIME_UNSET || dur > currentIntroMs + currentOutroMs) {
                player.seekTo(currentIntroMs)
            }
        }
        if (pod?.useCustomAudio == true) {
            player.playbackParameters = PlaybackParameters(pod.customSpeed.coerceIn(0.5f, 3.0f))
            player.skipSilenceEnabled = pod.customSkipSilence
            currentBoostDb = pod.customVolumeBoostDb
            applyLoudnessBoost(player.audioSessionId, pod.customVolumeBoostDb)
        } else {
            player.playbackParameters = PlaybackParameters(globalPlaybackSpeed.coerceIn(0.5f, 3.0f))
            player.skipSilenceEnabled = globalSkipSilence
            currentBoostDb = globalVolumeBoostDb
            applyLoudnessBoost(player.audioSessionId, globalVolumeBoostDb)
        }
    }

    private fun handleEpisodeCompletedInService(completedEpisodeId: String, player: ExoPlayer) {
        cacheResumePositionMs(completedEpisodeId, 0L)
        serviceScope.launch {
            if (markPlayedWhenFinished) {
                loheDao.markEpisodePlayed(completedEpisodeId, true)
            }
            if (autoArchivePolicy == "IMMEDIATELY") {
                loheDao.archiveAllPlayedNonStarred()
            }
            if (continuousPlayback) {
                val nextInQueue = loheDao.getUpNextEpisodes().first().firstOrNull { it.id != completedEpisodeId }
                if (nextInQueue != null) {
                    val (items, startIdx, startPosMs) = buildSharedQueueForEpisode(nextInQueue)
                    sessionPlayer?.setMediaItems(items, startIdx, startPosMs)
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        val action = intent?.action
        val player = exoPlayer
        if (player != null && action != null) {
            when (action) {
                ACTION_WIDGET_TOGGLE_PLAY_PAUSE -> handleWidgetPlayPause(player)
                ACTION_WIDGET_SKIP_BACK -> {
                    val target = (player.currentPosition - skipBackSeconds * 1000L).coerceAtLeast(0L)
                    player.seekTo(target)
                    syncWidgetFromPlayer(player)
                }
                ACTION_WIDGET_SKIP_FORWARD -> {
                    val dur = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                    val target = (player.currentPosition + skipForwardSeconds * 1000L).coerceAtMost(dur)
                    player.seekTo(target)
                    syncWidgetFromPlayer(player)
                }
            }
        }
        return result
    }

    private fun handleWidgetPlayPause(player: ExoPlayer) {
        if (player.mediaItemCount > 0) {
            if (player.isPlaying) player.pause() else player.play()
            syncWidgetFromPlayer(player)
            return
        }
        serviceScope.launch {
            val ep = resolveMostRecentEpisode()
            if (ep != null) {
                val (items, startIdx, startPosMs) = buildSharedQueueForEpisode(ep)
                sessionPlayer?.setMediaItems(items, startIdx, startPosMs)
                player.prepare()
                player.play()
                syncWidgetFromPlayer(player)
            }
        }
    }

    private suspend fun resolveMostRecentEpisode(): EpisodeEntity? {
        val lastId = runCatching { prefsRepo.settingsFlow.first().lastPlayingEpisodeId }.getOrDefault("")
        return (if (lastId.isNotBlank()) loheDao.getEpisodeById(lastId) else null)
            ?: loheDao.getInProgressEpisodes().first().firstOrNull()
            ?: loheDao.getUpNextEpisodes().first().firstOrNull()
            ?: loheDao.getActiveEpisodes().first().firstOrNull()
    }

    suspend fun buildSharedQueueForEpisode(targetEpisode: EpisodeEntity): Triple<List<MediaItem>, Int, Long> {
        val upNext = loheDao.getUpNextEpisodes().first()
        val episodesInQueue = if (upNext.any { it.id == targetEpisode.id }) {
            upNext.map { if (it.id == targetEpisode.id) targetEpisode else it }
        } else {
            listOf(targetEpisode) + upNext
        }
        val items = episodesInQueue.map(::buildPlayableEpisodeItem)
        val startIndex = episodesInQueue.indexOfFirst { it.id == targetEpisode.id }.coerceAtLeast(0)
        val startPositionMs = validResumePositionMs(targetEpisode)
        return Triple(items, startIndex, startPositionMs)
    }

    private suspend fun resolveRequestedMediaItems(requestedItems: List<MediaItem>): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        for (item in requestedItems) {
            val searchQuery = extractVoiceSearchQuery(item)
            val mediaId = item.mediaId
            when {
                mediaId.isBlank() && searchQuery != null -> {
                    out += resolveVoiceSearchEpisodes(searchQuery, item.requestMetadata.extras)
                        .map(::buildPlayableEpisodeItem)
                }
                mediaId == NODE_CONTINUE_LISTENING || mediaId == NODE_IN_PROGRESS_LEGACY -> {
                    out += loheDao.getInProgressEpisodes().first().map(::buildPlayableEpisodeItem)
                }
                mediaId == NODE_UP_NEXT -> {
                    out += loheDao.getUpNextEpisodes().first().map(::buildPlayableEpisodeItem)
                }
                mediaId == NODE_DOWNLOADS -> {
                    out += loheDao.getDownloadedEpisodes().first().map(::buildPlayableEpisodeItem)
                }
                mediaId.startsWith(PREFIX_PODCAST) -> {
                    val podId = mediaId.removePrefix(PREFIX_PODCAST)
                    out += loheDao.getEpisodesByPodcastId(podId).first().map(::buildPlayableEpisodeItem)
                }
                mediaId.startsWith(PREFIX_PLAYLIST) -> {
                    val plId = mediaId.removePrefix(PREFIX_PLAYLIST)
                    val pl = findPlaylistByIdOrRule(plId)
                    if (pl != null) {
                        val allEps = loheDao.getActiveEpisodes().first()
                        out += resolvePlaylistEpisodes(pl, allEps).map(::buildPlayableEpisodeItem)
                    }
                }
                else -> {
                    val ep = loheDao.getEpisodeById(mediaId)
                    if (ep != null) {
                        out += buildPlayableEpisodeItem(ep)
                    } else if (item.localConfiguration != null) {
                        out += item
                    }
                }
            }
        }
        return out
    }

    private suspend fun resolveQueueAndStartPosition(
        requestedItems: List<MediaItem>,
        requestedStartIndex: Int,
        requestedStartPositionMs: Long
    ): MediaSession.MediaItemsWithStartPosition {
        if (requestedItems.isEmpty()) {
            val fallback = resolveMostRecentEpisode()
            if (fallback != null) {
                val (items, idx, pos) = buildSharedQueueForEpisode(fallback)
                return MediaSession.MediaItemsWithStartPosition(items, idx, pos)
            }
            return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
        }

        // Single-item selection from Android Auto browse tree, voice search, or phone
        if (requestedItems.size == 1) {
            val single = requestedItems[0]
            val voiceQuery = extractVoiceSearchQuery(single)
            val mediaId = single.mediaId

            if (mediaId.isBlank() && voiceQuery != null) {
                val matchedEpisodes = resolveVoiceSearchEpisodes(voiceQuery, single.requestMetadata.extras)
                val targetEp = matchedEpisodes.firstOrNull() ?: resolveMostRecentEpisode()
                if (targetEp != null) {
                    val upNext = loheDao.getUpNextEpisodes().first()
                    val queueEps = (matchedEpisodes + upNext).distinctBy { it.id }
                    val items = queueEps.map(::buildPlayableEpisodeItem)
                    val startPosMs = if (requestedStartPositionMs != C.TIME_UNSET && requestedStartPositionMs > 0L) {
                        requestedStartPositionMs
                    } else {
                        validResumePositionMs(targetEp)
                    }
                    return MediaSession.MediaItemsWithStartPosition(items, 0, startPosMs)
                }
            }

            if (mediaId.startsWith(PREFIX_PODCAST) ||
                mediaId.startsWith(PREFIX_PLAYLIST) ||
                mediaId == NODE_CONTINUE_LISTENING ||
                mediaId == NODE_IN_PROGRESS_LEGACY ||
                mediaId == NODE_UP_NEXT ||
                mediaId == NODE_DOWNLOADS
            ) {
                val containerItems = resolveRequestedMediaItems(listOf(single))
                if (containerItems.isNotEmpty()) {
                    val firstEp = loheDao.getEpisodeById(containerItems[0].mediaId)
                    val startPosMs = if (requestedStartPositionMs != C.TIME_UNSET && requestedStartPositionMs > 0L) {
                        requestedStartPositionMs
                    } else {
                        firstEp?.let(::validResumePositionMs) ?: getCachedResumePositionMs(containerItems[0].mediaId)
                    }
                    return MediaSession.MediaItemsWithStartPosition(containerItems, 0, startPosMs)
                }
            }

            val ep = loheDao.getEpisodeById(mediaId)
            if (ep != null) {
                val (queueItems, startIdx, savedResumeMs) = buildSharedQueueForEpisode(ep)
                val effectiveStartPosMs = if (requestedStartPositionMs != C.TIME_UNSET && requestedStartPositionMs > 0L) {
                    requestedStartPositionMs
                } else {
                    savedResumeMs
                }
                return MediaSession.MediaItemsWithStartPosition(queueItems, startIdx, effectiveStartPosMs)
            }
        }

        // Multi-item queue selection
        val resolvedItems = resolveRequestedMediaItems(requestedItems)
        if (resolvedItems.isEmpty()) {
            return MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
        }
        val safeStartIndex = if (requestedStartIndex in resolvedItems.indices) requestedStartIndex else 0
        val targetMediaId = resolvedItems[safeStartIndex].mediaId
        val targetEp = loheDao.getEpisodeById(targetMediaId)
        val savedResumeMs = targetEp?.let(::validResumePositionMs) ?: getCachedResumePositionMs(targetMediaId)
        val effectiveStartPosMs = if (requestedStartPositionMs != C.TIME_UNSET && requestedStartPositionMs > 0L) {
            requestedStartPositionMs
        } else {
            savedResumeMs
        }
        return MediaSession.MediaItemsWithStartPosition(resolvedItems, safeStartIndex, effectiveStartPosMs)
    }

    private fun extractVoiceSearchQuery(item: MediaItem): String? {
        val direct = item.requestMetadata.searchQuery
        if (direct != null) return direct
        val extras = item.requestMetadata.extras ?: return null
        return extras.getString(SearchManager.QUERY)
            ?: extras.getString(MediaStore.EXTRA_MEDIA_TITLE)
            ?: extras.getString(MediaStore.EXTRA_MEDIA_ALBUM)
            ?: extras.getString(MediaStore.EXTRA_MEDIA_ARTIST)
    }

    private suspend fun resolveVoiceSearchEpisodes(query: String, extras: Bundle?): List<EpisodeEntity> {
        val q = query.trim()
        val activeEps = loheDao.getActiveEpisodes().first()
        val shows = loheDao.getSubscribedPodcasts().first()
        if (q.isBlank()) {
            val recent = resolveMostRecentEpisode()
            return listOfNotNull(recent) + loheDao.getUpNextEpisodes().first()
        }

        val titleExtras = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)?.trim().orEmpty()
        val artistExtras = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)?.trim().orEmpty()
        val effectiveTerms = listOf(q, titleExtras, artistExtras).filter { it.isNotBlank() }

        // 1. Direct episode title matches
        val directEpMatches = activeEps.filter { ep ->
            effectiveTerms.any { term ->
                ep.title.contains(term, ignoreCase = true)
            }
        }
        if (directEpMatches.isNotEmpty()) return directEpMatches

        // 2. Show title / author matches -> return that show's episodes (in-progress first, then newest)
        val matchingShowIds = shows.filter { pod ->
            effectiveTerms.any { term ->
                pod.title.contains(term, ignoreCase = true) ||
                    pod.author.contains(term, ignoreCase = true) ||
                    pod.category.contains(term, ignoreCase = true)
            }
        }.map { it.id }.toSet()

        if (matchingShowIds.isNotEmpty()) {
            val showEpisodes = activeEps.filter { it.podcastId in matchingShowIds }
            val inProgressFirst = showEpisodes.sortedWith(
                compareByDescending<EpisodeEntity> { it.resumePositionMs > 0L && !it.isPlayed }
                    .thenByDescending { !it.isPlayed }
                    .thenByDescending { it.publishedAt }
            )
            if (inProgressFirst.isNotEmpty()) return inProgressFirst
        }

        // 3. Episode podcastTitle or description matches
        val secondaryEpMatches = activeEps.filter { ep ->
            effectiveTerms.any { term ->
                ep.podcastTitle.contains(term, ignoreCase = true) ||
                    ep.descriptionHtml.contains(term, ignoreCase = true)
            }
        }
        if (secondaryEpMatches.isNotEmpty()) return secondaryEpMatches

        // 4. Playlist name matches
        val matchingPlaylist = getAllPlaylistsIncludingSmart().firstOrNull { pl ->
            effectiveTerms.any { term -> pl.name.contains(term, ignoreCase = true) }
        }
        if (matchingPlaylist != null) {
            return resolvePlaylistEpisodes(matchingPlaylist, activeEps)
        }

        return emptyList()
    }

    private fun syncWidgetFromPlayer(player: ExoPlayer) {
        val md = player.mediaMetadata
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val title = md.title?.toString().orEmpty()
        val podcast = md.artist?.toString().orEmpty()
        val isPlaying = player.isPlaying
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.takeIf { it > 0L } ?: 60_000L
        serviceScope.launch {
            LohePlayerWidget.syncSnapshotAndUpdate(
                context = applicationContext,
                episodeId = mediaId,
                episodeTitle = title,
                podcastTitle = podcast,
                isPlaying = isPlaying,
                positionMs = pos,
                durationMs = dur
            )
        }
    }

    suspend fun searchLibraryItems(query: String): List<MediaItem> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val tokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }

        val pods = loheDao.getSubscribedPodcasts().first()
            .filter { pod ->
                pod.title.contains(q, ignoreCase = true) ||
                    pod.author.contains(q, ignoreCase = true) ||
                    pod.category.contains(q, ignoreCase = true) ||
                    pod.description.contains(q, ignoreCase = true) ||
                    (tokens.isNotEmpty() && tokens.all { tok ->
                        pod.title.contains(tok, ignoreCase = true) ||
                            pod.author.contains(tok, ignoreCase = true)
                    })
            }
            .take(15)
            .map { pod -> buildPodcastFolderItem(pod, groupTitle = "Shows") }

        val eps = loheDao.getActiveEpisodes().first()
            .filter { ep ->
                ep.title.contains(q, ignoreCase = true) ||
                    ep.podcastTitle.contains(q, ignoreCase = true) ||
                    ep.descriptionHtml.contains(q, ignoreCase = true) ||
                    (tokens.isNotEmpty() && tokens.all { tok ->
                        ep.title.contains(tok, ignoreCase = true) ||
                            ep.podcastTitle.contains(tok, ignoreCase = true)
                    })
            }
            .take(25)
            .map { ep -> buildPlayableEpisodeItem(ep, groupTitle = "Episodes") }

        return pods + eps
    }

    private suspend fun getAllPlaylistsIncludingSmart(): List<PlaylistEntity> {
        val dbPlaylists = loheDao.getCustomPlaylists().first()
        val existingIds = dbPlaylists.map { it.id }.toSet()
        val existingSmartRules = dbPlaylists.mapNotNull { it.smartRuleType.takeIf(String::isNotBlank) }.toSet()
        val synthesizedSmart = SmartPlaylistType.entries
            .filter { it.id !in existingIds && it.name !in existingSmartRules }
            .mapIndexed { idx, smart ->
                PlaylistEntity(
                    id = smart.id,
                    name = smart.title,
                    folderName = "Smart Mixes",
                    smartRuleType = smart.name,
                    isPinned = idx < 2
                )
            }
        return dbPlaylists + synthesizedSmart
    }

    private suspend fun findPlaylistByIdOrRule(plId: String): PlaylistEntity? {
        val all = getAllPlaylistsIncludingSmart()
        return all.find {
            it.id == plId ||
                it.smartRuleType.equals(plId, ignoreCase = true)
        }
    }

    private fun resolvePlaylistEpisodes(playlist: PlaylistEntity, allEpisodes: List<EpisodeEntity>): List<EpisodeEntity> {
        val unplayedEpisodes = allEpisodes.filter { !it.isPlayed }
        val smart = SmartPlaylistType.entries.find {
            it.name.equals(playlist.smartRuleType, ignoreCase = true) ||
                it.id.equals(playlist.id, ignoreCase = true)
        }
        if (smart != null) {
            return when (smart) {
                SmartPlaylistType.EVERYTHING -> unplayedEpisodes.sortedByDescending { it.publishedAt }
                SmartPlaylistType.RECENT_RELEASES -> unplayedEpisodes.sortedByDescending { it.publishedAt }.take(25)
                SmartPlaylistType.IN_PROGRESS -> unplayedEpisodes.filter { it.resumePositionMs > 0L }
                SmartPlaylistType.UNPLAYED -> unplayedEpisodes
                SmartPlaylistType.DOWNLOADED -> unplayedEpisodes.filter { it.isDownloaded || it.localFilePath.isNotBlank() }
                SmartPlaylistType.STARRED -> unplayedEpisodes.filter { it.isStarred }
                SmartPlaylistType.SHORT_EPISODES -> unplayedEpisodes.filter { it.durationMs <= 25 * 60 * 1000L }
            }
        }
        val assignedPods = playlist.assignedPodcastIdList()
        val explicitEps = playlist.episodeIdList()
        return unplayedEpisodes.filter { it.id in explicitEps || it.podcastId in assignedPods }
            .sortedByDescending { it.publishedAt }
    }

    private fun paginateItems(items: List<MediaItem>, page: Int, pageSize: Int): ImmutableList<MediaItem> {
        if (items.isEmpty() || pageSize <= 0) return ImmutableList.of()
        val from = (page.coerceAtLeast(0) * pageSize).coerceAtMost(items.size)
        val to = (from + pageSize).coerceAtMost(items.size)
        return ImmutableList.copyOf(items.subList(from, to))
    }

    private fun buildBrowsableNode(mediaId: String, title: String, subtitle: String, mediaType: Int): MediaItem {
        val md = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(md)
            .build()
    }

    private fun buildPodcastFolderItem(podcast: PodcastEntity, groupTitle: String? = null): MediaItem {
        val extras = Bundle().apply {
            if (!groupTitle.isNullOrBlank()) {
                putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, groupTitle)
            }
        }
        val md = MediaMetadata.Builder()
            .setTitle(podcast.title)
            .setArtist(podcast.author)
            .setSubtitle(podcast.author.ifBlank { podcast.category })
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
            .setExtras(extras)
            .apply {
                if (podcast.artworkUrl.isNotBlank()) {
                    setArtworkUri(Uri.parse(podcast.artworkUrl))
                }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId("$PREFIX_PODCAST${podcast.id}")
            .setMediaMetadata(md)
            .build()
    }

    private fun buildPlaylistFolderItem(playlist: PlaylistEntity): MediaItem {
        val isSmart = SmartPlaylistType.entries.any {
            it.name.equals(playlist.smartRuleType, ignoreCase = true) ||
                it.id.equals(playlist.id, ignoreCase = true)
        }
        val subtitle = if (isSmart) {
            SmartPlaylistType.entries.find {
                it.name.equals(playlist.smartRuleType, ignoreCase = true) ||
                    it.id.equals(playlist.id, ignoreCase = true)
            }?.subtitle ?: "Smart Playlist"
        } else {
            playlist.folderName.ifBlank { "Custom Playlist" }
        }
        val md = MediaMetadata.Builder()
            .setTitle(playlist.name)
            .setSubtitle(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
            .build()
        return MediaItem.Builder()
            .setMediaId("$PREFIX_PLAYLIST${playlist.id}")
            .setMediaMetadata(md)
            .build()
    }

    fun buildPlayableEpisodeItem(episode: EpisodeEntity, groupTitle: String? = null): MediaItem {
        val resumePosMs = validResumePositionMs(episode)
        cacheResumePositionMs(episode.id, resumePosMs)

        val localFile = episode.localFilePath.takeIf { it.isNotBlank() }?.let(::File)
        val uri = if (localFile != null && localFile.exists() && localFile.length() > 0L) {
            Uri.fromFile(localFile)
        } else if (episode.localFilePath.startsWith("file://") || episode.localFilePath.startsWith("content://")) {
            Uri.parse(episode.localFilePath)
        } else {
            Uri.parse(episode.audioUrl)
        }

        val completionStatus = when {
            episode.isPlayed -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
            resumePosMs > 0L -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
            else -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
        }
        val completionPct = when {
            episode.isPlayed -> 1.0
            resumePosMs > 0L && episode.durationMs > 0L ->
                (resumePosMs.toDouble() / episode.durationMs.toDouble()).coerceIn(0.0, 1.0)
            else -> 0.0
        }
        val downloadStatus = if (episode.isDownloaded || episode.localFilePath.isNotBlank()) {
            2L // MediaDescriptionCompat.STATUS_DOWNLOADED
        } else {
            0L // MediaDescriptionCompat.STATUS_NOT_DOWNLOADED
        }

        val extras = Bundle().apply {
            putLong(EXTRA_RESUME_POSITION_MS, resumePosMs)
            putLong(EXTRA_DURATION_MS, episode.durationMs)
            putString(EXTRA_PODCAST_ID, episode.podcastId)
            putBoolean(EXTRA_IS_DOWNLOADED, episode.isDownloaded)
            putInt(MediaConstants.EXTRAS_KEY_COMPLETION_STATUS, completionStatus)
            putDouble(MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE, completionPct)
            putLong(MediaConstants.EXTRAS_KEY_DOWNLOAD_STATUS, downloadStatus)
            if (!groupTitle.isNullOrBlank()) {
                putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, groupTitle)
            }
        }

        val md = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.podcastTitle)
            .setSubtitle(episode.podcastTitle)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .setExtras(extras)
            .apply {
                if (episode.artworkUrl.isNotBlank()) {
                    setArtworkUri(Uri.parse(episode.artworkUrl))
                }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(uri)
            .setMediaMetadata(md)
            .build()
    }

    private fun registerHeadsetAutoResumeListeners(player: ExoPlayer) {
        if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) return

        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager = am

        val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                val now = SystemClock.elapsedRealtime()
                if (now - serviceCreatedElapsedMs < 1200L) return
                val hasHeadsetOutput = addedDevices.any { device ->
                    device.isSink && (
                        device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            device.type == AudioDeviceInfo.TYPE_HEARING_AID
                        )
                }
                if (hasHeadsetOutput) {
                    triggerHeadsetAutoResume(player)
                }
            }
        }
        runCatching {
            am?.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
            audioDeviceCallback = deviceCallback
        }

        val plugReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_HEADSET_PLUG) return
                if (isInitialStickyBroadcast) return
                if (intent.getIntExtra("state", 0) == 1) {
                    triggerHeadsetAutoResume(player)
                }
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                plugReceiver,
                IntentFilter(Intent.ACTION_HEADSET_PLUG),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            headsetPlugReceiver = plugReceiver
        }
    }

    private fun triggerHeadsetAutoResume(player: ExoPlayer) {
        val now = SystemClock.elapsedRealtime()
        if (!resumeOnHeadsetConnect) return
        if (!wasPausedAfterActivePlaybackInSession || isCarOrExternalBrowserConnected) return
        if (now - lastHeadsetResumeElapsedMs < 1500L) return
        if (player.mediaItemCount == 0 || player.isPlaying) return
        lastHeadsetResumeElapsedMs = now

        val rewindMs = autoRewindSeconds.coerceAtLeast(0) * 1000L
        if (rewindMs > 0L && player.currentPosition > 0L) {
            player.seekTo((player.currentPosition - rewindMs).coerceAtLeast(0L))
        }
        player.play()
    }

    private fun applyLoudnessBoost(audioSessionId: Int, db: Int) {
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0 && db > 0) {
                val enhancer = LoudnessEnhancer(audioSessionId)
                enhancer.setTargetGain(db * 100)
                enhancer.enabled = true
                loudnessEnhancer = enhancer
            }
        } catch (_: Throwable) {
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (_activeServiceFlow.value === this) {
            _activeServiceFlow.value = null
        }
        progressPersistJob?.cancel()
        audioDeviceCallback?.let { cb ->
            runCatching { audioManager?.unregisterAudioDeviceCallback(cb) }
        }
        audioDeviceCallback = null
        headsetPlugReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        headsetPlugReceiver = null

        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (_: Throwable) {
        }
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        sessionPlayer = null
        exoPlayer = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
