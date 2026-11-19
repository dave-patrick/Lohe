package com.lohe.app.data.local

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
import com.lohe.app.ui.theme.QueuePresetColor
import com.lohe.app.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.loheDataStore: DataStore<Preferences> by preferencesDataStore(name = "lohe_settings")

data class LoheUserSettings(
    val themePreference: ThemePreference = ThemePreference.DARK,
    val reducedMotion: Boolean = false,
    val upNextIndicatorEnabled: Boolean = true,
    val queueIndicatorStyle: QueueIndicatorStyle = QueueIndicatorStyle.DOT,
    val queueIndicatorSize: QueueIndicatorSize = QueueIndicatorSize.SMALL,
    val queuePresetColor: QueuePresetColor = QueuePresetColor.BLUE,
    val useCustomHexColor: Boolean = false,
    val customHexColor: String = "#00D2FF",
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
    val episodeLimitPerShow: Int = 25,
    val autoDownloadWifi: Boolean = true,
    val autoDownloadUpNext: Boolean = true,
    val autoDownloadQueueDepth: Int = 5,
    val isProUnlocked: Boolean = false, // Simulated $9.99/year local test upgrade (android.test.purchased)
    val cloudSyncAutoBackup: Boolean = false,
    val lastCloudSyncSummary: String = "Not connected • Local SAF Drive import/export active",
    val lastPlayingEpisodeId: String = "",
    val playlistsGridView: Boolean = true,
    val dailyListeningMinutes: List<Int> = listOf(12, 18, 8, 24, 15, 32, 22, 14, 19, 28, 11, 16, 25, 20),
    val totalTimeSavedMs: Long = 14 * 60_000L,
    val sleepShakeToExtendEnabled: Boolean = true,
    val newEpisodeNotificationsEnabled: Boolean = true,
    val backgroundSyncEnabled: Boolean = true,
    val backgroundSyncIntervalHours: Int = 6,
    val autoDeletePlayedPolicy: String = "AFTER_24H", // NEVER, IMMEDIATELY, AFTER_24H, AFTER_7D
    val maxCacheSizeMb: Int = 1024 // 0 = Unlimited, 500, 1024, 2048
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
        val LAST_CLOUD_SYNC = stringPreferencesKey("last_cloud_sync_summary")
        val LAST_PLAYING_EPISODE_ID = stringPreferencesKey("last_playing_episode_id")
        val PLAYLISTS_GRID_VIEW = booleanPreferencesKey("playlists_grid_view")
        val DAILY_LISTENING_CSV = stringPreferencesKey("daily_listening_csv")
        val TOTAL_TIME_SAVED_MS = longPreferencesKey("total_time_saved_ms")
        val SLEEP_SHAKE_EXTEND = booleanPreferencesKey("sleep_shake_to_extend")
        val NEW_EP_NOTIFICATIONS = booleanPreferencesKey("new_episode_notifications")
        val BG_SYNC_ENABLED = booleanPreferencesKey("background_sync_enabled")
        val BG_SYNC_INTERVAL_H = intPreferencesKey("background_sync_interval_hours")
        val AUTO_DELETE_PLAYED = stringPreferencesKey("auto_delete_played_policy")
        val MAX_CACHE_MB = intPreferencesKey("max_cache_size_mb")
    }

    val settingsFlow: Flow<LoheUserSettings> = context.loheDataStore.data.map { prefs ->
        val isPro = prefs[Keys.IS_PRO_UNLOCKED] ?: false
        val rawStyle = QueueIndicatorStyle.fromName(prefs[Keys.INDICATOR_STYLE] ?: QueueIndicatorStyle.DOT.name)
        val effectiveStyle = if (rawStyle.isPro && !isPro) QueueIndicatorStyle.DOT else rawStyle
        val defaultDays = listOf(12, 18, 8, 24, 15, 32, 22, 14, 19, 28, 11, 16, 25, 20)
        val parsedDays = (prefs[Keys.DAILY_LISTENING_CSV] ?: "")
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .let { if (it.size == 14) it else defaultDays }

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
            customHexColor = prefs[Keys.CUSTOM_HEX] ?: "#00D2FF",
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
            episodeLimitPerShow = prefs[Keys.EPISODE_LIMIT] ?: 25,
            autoDownloadWifi = prefs[Keys.AUTO_DOWNLOAD_WIFI] ?: true,
            autoDownloadUpNext = prefs[Keys.AUTO_DOWNLOAD_UP_NEXT] ?: true,
            autoDownloadQueueDepth = prefs[Keys.AUTO_DOWNLOAD_DEPTH] ?: 5,
            isProUnlocked = isPro,
            cloudSyncAutoBackup = (prefs[Keys.CLOUD_SYNC_AUTO] ?: false) && isPro,
            lastCloudSyncSummary = prefs[Keys.LAST_CLOUD_SYNC]
                ?: "Not connected • Local SAF Drive import/export active",
            lastPlayingEpisodeId = prefs[Keys.LAST_PLAYING_EPISODE_ID] ?: "",
            playlistsGridView = prefs[Keys.PLAYLISTS_GRID_VIEW] ?: true,
            dailyListeningMinutes = parsedDays,
            totalTimeSavedMs = prefs[Keys.TOTAL_TIME_SAVED_MS] ?: (14 * 60_000L),
            sleepShakeToExtendEnabled = prefs[Keys.SLEEP_SHAKE_EXTEND] ?: true,
            newEpisodeNotificationsEnabled = prefs[Keys.NEW_EP_NOTIFICATIONS] ?: true,
            backgroundSyncEnabled = prefs[Keys.BG_SYNC_ENABLED] ?: true,
            backgroundSyncIntervalHours = prefs[Keys.BG_SYNC_INTERVAL_H] ?: 6,
            autoDeletePlayedPolicy = prefs[Keys.AUTO_DELETE_PLAYED] ?: "AFTER_24H",
            maxCacheSizeMb = prefs[Keys.MAX_CACHE_MB] ?: 1024
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

    suspend fun setLastPlayingEpisodeId(episodeId: String) {
        context.loheDataStore.edit { it[Keys.LAST_PLAYING_EPISODE_ID] = episodeId }
    }

    suspend fun setPlaylistsGridView(grid: Boolean) {
        context.loheDataStore.edit { it[Keys.PLAYLISTS_GRID_VIEW] = grid }
    }

    suspend fun recordListeningTick(savedDeltaMs: Long, addMinuteToday: Boolean) {
        context.loheDataStore.edit { prefs ->
            val currentSaved = prefs[Keys.TOTAL_TIME_SAVED_MS] ?: (14 * 60_000L)
            if (savedDeltaMs > 0L) {
                prefs[Keys.TOTAL_TIME_SAVED_MS] = currentSaved + savedDeltaMs
            }
            if (addMinuteToday) {
                val defaultDays = mutableListOf(12, 18, 8, 24, 15, 32, 22, 14, 19, 28, 11, 16, 25, 20)
                val parsed = (prefs[Keys.DAILY_LISTENING_CSV] ?: "")
                    .split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableList()
                val days = if (parsed.size == 14) parsed else defaultDays
                days[days.lastIndex] = days.last() + 1
                prefs[Keys.DAILY_LISTENING_CSV] = days.joinToString(",")
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
}
