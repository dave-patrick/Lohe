package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.sync.DriveSyncLocalJournal
import com.example.ui.theme.QueuePresetColor
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.loheDataStore: DataStore<Preferences> by preferencesDataStore(name = "lohe_settings")

enum class ListeningHistoryRange(
    val days: Int,
    val label: String,
    val shortLabel: String
) {
    DAYS_7(7, "7 days", "7D"),
    DAYS_14(14, "14 days", "14D"),
    DAYS_30(30, "30 days", "30D"),
    DAYS_90(90, "90 days", "90D"),
    ALL_TIME(0, "All time", "ALL");

    companion object {
        fun fromDays(days: Int): ListeningHistoryRange =
            entries.firstOrNull { it.days == days } ?: DAYS_14
    }
}

enum class CloudSyncProvider(
    val title: String,
    val subtitle: String,
    val fileTag: String
) {
    GOOGLE_DRIVE(
        title = "Google Drive",
        subtitle = "Sync playlists, Up Next, podcasts & episode status via Drive AppData",
        fileTag = "gdrive"
    ),
    ONEDRIVE(
        title = "Microsoft OneDrive",
        subtitle = "Link a OneDrive sync file for playlists, Up Next, podcasts & episode status",
        fileTag = "onedrive"
    ),
    DROPBOX(
        title = "Dropbox",
        subtitle = "Link a Dropbox sync file for playlists, Up Next, podcasts & episode status",
        fileTag = "dropbox"
    ),
    WEBDAV(
        title = "Nextcloud / WebDAV",
        subtitle = "Direct HTTPS sync to Nextcloud, ownCloud, Box WebDAV, Koofr & NAS",
        fileTag = "webdav"
    ),
    OTHER_CLOUD(
        title = "Box / pCloud / Other",
        subtitle = "Link a sync file in Box, pCloud, Syncthing or any Android drive app",
        fileTag = "cloud"
    );

    companion object {
        fun defaultProvider(): CloudSyncProvider =
            if (com.example.edition.Edition.HAS_PRO) GOOGLE_DRIVE else WEBDAV

        fun availableProviders(): List<CloudSyncProvider> =
            if (com.example.edition.Edition.HAS_PRO) entries else entries.filter { it != GOOGLE_DRIVE }

        fun fromName(name: String?): CloudSyncProvider {
            val resolved = entries.firstOrNull { it.name == name } ?: defaultProvider()
            return if (!com.example.edition.Edition.HAS_PRO && resolved == GOOGLE_DRIVE) WEBDAV else resolved
        }
    }
}

enum class HomeCardType(
    val title: String,
    val description: String,
    val isPro: Boolean = false,
    val defaultEnabled: Boolean = false
) {
    CONTINUE_LISTENING(
        title = "Continue Listening",
        description = "Resume card for the episode you were last in the middle of",
        isPro = false,
        defaultEnabled = true
    ),
    YOUR_SIGNAL(
        title = "Your Signal",
        description = "Played ratio gauge, time played, time saved & remaining backlog",
        isPro = false,
        defaultEnabled = true
    ),
    LISTENING_ACTIVITY(
        title = "Listening History",
        description = "Listening timeline with daily average, peak period & streak",
        isPro = false,
        defaultEnabled = true
    ),
    LIBRARY_DETAILS(
        title = "Library Details",
        description = "Unplayed spotlight, total episodes, played, in progress, subscriptions & playlists",
        isPro = false,
        defaultEnabled = true
    ),
    MOST_EPISODES(
        title = "Most Episodes",
        description = "Top shows in your library ranked by total episode count",
        isPro = true,
        defaultEnabled = true
    ),
    MOST_LISTENED(
        title = "Most Listened Podcasts",
        description = "Top shows ranked by total listening time & completed episodes",
        isPro = true,
        defaultEnabled = true
    ),
    UP_NEXT_SPOTLIGHT(
        title = "Up Next Spotlight",
        description = "Quick preview of your upcoming queue with total remaining time",
        isPro = true,
        defaultEnabled = false
    ),
    RECENT_EPISODES(
        title = "Recent Dispatches",
        description = "Latest episodes published across your subscribed shows",
        isPro = false,
        defaultEnabled = false
    );

    companion object {
        fun defaultEnabledFor(isProUnlocked: Boolean): Set<HomeCardType> =
            entries.filter { it.defaultEnabled && (!it.isPro || isProUnlocked) }.toSet()

        /** Saved order first; any card missing from the saved list (e.g. added later) follows in default order. */
        fun orderFromCsv(csv: String?): List<HomeCardType> {
            val saved = csv.orEmpty().split(",")
                .mapNotNull { token -> entries.firstOrNull { it.name == token.trim() } }
                .distinct()
            return saved + entries.filter { it !in saved }
        }

        fun orderToCsv(order: List<HomeCardType>): String = order.joinToString(",") { it.name }

        fun fromCsv(csv: String?, isProUnlocked: Boolean): Set<HomeCardType> {
            if (csv == null) return defaultEnabledFor(isProUnlocked)
            if (csv.isBlank()) return emptySet()
            return csv.split(",")
                .mapNotNull { token -> entries.firstOrNull { it.name == token.trim() } }
                .filter { !it.isPro || isProUnlocked }
                .toSet()
        }
    }
}

enum class EpisodeSwipeAction(val label: String, val shortLabel: String) {
    UP_NEXT_CHOOSE("Up Next (Choose Top or Bottom)", "Up Next"),
    PLAY_NEXT("Play Next (Top of Queue)", "Play Next"),
    PLAY_LAST("Play Last (Bottom of Queue)", "Play Last"),
    MARK_PLAYED("Mark Played / Unplayed", "Played"),
    DOWNLOAD("Download / Remove Cache", "Download"),
    BOOKMARK("Bookmark / Star", "Bookmark"),
    NONE("Disabled", "Off");

    companion object {
        fun fromName(name: String?, fallback: EpisodeSwipeAction): EpisodeSwipeAction =
            entries.firstOrNull { it.name == name } ?: fallback
    }
}

enum class PodcastSortOption(val label: String, val shortLabel: String) {
    TITLE_ASC("Name (A–Z)", "A–Z"),
    TITLE_DESC("Name (Z–A)", "Z–A"),
    LATEST_EPISODE("Latest Episode", "Newest"),
    MOST_UNPLAYED("Most Unplayed", "Most Unplayed"),
    LEAST_UNPLAYED("Least Unplayed", "Least Unplayed"),
    MOST_EPISODES("Most Episodes", "Episodes"),
    MOST_LISTENED("Most Listened", "Listened"),
    RECENTLY_UPDATED("Recently Updated", "Updated"),
    CATEGORY_ASC("Category (A–Z)", "Category");

    companion object {
        fun fromName(name: String?): PodcastSortOption =
            entries.firstOrNull { it.name == name } ?: TITLE_ASC
    }
}

enum class PodcastFilterOption(val label: String) {
    ALL("All"),
    UNPLAYED("Unplayed"),
    IN_PROGRESS("In Progress"),
    DOWNLOADED("Downloaded"),
    STARRED("Starred"),
    AUTO_QUEUE("Auto-Queue"),
    CUSTOM_AUDIO("Custom Audio"),
    CAUGHT_UP("Caught Up");

    companion object {
        fun fromName(name: String?): PodcastFilterOption =
            entries.firstOrNull { it.name == name } ?: ALL
    }
}

enum class ToastDurationOption(val label: String, val shortLabel: String, val durationMs: Long) {
    SHORT("Short (2s)", "2s", 2_000L),
    MEDIUM("Medium (3.5s)", "3.5s", 3_500L),
    LONG("Long (6s)", "6s", 6_000L),
    PERSISTENT("Until Dismissed", "Manual", 0L);

    companion object {
        fun fromName(name: String?): ToastDurationOption =
            entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

enum class ToastVisualStyle(val label: String, val description: String) {
    PILL("Floating Pill", "Compact capsule with clean subtle border"),
    CARD("Elevated Card", "Full-width tonal card with Dismiss action"),
    MINIMAL("Minimal Bar", "Sleek dark strip with a blue left accent"),
    ACCENT("Electric Blue", "High-contrast electric blue notification banner");

    companion object {
        fun fromName(name: String?): ToastVisualStyle =
            entries.firstOrNull { it.name == name } ?: PILL
    }
}

enum class ToastPlacement(val label: String) {
    TOP("Top"),
    CENTER("Center"),
    BOTTOM("Bottom");

    companion object {
        fun fromName(name: String?): ToastPlacement =
            entries.firstOrNull { it.name == name } ?: BOTTOM
    }
}

data class LoheUserSettings(
    val themePreference: ThemePreference = ThemePreference.DARK,
    val reducedMotion: Boolean = false,
    val upNextIndicatorEnabled: Boolean = true,
    val queueIndicatorStyle: QueueIndicatorStyle = QueueIndicatorStyle.DOT,
    val queueIndicatorSize: QueueIndicatorSize = QueueIndicatorSize.SMALL,
    val queuePresetColor: QueuePresetColor = QueuePresetColor.BLUE,
    val useCustomHexColor: Boolean = false,
    val customHexColor: String = "#008EFF",
    val playbackSpeed: Float = 1.0f,
    val skipBackSeconds: Int = 15,
    val skipForwardSeconds: Int = 30,
    val skipSilenceEnabled: Boolean = false,
    val volumeBoostDb: Int = 0, // 0 = Off, 3 = Mild (+3dB), 6 = Medium (+6dB), 10 = Max (+10dB)
    val continuousPlayback: Boolean = true,
    val markPlayedWhenFinished: Boolean = true,
    val resumeOnHeadsetConnect: Boolean = true,
    val autoRewindSeconds: Int = 3,
    val floatingMiniPlayerEnabled: Boolean = false,
    val floatingButtonSizeDp: Int = 56,
    val floatingTransparencyPct: Int = 25,
    val autoArchivePolicy: String = "AFTER_24H", // IMMEDIATELY, AFTER_24H, AFTER_7D, NEVER
    val keepStarredExcluded: Boolean = true,
    val episodeLimitPerShow: Int = 0, // 0 = All episodes
    val autoDownloadWifi: Boolean = true,
    val autoDownloadUpNext: Boolean = true,
    val autoDownloadQueueDepth: Int = 5,
    val isProUnlocked: Boolean = false, // Simulated one-time local test upgrade (android.test.purchased)
    val cloudSyncAutoBackup: Boolean = false,
    val cloudSyncProvider: CloudSyncProvider = CloudSyncProvider.defaultProvider(),
    val webDavServerUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val safSyncUri: String = "",
    val safSyncProviderName: String = "",
    val safSyncDisplayName: String = "",
    val driveSyncStateJson: String = "",
    val lastCloudSyncSummary: String = "Not connected • Select a cloud drive below",
    val lastPlayingEpisodeId: String = "",
    val playlistsGridView: Boolean = true,
    val dailyListeningMinutes: List<Int> = List(90) { 0 },
    val totalTimeSavedMs: Long = 0L,
    val sleepShakeToExtendEnabled: Boolean = true,
    val newEpisodeNotificationsEnabled: Boolean = true,
    val backgroundSyncEnabled: Boolean = true,
    val backgroundSyncIntervalHours: Int = 6,
    val autoDeletePlayedPolicy: String = "AFTER_24H", // NEVER, IMMEDIATELY, AFTER_24H, AFTER_7D
    val maxCacheSizeMb: Int = 1024, // 0 = Unlimited, 500, 1024, 2048
    val enabledHomeCards: Set<HomeCardType> = HomeCardType.defaultEnabledFor(isProUnlocked = false),
    val homeCardOrder: List<HomeCardType> = HomeCardType.entries.toList(),
    val listeningHistoryRange: ListeningHistoryRange = ListeningHistoryRange.DAYS_14,
    val swipeRightAction: EpisodeSwipeAction = EpisodeSwipeAction.UP_NEXT_CHOOSE,
    val swipeLeftAction: EpisodeSwipeAction = EpisodeSwipeAction.MARK_PLAYED,
    val podcastSortOption: PodcastSortOption = PodcastSortOption.TITLE_ASC,
    val podcastFilterOption: PodcastFilterOption = PodcastFilterOption.ALL,
    val toastNotificationsEnabled: Boolean = true,
    val toastDuration: ToastDurationOption = ToastDurationOption.MEDIUM,
    val toastStyle: ToastVisualStyle = ToastVisualStyle.PILL,
    val toastPlacement: ToastPlacement = ToastPlacement.BOTTOM
)

class LohePreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_preference")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val INDICATOR_ENABLED = booleanPreferencesKey("up_next_indicator_enabled")
        val INDICATOR_STYLE = stringPreferencesKey("queue_indicator_style")
        val INDICATOR_SIZE = stringPreferencesKey("queue_indicator_size")
        val PRESET_COLOR = stringPreferencesKey("queue_preset_color")
        val USE_CUSTOM_HEX = booleanPreferencesKey("use_custom_hex_color")
        val CUSTOM_HEX = stringPreferencesKey("custom_hex_color")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SKIP_BACK = intPreferencesKey("skip_back_seconds")
        val SKIP_FORWARD = intPreferencesKey("skip_forward_seconds")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence_enabled")
        val VOLUME_BOOST_DB = intPreferencesKey("volume_boost_db")
        val CONTINUOUS_PLAYBACK = booleanPreferencesKey("continuous_playback")
        val MARK_PLAYED_FINISHED = booleanPreferencesKey("mark_played_when_finished")
        val RESUME_HEADSET = booleanPreferencesKey("resume_on_headset_connect")
        val AUTO_REWIND_SEC = intPreferencesKey("auto_rewind_seconds")
        val FLOATING_MINI_PLAYER = booleanPreferencesKey("floating_mini_player")
        val FLOATING_SIZE_DP = intPreferencesKey("floating_button_size_dp")
        val FLOATING_TRANSPARENCY = intPreferencesKey("floating_transparency_pct")
        val AUTO_ARCHIVE_POLICY = stringPreferencesKey("auto_archive_policy")
        val KEEP_STARRED = booleanPreferencesKey("keep_starred_excluded")
        val EPISODE_LIMIT = intPreferencesKey("episode_limit_per_show")
        val AUTO_DOWNLOAD_WIFI = booleanPreferencesKey("auto_download_wifi")
        val AUTO_DOWNLOAD_UP_NEXT = booleanPreferencesKey("auto_download_up_next")
        val AUTO_DOWNLOAD_DEPTH = intPreferencesKey("auto_download_queue_depth")
        val IS_PRO_UNLOCKED = booleanPreferencesKey("is_pro_unlocked")
        val CLOUD_SYNC_AUTO = booleanPreferencesKey("cloud_sync_auto_backup")
        val CLOUD_SYNC_PROVIDER = stringPreferencesKey("cloud_sync_provider")
        val WEBDAV_SERVER_URL = stringPreferencesKey("webdav_server_url")
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val SAF_SYNC_URI = stringPreferencesKey("saf_sync_uri")
        val SAF_SYNC_PROVIDER = stringPreferencesKey("saf_sync_provider")
        val SAF_SYNC_NAME = stringPreferencesKey("saf_sync_display_name")
        val DRIVE_SYNC_STATE_JSON = stringPreferencesKey("drive_sync_state_json")
        val LAST_CLOUD_SYNC = stringPreferencesKey("last_cloud_sync_summary")
        val LAST_PLAYING_EPISODE_ID = stringPreferencesKey("last_playing_episode_id")
        val PLAYLISTS_GRID_VIEW = booleanPreferencesKey("playlists_grid_view")
        val DAILY_LISTENING_CSV = stringPreferencesKey("daily_listening_csv")
        val LAST_LISTENING_EPOCH_DAY = longPreferencesKey("last_listening_epoch_day")
        val TOTAL_TIME_SAVED_MS = longPreferencesKey("total_time_saved_ms")
        val SLEEP_SHAKE_EXTEND = booleanPreferencesKey("sleep_shake_to_extend")
        val NEW_EP_NOTIFICATIONS = booleanPreferencesKey("new_episode_notifications")
        val BG_SYNC_ENABLED = booleanPreferencesKey("background_sync_enabled")
        val BG_SYNC_INTERVAL_H = intPreferencesKey("background_sync_interval_hours")
        val AUTO_DELETE_PLAYED = stringPreferencesKey("auto_delete_played_policy")
        val MAX_CACHE_MB = intPreferencesKey("max_cache_size_mb")
        val HOME_CARDS_CSV = stringPreferencesKey("enabled_home_cards_csv")
        val HOME_CARD_ORDER_CSV = stringPreferencesKey("home_card_order_csv")
        val LISTENING_HISTORY_DAYS = intPreferencesKey("listening_history_days")
        val SWIPE_RIGHT_ACTION = stringPreferencesKey("swipe_right_action")
        val SWIPE_LEFT_ACTION = stringPreferencesKey("swipe_left_action")
        val PODCAST_SORT_OPTION = stringPreferencesKey("podcast_sort_option")
        val PODCAST_FILTER_OPTION = stringPreferencesKey("podcast_filter_option")
        val TOAST_ENABLED = booleanPreferencesKey("toast_notifications_enabled")
        val TOAST_DURATION = stringPreferencesKey("toast_duration_option")
        val TOAST_STYLE = stringPreferencesKey("toast_visual_style")
        val TOAST_PLACEMENT = stringPreferencesKey("toast_placement")
    }

    val settingsFlow: Flow<LoheUserSettings> = context.loheDataStore.data.map { prefs ->
        val isPro = prefs[Keys.IS_PRO_UNLOCKED] ?: false
        val rawStyle = QueueIndicatorStyle.fromName(prefs[Keys.INDICATOR_STYLE] ?: QueueIndicatorStyle.DOT.name)
        val effectiveStyle = if (rawStyle.isPro && !isPro) QueueIndicatorStyle.DOT else rawStyle
        val legacyFakeSeed = listOf(12, 18, 8, 24, 15, 32, 22, 14, 19, 28, 11, 16, 25, 20)
        val rawDays = (prefs[Keys.DAILY_LISTENING_CSV] ?: "")
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .let { list ->
                when {
                    list.isEmpty() || list == legacyFakeSeed -> List(90) { 0 }
                    list.size < 90 -> List(90 - list.size) { 0 } + list
                    else -> list.takeLast(90)
                }
            }
        val todayEpochDay = System.currentTimeMillis() / 86_400_000L
        val lastEpochDay = prefs[Keys.LAST_LISTENING_EPOCH_DAY] ?: todayEpochDay
        val dayDelta = (todayEpochDay - lastEpochDay).toInt().coerceAtLeast(0)
        val parsedDays = when {
            dayDelta == 0 -> rawDays
            dayDelta >= 90 -> List(90) { 0 }
            else -> rawDays.drop(dayDelta) + List(dayDelta) { 0 }
        }

        LoheUserSettings(
            themePreference = runCatching {
                ThemePreference.valueOf(prefs[Keys.THEME] ?: ThemePreference.DARK.name)
            }.getOrDefault(ThemePreference.DARK),
            reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
            upNextIndicatorEnabled = prefs[Keys.INDICATOR_ENABLED] ?: true,
            queueIndicatorStyle = effectiveStyle,
            queueIndicatorSize = QueueIndicatorSize.fromName(prefs[Keys.INDICATOR_SIZE] ?: QueueIndicatorSize.SMALL.name),
            queuePresetColor = QueuePresetColor.fromName(prefs[Keys.PRESET_COLOR] ?: QueuePresetColor.BLUE.name),
            useCustomHexColor = (prefs[Keys.USE_CUSTOM_HEX] ?: false) && isPro,
            customHexColor = prefs[Keys.CUSTOM_HEX] ?: "#008EFF",
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
            skipBackSeconds = prefs[Keys.SKIP_BACK] ?: 15,
            skipForwardSeconds = prefs[Keys.SKIP_FORWARD] ?: 30,
            skipSilenceEnabled = prefs[Keys.SKIP_SILENCE] ?: false,
            volumeBoostDb = prefs[Keys.VOLUME_BOOST_DB] ?: 0,
            continuousPlayback = prefs[Keys.CONTINUOUS_PLAYBACK] ?: true,
            markPlayedWhenFinished = prefs[Keys.MARK_PLAYED_FINISHED] ?: true,
            resumeOnHeadsetConnect = prefs[Keys.RESUME_HEADSET] ?: true,
            autoRewindSeconds = prefs[Keys.AUTO_REWIND_SEC] ?: 3,
            floatingMiniPlayerEnabled = (prefs[Keys.FLOATING_MINI_PLAYER] ?: false) && isPro,
            floatingButtonSizeDp = prefs[Keys.FLOATING_SIZE_DP] ?: 56,
            floatingTransparencyPct = prefs[Keys.FLOATING_TRANSPARENCY] ?: 25,
            autoArchivePolicy = prefs[Keys.AUTO_ARCHIVE_POLICY] ?: "AFTER_24H",
            keepStarredExcluded = prefs[Keys.KEEP_STARRED] ?: true,
            episodeLimitPerShow = prefs[Keys.EPISODE_LIMIT] ?: 0,
            autoDownloadWifi = prefs[Keys.AUTO_DOWNLOAD_WIFI] ?: true,
            autoDownloadUpNext = prefs[Keys.AUTO_DOWNLOAD_UP_NEXT] ?: true,
            autoDownloadQueueDepth = prefs[Keys.AUTO_DOWNLOAD_DEPTH] ?: 5,
            isProUnlocked = isPro,
            cloudSyncAutoBackup = (prefs[Keys.CLOUD_SYNC_AUTO] ?: false) && (isPro || !com.example.edition.Edition.HAS_PRO),
            cloudSyncProvider = CloudSyncProvider.fromName(prefs[Keys.CLOUD_SYNC_PROVIDER]),
            webDavServerUrl = prefs[Keys.WEBDAV_SERVER_URL] ?: "",
            webDavUsername = prefs[Keys.WEBDAV_USERNAME] ?: "",
            webDavPassword = prefs[Keys.WEBDAV_PASSWORD] ?: "",
            safSyncUri = prefs[Keys.SAF_SYNC_URI] ?: "",
            safSyncProviderName = prefs[Keys.SAF_SYNC_PROVIDER] ?: "",
            safSyncDisplayName = prefs[Keys.SAF_SYNC_NAME] ?: "",
            driveSyncStateJson = prefs[Keys.DRIVE_SYNC_STATE_JSON] ?: "",
            lastCloudSyncSummary = prefs[Keys.LAST_CLOUD_SYNC]
                ?: "Not connected • Select a cloud drive below",
            lastPlayingEpisodeId = prefs[Keys.LAST_PLAYING_EPISODE_ID] ?: "",
            playlistsGridView = prefs[Keys.PLAYLISTS_GRID_VIEW] ?: true,
            dailyListeningMinutes = parsedDays,
            totalTimeSavedMs = prefs[Keys.TOTAL_TIME_SAVED_MS] ?: 0L,
            sleepShakeToExtendEnabled = prefs[Keys.SLEEP_SHAKE_EXTEND] ?: true,
            newEpisodeNotificationsEnabled = prefs[Keys.NEW_EP_NOTIFICATIONS] ?: true,
            backgroundSyncEnabled = prefs[Keys.BG_SYNC_ENABLED] ?: true,
            backgroundSyncIntervalHours = prefs[Keys.BG_SYNC_INTERVAL_H] ?: 6,
            autoDeletePlayedPolicy = prefs[Keys.AUTO_DELETE_PLAYED] ?: "AFTER_24H",
            maxCacheSizeMb = prefs[Keys.MAX_CACHE_MB] ?: 1024,
            enabledHomeCards = HomeCardType.fromCsv(prefs[Keys.HOME_CARDS_CSV], isPro),
            homeCardOrder = HomeCardType.orderFromCsv(prefs[Keys.HOME_CARD_ORDER_CSV]),
            listeningHistoryRange = ListeningHistoryRange.fromDays(prefs[Keys.LISTENING_HISTORY_DAYS] ?: 14),
            swipeRightAction = EpisodeSwipeAction.fromName(prefs[Keys.SWIPE_RIGHT_ACTION], EpisodeSwipeAction.UP_NEXT_CHOOSE),
            swipeLeftAction = EpisodeSwipeAction.fromName(prefs[Keys.SWIPE_LEFT_ACTION], EpisodeSwipeAction.MARK_PLAYED),
            podcastSortOption = PodcastSortOption.fromName(prefs[Keys.PODCAST_SORT_OPTION]),
            podcastFilterOption = PodcastFilterOption.fromName(prefs[Keys.PODCAST_FILTER_OPTION]),
            toastNotificationsEnabled = prefs[Keys.TOAST_ENABLED] ?: true,
            toastDuration = ToastDurationOption.fromName(prefs[Keys.TOAST_DURATION]),
            toastStyle = ToastVisualStyle.fromName(prefs[Keys.TOAST_STYLE]),
            toastPlacement = ToastPlacement.fromName(prefs[Keys.TOAST_PLACEMENT])
        )
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.loheDataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        context.loheDataStore.edit { it[Keys.REDUCED_MOTION] = enabled }
    }

    suspend fun setUpNextIndicatorEnabled(enabled: Boolean) {
        context.loheDataStore.edit { it[Keys.INDICATOR_ENABLED] = enabled }
    }

    suspend fun setQueueIndicatorStyle(style: QueueIndicatorStyle) {
        context.loheDataStore.edit { it[Keys.INDICATOR_STYLE] = style.name }
    }

    suspend fun setQueueIndicatorSize(size: QueueIndicatorSize) {
        context.loheDataStore.edit { it[Keys.INDICATOR_SIZE] = size.name }
    }

    suspend fun setQueuePresetColor(color: QueuePresetColor) {
        context.loheDataStore.edit {
            it[Keys.PRESET_COLOR] = color.name
            it[Keys.USE_CUSTOM_HEX] = false
        }
    }

    suspend fun setCustomHexColor(hex: String, enabled: Boolean) {
        context.loheDataStore.edit {
            it[Keys.CUSTOM_HEX] = hex
            it[Keys.USE_CUSTOM_HEX] = enabled
        }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.loheDataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 3.5f) }
    }

    suspend fun setSkipDurations(backSeconds: Int, forwardSeconds: Int) {
        context.loheDataStore.edit {
            it[Keys.SKIP_BACK] = backSeconds
            it[Keys.SKIP_FORWARD] = forwardSeconds
        }
    }

    suspend fun setSkipSilence(enabled: Boolean) {
        context.loheDataStore.edit { it[Keys.SKIP_SILENCE] = enabled }
    }

    suspend fun setVolumeBoostDb(db: Int) {
        context.loheDataStore.edit { it[Keys.VOLUME_BOOST_DB] = db }
    }

    suspend fun setContinuousPlayback(enabled: Boolean) {
        context.loheDataStore.edit { it[Keys.CONTINUOUS_PLAYBACK] = enabled }
    }

    suspend fun setPlaybackExtraOptions(
        markPlayedFinished: Boolean,
        resumeHeadset: Boolean,
        autoRewindSec: Int
    ) {
        context.loheDataStore.edit {
            it[Keys.MARK_PLAYED_FINISHED] = markPlayedFinished
            it[Keys.RESUME_HEADSET] = resumeHeadset
            it[Keys.AUTO_REWIND_SEC] = autoRewindSec
        }
    }

    suspend fun setFloatingMiniPlayer(enabled: Boolean) {
        context.loheDataStore.edit { it[Keys.FLOATING_MINI_PLAYER] = enabled }
    }

    suspend fun setFloatingAppearance(sizeDp: Int, transparencyPct: Int) {
        context.loheDataStore.edit {
            it[Keys.FLOATING_SIZE_DP] = sizeDp
            it[Keys.FLOATING_TRANSPARENCY] = transparencyPct
        }
    }

    suspend fun setAutoArchivePolicy(policy: String) {
        context.loheDataStore.edit { it[Keys.AUTO_ARCHIVE_POLICY] = policy }
    }

    suspend fun setKeepStarredExcluded(keep: Boolean) {
        context.loheDataStore.edit { it[Keys.KEEP_STARRED] = keep }
    }

    suspend fun setEpisodeLimitPerShow(limit: Int) {
        context.loheDataStore.edit { it[Keys.EPISODE_LIMIT] = limit }
    }

    suspend fun setAutoDownloadSettings(wifi: Boolean, upNext: Boolean, depth: Int = 5) {
        context.loheDataStore.edit {
            it[Keys.AUTO_DOWNLOAD_WIFI] = wifi
            it[Keys.AUTO_DOWNLOAD_UP_NEXT] = upNext
            it[Keys.AUTO_DOWNLOAD_DEPTH] = depth
        }
    }

    suspend fun setProUnlocked(unlocked: Boolean) {
        context.loheDataStore.edit {
            it[Keys.IS_PRO_UNLOCKED] = unlocked
            if (!unlocked) {
                it[Keys.FLOATING_MINI_PLAYER] = false
                it[Keys.USE_CUSTOM_HEX] = false
                it[Keys.CLOUD_SYNC_AUTO] = false
            }
        }
    }

    suspend fun setCloudSyncStatus(autoBackup: Boolean, summary: String) {
        context.loheDataStore.edit {
            it[Keys.CLOUD_SYNC_AUTO] = autoBackup
            it[Keys.LAST_CLOUD_SYNC] = summary
        }
    }

    suspend fun setCloudSyncProvider(provider: CloudSyncProvider) {
        context.loheDataStore.edit {
            it[Keys.CLOUD_SYNC_PROVIDER] = provider.name
        }
    }

    suspend fun setWebDavConfig(serverUrl: String, username: String, password: String) {
        context.loheDataStore.edit {
            it[Keys.WEBDAV_SERVER_URL] = serverUrl.trim()
            it[Keys.WEBDAV_USERNAME] = username.trim()
            it[Keys.WEBDAV_PASSWORD] = password
        }
    }

    suspend fun setSafSyncFile(uri: String, providerName: String, displayName: String) {
        context.loheDataStore.edit {
            it[Keys.SAF_SYNC_URI] = uri.trim()
            it[Keys.SAF_SYNC_PROVIDER] = providerName
            it[Keys.SAF_SYNC_NAME] = displayName.trim().ifBlank { "lohe_cloud_sync.json" }
        }
    }

    suspend fun clearSafSyncFile() {
        context.loheDataStore.edit {
            it.remove(Keys.SAF_SYNC_URI)
            it.remove(Keys.SAF_SYNC_PROVIDER)
            it.remove(Keys.SAF_SYNC_NAME)
        }
    }

    suspend fun getDriveSyncJournal(): DriveSyncLocalJournal {
        val raw = context.loheDataStore.data.first()[Keys.DRIVE_SYNC_STATE_JSON]
        return DriveSyncLocalJournal.fromJsonString(raw)
    }

    suspend fun setDriveSyncJournal(journal: DriveSyncLocalJournal) {
        context.loheDataStore.edit {
            it[Keys.DRIVE_SYNC_STATE_JSON] = journal.toJsonString()
        }
    }

    suspend fun updateDriveSyncJournal(transform: (DriveSyncLocalJournal) -> DriveSyncLocalJournal) {
        context.loheDataStore.edit { prefs ->
            val current = DriveSyncLocalJournal.fromJsonString(prefs[Keys.DRIVE_SYNC_STATE_JSON])
            prefs[Keys.DRIVE_SYNC_STATE_JSON] = transform(current).toJsonString()
        }
    }

    suspend fun setLastPlayingEpisodeId(episodeId: String) {
        context.loheDataStore.edit { it[Keys.LAST_PLAYING_EPISODE_ID] = episodeId }
    }

    suspend fun setPlaylistsGridView(grid: Boolean) {
        context.loheDataStore.edit { it[Keys.PLAYLISTS_GRID_VIEW] = grid }
    }

    suspend fun recordListeningTick(savedDeltaMs: Long, addMinuteToday: Boolean) {
        context.loheDataStore.edit { prefs ->
            val currentSaved = prefs[Keys.TOTAL_TIME_SAVED_MS] ?: 0L
            if (savedDeltaMs > 0L) {
                prefs[Keys.TOTAL_TIME_SAVED_MS] = currentSaved + savedDeltaMs
            }
            if (addMinuteToday) {
                val legacyFakeSeed = listOf(12, 18, 8, 24, 15, 32, 22, 14, 19, 28, 11, 16, 25, 20)
                val parsed = (prefs[Keys.DAILY_LISTENING_CSV] ?: "")
                    .split(",").mapNotNull { it.trim().toIntOrNull() }
                val baseDays = when {
                    parsed.isEmpty() || parsed == legacyFakeSeed -> List(90) { 0 }
                    parsed.size < 90 -> List(90 - parsed.size) { 0 } + parsed
                    else -> parsed.takeLast(90)
                }
                val todayEpochDay = System.currentTimeMillis() / 86_400_000L
                val lastEpochDay = prefs[Keys.LAST_LISTENING_EPOCH_DAY] ?: todayEpochDay
                val delta = (todayEpochDay - lastEpochDay).toInt().coerceAtLeast(0)
                val days = when {
                    delta == 0 -> baseDays.toMutableList()
                    delta >= 90 -> MutableList(90) { 0 }
                    else -> (baseDays.drop(delta) + List(delta) { 0 }).toMutableList()
                }
                days[days.lastIndex] = days.last() + 1
                prefs[Keys.DAILY_LISTENING_CSV] = days.joinToString(",")
                prefs[Keys.LAST_LISTENING_EPOCH_DAY] = todayEpochDay
            }
        }
    }

    suspend fun setSleepShakeToExtend(enabled: Boolean) {
        context.loheDataStore.edit { it[Keys.SLEEP_SHAKE_EXTEND] = enabled }
    }

    suspend fun setNewEpisodeNotificationsEnabled(enabled: Boolean) {
        context.loheDataStore.edit { it[Keys.NEW_EP_NOTIFICATIONS] = enabled }
    }

    suspend fun setBackgroundSyncPolicy(enabled: Boolean, intervalHours: Int) {
        context.loheDataStore.edit {
            it[Keys.BG_SYNC_ENABLED] = enabled
            it[Keys.BG_SYNC_INTERVAL_H] = intervalHours.coerceIn(6, 24)
        }
    }

    suspend fun setStorageCleanupPolicy(autoDeletePlayedPolicy: String, maxCacheSizeMb: Int) {
        context.loheDataStore.edit {
            it[Keys.AUTO_DELETE_PLAYED] = autoDeletePlayedPolicy
            it[Keys.MAX_CACHE_MB] = maxCacheSizeMb
        }
    }

    suspend fun setHomeCardEnabled(card: HomeCardType, enabled: Boolean, isProUnlocked: Boolean) {
        context.loheDataStore.edit { prefs ->
            val current = HomeCardType.fromCsv(prefs[Keys.HOME_CARDS_CSV], isProUnlocked).toMutableSet()
            if (enabled) current.add(card) else current.remove(card)
            prefs[Keys.HOME_CARDS_CSV] = current.joinToString(",") { it.name }
        }
    }

    suspend fun moveHomeCard(card: HomeCardType, delta: Int) {
        context.loheDataStore.edit { prefs ->
            val order = HomeCardType.orderFromCsv(prefs[Keys.HOME_CARD_ORDER_CSV]).toMutableList()
            val from = order.indexOf(card)
            val to = (from + delta).coerceIn(0, order.lastIndex)
            if (from < 0 || from == to) return@edit
            order.add(to, order.removeAt(from))
            prefs[Keys.HOME_CARD_ORDER_CSV] = HomeCardType.orderToCsv(order)
        }
    }

    suspend fun resetHomeCardsToDefault() {
        context.loheDataStore.edit { prefs ->
            prefs.remove(Keys.HOME_CARDS_CSV)
            prefs.remove(Keys.HOME_CARD_ORDER_CSV)
        }
    }

    suspend fun setListeningHistoryRange(range: ListeningHistoryRange) {
        context.loheDataStore.edit { prefs ->
            prefs[Keys.LISTENING_HISTORY_DAYS] = range.days
        }
    }

    suspend fun setSwipeActions(rightAction: EpisodeSwipeAction, leftAction: EpisodeSwipeAction) {
        context.loheDataStore.edit { prefs ->
            prefs[Keys.SWIPE_RIGHT_ACTION] = rightAction.name
            prefs[Keys.SWIPE_LEFT_ACTION] = leftAction.name
        }
    }

    suspend fun setPodcastSortOption(sort: PodcastSortOption) {
        context.loheDataStore.edit { prefs ->
            prefs[Keys.PODCAST_SORT_OPTION] = sort.name
        }
    }

    suspend fun setPodcastFilterOption(filter: PodcastFilterOption) {
        context.loheDataStore.edit { prefs ->
            prefs[Keys.PODCAST_FILTER_OPTION] = filter.name
        }
    }

    suspend fun setToastSettings(
        enabled: Boolean,
        duration: ToastDurationOption,
        style: ToastVisualStyle,
        placement: ToastPlacement
    ) {
        context.loheDataStore.edit { prefs ->
            prefs[Keys.TOAST_ENABLED] = enabled
            prefs[Keys.TOAST_DURATION] = duration.name
            prefs[Keys.TOAST_STYLE] = style.name
            prefs[Keys.TOAST_PLACEMENT] = placement.name
        }
    }
}
