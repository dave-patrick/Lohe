package com.example.playback

import android.app.PendingIntent
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
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.example.MainActivity
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

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

        private const val NODE_ROOT = "lohe_root"
        private const val NODE_UP_NEXT = "cat_up_next"
        private const val NODE_IN_PROGRESS = "cat_in_progress"
        private const val NODE_PODCASTS = "cat_podcasts"
        private const val NODE_PLAYLISTS = "cat_playlists"
        private const val PREFIX_PODCAST = "pod_"
        private const val PREFIX_PLAYLIST = "pl_"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaLibrarySession: MediaLibrarySession? = null
    private var exoPlayer: ExoPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentBoostDb: Int = 0

    @Volatile
    private var resumeOnHeadsetConnect: Boolean = true
    @Volatile
    private var autoRewindSeconds: Int = 3
    @Volatile
    private var skipBackSeconds: Int = 15
    @Volatile
    private var skipForwardSeconds: Int = 30
    private var serviceCreatedElapsedMs: Long = 0L
    private var lastHeadsetResumeElapsedMs: Long = 0L

    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var headsetPlugReceiver: BroadcastReceiver? = null
    private lateinit var loheDao: LoheDao
    private lateinit var prefsRepo: LohePreferencesRepository

    override fun onCreate() {
        super.onCreate()
        serviceCreatedElapsedMs = SystemClock.elapsedRealtime()
        loheDao = LoheDatabase.getInstance(applicationContext).loheDao()
        prefsRepo = LohePreferencesRepository(applicationContext)

        serviceScope.launch {
            prefsRepo.settingsFlow.collectLatest { settings ->
                resumeOnHeadsetConnect = settings.resumeOnHeadsetConnect
                autoRewindSeconds = settings.autoRewindSeconds
                skipBackSeconds = settings.skipBackSeconds
                skipForwardSeconds = settings.skipForwardSeconds
            }
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                applyLoudnessBoost(audioSessionId, currentBoostDb)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncWidgetFromPlayer(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncWidgetFromPlayer(player)
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
                        player.skipSilenceEnabled = enabled
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    ACTION_SET_VOLUME_BOOST -> {
                        val db = args.getInt(EXTRA_BOOST_DB, 0)
                        currentBoostDb = db
                        applyLoudnessBoost(player.audioSessionId, db)
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
                val rootItem = buildBrowsableNode(
                    mediaId = NODE_ROOT,
                    title = "Lohe Podcasts",
                    subtitle = "Listen Offline & Synced",
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
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
                                buildBrowsableNode(NODE_UP_NEXT, "Up Next", "Queued episodes", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                                buildBrowsableNode(NODE_IN_PROGRESS, "In Progress", "Continue listening", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                                buildBrowsableNode(NODE_PODCASTS, "Subscriptions", "Subscribed podcasts", MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS),
                                buildBrowsableNode(NODE_PLAYLISTS, "Playlists", "Smart & custom mixes", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                            )
                            parentId == NODE_UP_NEXT -> {
                                loheDao.getUpNextEpisodes().first().map(::buildPlayableEpisodeItem)
                            }
                            parentId == NODE_IN_PROGRESS -> {
                                loheDao.getInProgressEpisodes().first().map(::buildPlayableEpisodeItem)
                            }
                            parentId == NODE_PODCASTS -> {
                                loheDao.getSubscribedPodcasts().first().map(::buildPodcastFolderItem)
                            }
                            parentId == NODE_PLAYLISTS -> {
                                loheDao.getCustomPlaylists().first().map(::buildPlaylistFolderItem)
                            }
                            parentId.startsWith(PREFIX_PODCAST) -> {
                                val podId = parentId.removePrefix(PREFIX_PODCAST)
                                loheDao.getActiveEpisodes().first()
                                    .filter { it.podcastId == podId }
                                    .map(::buildPlayableEpisodeItem)
                            }
                            parentId.startsWith(PREFIX_PLAYLIST) -> {
                                val plId = parentId.removePrefix(PREFIX_PLAYLIST)
                                val pl = loheDao.getCustomPlaylists().first().find { it.id == plId }
                                val allEps = loheDao.getActiveEpisodes().first()
                                if (pl != null) {
                                    resolvePlaylistEpisodes(pl, allEps).map(::buildPlayableEpisodeItem)
                                } else emptyList()
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
                        mediaId == NODE_ROOT -> buildBrowsableNode(NODE_ROOT, "Lohe Podcasts", "Home", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        mediaId.startsWith(PREFIX_PODCAST) -> loheDao.getPodcastById(mediaId.removePrefix(PREFIX_PODCAST))?.let(::buildPodcastFolderItem)
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
                    val resolved = mediaItems.map { item ->
                        if (item.localConfiguration != null) {
                            item
                        } else {
                            loheDao.getEpisodeById(item.mediaId)?.let(::buildPlayableEpisodeItem) ?: item
                        }
                    }
                    future.set(resolved)
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

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(pendingIntent)
            .build()
        exoPlayer = player

        registerHeadsetAutoResumeListeners(player)
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
            val lastId = runCatching { prefsRepo.settingsFlow.first().lastPlayingEpisodeId }.getOrDefault("")
            val ep = (if (lastId.isNotBlank()) loheDao.getEpisodeById(lastId) else null)
                ?: loheDao.getInProgressEpisodes().first().firstOrNull()
                ?: loheDao.getUpNextEpisodes().first().firstOrNull()
                ?: loheDao.getActiveEpisodes().first().firstOrNull()
            if (ep != null) {
                player.setMediaItem(buildPlayableEpisodeItem(ep))
                player.prepare()
                if (ep.resumePositionMs in 1L until ep.durationMs) {
                    player.seekTo(ep.resumePositionMs)
                }
                player.play()
                syncWidgetFromPlayer(player)
            }
        }
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

    private suspend fun searchLibraryItems(query: String): List<MediaItem> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val eps = loheDao.getActiveEpisodes().first()
            .filter { it.title.contains(q, ignoreCase = true) || it.podcastTitle.contains(q, ignoreCase = true) }
            .take(20)
            .map(::buildPlayableEpisodeItem)
        val pods = loheDao.getSubscribedPodcasts().first()
            .filter { it.title.contains(q, ignoreCase = true) || it.author.contains(q, ignoreCase = true) }
            .take(10)
            .map(::buildPodcastFolderItem)
        return eps + pods
    }

    private fun resolvePlaylistEpisodes(playlist: PlaylistEntity, allEpisodes: List<EpisodeEntity>): List<EpisodeEntity> {
        val smart = SmartPlaylistType.entries.find { it.name == playlist.smartRuleType }
        if (smart != null) {
            return when (smart) {
                SmartPlaylistType.EVERYTHING -> allEpisodes.sortedByDescending { it.publishedAt }
                SmartPlaylistType.RECENT_RELEASES -> allEpisodes.sortedByDescending { it.publishedAt }.take(25)
                SmartPlaylistType.IN_PROGRESS -> allEpisodes.filter { it.resumePositionMs > 0L && !it.isPlayed }
                SmartPlaylistType.UNPLAYED -> allEpisodes.filter { !it.isPlayed }
                SmartPlaylistType.DOWNLOADED -> allEpisodes.filter { it.isDownloaded }
                SmartPlaylistType.STARRED -> allEpisodes.filter { it.isStarred }
                SmartPlaylistType.SHORT_EPISODES -> allEpisodes.filter { it.durationMs <= 25 * 60 * 1000L }
            }
        }
        val assignedPods = playlist.assignedPodcastIdList()
        val explicitEps = playlist.episodeIdList()
        return allEpisodes.filter { it.id in explicitEps || it.podcastId in assignedPods }
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

    private fun buildPodcastFolderItem(podcast: PodcastEntity): MediaItem {
        val md = MediaMetadata.Builder()
            .setTitle(podcast.title)
            .setArtist(podcast.author)
            .setSubtitle(podcast.category)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
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
        val md = MediaMetadata.Builder()
            .setTitle(playlist.name)
            .setSubtitle(playlist.folderName)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
            .build()
        return MediaItem.Builder()
            .setMediaId("$PREFIX_PLAYLIST${playlist.id}")
            .setMediaMetadata(md)
            .build()
    }

    private fun buildPlayableEpisodeItem(episode: EpisodeEntity): MediaItem {
        val localFile = episode.localFilePath.takeIf { it.isNotBlank() }?.let(::File)
        val uri = if (localFile != null && localFile.exists() && localFile.length() > 0L) {
            Uri.fromFile(localFile)
        } else {
            Uri.parse(episode.audioUrl)
        }
        val md = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.podcastTitle)
            .setSubtitle(episode.podcastTitle)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
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
                            device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
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
        exoPlayer = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
