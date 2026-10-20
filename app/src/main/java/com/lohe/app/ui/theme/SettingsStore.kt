package com.lohe.app.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "lohe-settings",
)

/** User-tunable playback behaviour, with defaults chosen to need no setup. */
data class PlaybackSettings(
    val appearance: Appearance = Appearance.DARK,
    val volumeBoostByDefault: Boolean = false,
    val skipSilenceByDefault: Boolean = false,
    /**
     * Whether smart speed is on by default.
     *
     * Smart speed skips silence-like passages on its own, rather than only
     * where the player is told to.
     */
    val smartSpeedByDefault: Boolean = false,
    /**
     * Whether trimming is on by default.
     *
     * Trimming cuts the dead air and the adverts off the ends.
     */
    val autoTrimByDefault: Boolean = false,
    val defaultPlaybackSpeed: Float = 1.0f,
    val skipBackSeconds: Int = DEFAULT_SKIP_BACK,
    val skipForwardSeconds: Int = DEFAULT_SKIP_FORWARD,
    val markPlayedAtEnd: Boolean = true,
    /**
     * Whether episodes put in Up Next are downloaded as they are added.
     *
     * A choice about data, not about the queue, so it lives here rather than
     * as a chip on the queue's own header.
     */
    val autoDownloadQueue: Boolean = true,
    /**
     * The sleep-timer length the listener last chose, in minutes.
     *
     * Remembered so the sheet opens on the choice they keep making instead of
     * making them find it again every time.
     */
    val sleepTimerMinutes: Int = 30,
    /** How the library is ordered. */
    val podcastSort: com.lohe.app.data.PodcastSort = com.lohe.app.data.PodcastSort.TITLE,
    /** How the playlists are ordered. */
    val playlistSort: com.lohe.app.data.PlaylistSort = com.lohe.app.data.PlaylistSort.MANUAL,
) {
    companion object {
        const val DEFAULT_SKIP_BACK = 10
        const val DEFAULT_SKIP_FORWARD = 30

        /** Offered in the UI; the choice is constrained to this list. */
        val SKIP_CHOICES = listOf(5, 10, 15, 30, 45, 60)
        val SPEED_CHOICES = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
    }
}

/** Per-show playback preferences. Defaults fall back to the global settings. */
data class ShowPlaybackSettings(
    /** Whether loudness boost above 0 dB is enabled for this show. */
    val volumeBoost: Boolean = false,
    /** Whether ExoPlayer silence trimming is enabled for this show. */
    val skipSilence: Boolean = false,
    /** Playback speed multiplier (0.5x .. 3.0x) for this show. */
    val playbackSpeed: Float = 1.0f,
    /** Smart speed: automatically skip detected silence/silence-like sections. */
    val smartSpeed: Boolean = false,
    /** Crossfade with the next episode when this one ends (ms). 0 = off. */
    val crossfadeMs: Int = 0,
    /** Auto-trim leading/trailing silence and detected ads. */
    val autoTrim: Boolean = false,
) {
    companion object {
        val EMPTY = ShowPlaybackSettings()
    }
}

/**
 * The settings a show has been given for itself.
 *
 * A null means the show follows the global setting, which is what lets the
 * settings screen say "use the global setting" rather than showing a value
 * that was never chosen here.
 */
data class ShowPlaybackOverrides(
    val volumeBoost: Boolean? = null,
    val skipSilence: Boolean? = null,
    val playbackSpeed: Float? = null,
    val smartSpeed: Boolean? = null,
    val crossfadeMs: Int? = null,
    val autoTrim: Boolean? = null,
)

/**
 * Durable user settings.
 *
 * Reads coerce to the legal range rather than trusting whatever is on disk: a
 * value written by an older build (or hand-edited) must not be able to put the
 * skip buttons into a nonsense state.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val appearance = stringPreferencesKey("appearance")
        val volumeBoost = booleanPreferencesKey("volume_boost")
        val skipSilence = booleanPreferencesKey("skip_silence")
        val smartSpeed = booleanPreferencesKey("smart_speed")
        val autoTrim = booleanPreferencesKey("auto_trim")
        val defaultSpeed = floatPreferencesKey("default_playback_speed")
        val skipBack = intPreferencesKey("skip_back")
        val skipForward = intPreferencesKey("skip_forward")
        val markPlayedAtEnd = booleanPreferencesKey("mark_played_at_end")
        val autoDownloadQueue = booleanPreferencesKey("auto_download_queue")
        val sleepTimerMinutes = intPreferencesKey("sleep_timer_minutes")
        val podcastSort = stringPreferencesKey("podcast_sort")
        val playlistSort = stringPreferencesKey("playlist_sort")
    }

    private fun showBoolKey(podcastUuid: String, suffix: String) =
        booleanPreferencesKey("show_playback_${suffix}_$podcastUuid")

    private fun showSpeedKey(podcastUuid: String) =
        floatPreferencesKey("show_playback_playback_speed_$podcastUuid")

    private fun showCrossfadeKey(podcastUuid: String) =
        intPreferencesKey("show_playback_crossfade_ms_$podcastUuid")

    val settings: Flow<PlaybackSettings> = context.settingsStore.data.map { prefs ->
        PlaybackSettings(
            appearance = when (prefs[Keys.appearance]) {
                Appearance.LIGHT.name -> Appearance.LIGHT
                else -> Appearance.DARK
            },
            volumeBoostByDefault = prefs[Keys.volumeBoost] ?: false,
            skipSilenceByDefault = prefs[Keys.skipSilence] ?: false,
            smartSpeedByDefault = prefs[Keys.smartSpeed] ?: false,
            autoTrimByDefault = prefs[Keys.autoTrim] ?: false,
            defaultPlaybackSpeed = (prefs[Keys.defaultSpeed] ?: 1.0f).coerceIn(0.5f, 3.0f),
            // A value written by an older build, or hand-edited into the file,
            // must not be able to put the skip buttons into a nonsense state.
            skipBackSeconds = (prefs[Keys.skipBack] ?: PlaybackSettings.DEFAULT_SKIP_BACK)
                .coerceIn(5, 60),
            skipForwardSeconds = (prefs[Keys.skipForward] ?: PlaybackSettings.DEFAULT_SKIP_FORWARD)
                .coerceIn(5, 120),
            markPlayedAtEnd = prefs[Keys.markPlayedAtEnd] ?: true,
            autoDownloadQueue = prefs[Keys.autoDownloadQueue] ?: true,
            sleepTimerMinutes = (prefs[Keys.sleepTimerMinutes] ?: 30)
                .coerceIn(1, 24 * 60),
            podcastSort = prefs[Keys.podcastSort]
                ?.let { name ->
                    com.lohe.app.data.PodcastSort.entries.firstOrNull { it.name == name }
                }
                ?: com.lohe.app.data.PodcastSort.TITLE,
            playlistSort = prefs[Keys.playlistSort]
                ?.let { name ->
                    com.lohe.app.data.PlaylistSort.entries.firstOrNull { it.name == name }
                }
                ?: com.lohe.app.data.PlaylistSort.MANUAL,
        )
    }

    val appearance: Flow<Appearance> = context.settingsStore.data.map { prefs ->
        when (prefs[Keys.appearance]) {
            Appearance.LIGHT.name -> Appearance.LIGHT
            else -> Appearance.DARK
        }
    }

    suspend fun setAppearance(value: Appearance) = edit { it[Keys.appearance] = value.name }

    suspend fun setVolumeBoostDefault(value: Boolean) =
        edit { it[Keys.volumeBoost] = value }

    suspend fun setSkipSilenceDefault(value: Boolean) =
        edit { it[Keys.skipSilence] = value }

    suspend fun setSmartSpeedDefault(value: Boolean) =
        edit { it[Keys.smartSpeed] = value }

    suspend fun setAutoTrimDefault(value: Boolean) =
        edit { it[Keys.autoTrim] = value }

    suspend fun setDefaultPlaybackSpeed(value: Float) =
        edit { it[Keys.defaultSpeed] = value.coerceIn(0.5f, 3.0f) }

    suspend fun setSkipBackSeconds(value: Int) =
        edit { it[Keys.skipBack] = value.coerceIn(5, 60) }

    suspend fun setSkipForwardSeconds(value: Int) =
        edit { it[Keys.skipForward] = value.coerceIn(5, 120) }

    suspend fun setMarkPlayedAtEnd(value: Boolean) =
        edit { it[Keys.markPlayedAtEnd] = value }

    suspend fun setAutoDownloadQueue(value: Boolean) =
        edit { it[Keys.autoDownloadQueue] = value }

    suspend fun setSleepTimerMinutes(value: Int) =
        edit { it[Keys.sleepTimerMinutes] = value.coerceIn(1, 24 * 60) }

    suspend fun setPodcastSort(value: com.lohe.app.data.PodcastSort) =
        edit { it[Keys.podcastSort] = value.name }

    suspend fun setPlaylistSort(value: com.lohe.app.data.PlaylistSort) =
        edit { it[Keys.playlistSort] = value.name }

    /**
     * The playback settings for one show.
     *
     * A show that has never been given a setting of its own follows the
     * global one, so changing the speed globally changes every show that has
     * not been given a speed of its own. Setting one here overrides it for
     * that show only, and clearing it puts the show back on the global value.
     */
    fun showPlaybackSettings(podcastUuid: String): Flow<ShowPlaybackSettings> =
        combine(context.settingsStore.data, settings) { prefs, global ->
            ShowPlaybackSettings(
                volumeBoost = prefs[showBoolKey(podcastUuid, "volume_boost")]
                    ?: global.volumeBoostByDefault,
                skipSilence = prefs[showBoolKey(podcastUuid, "skip_silence")]
                    ?: global.skipSilenceByDefault,
                playbackSpeed = (
                    prefs[showSpeedKey(podcastUuid)]
                        ?: global.defaultPlaybackSpeed
                    ).coerceIn(0.5f, 3.0f),
                smartSpeed = prefs[showBoolKey(podcastUuid, "smart_speed")]
                    ?: global.smartSpeedByDefault,
                crossfadeMs = (
                    prefs[showCrossfadeKey(podcastUuid)] ?: 0
                    ).coerceIn(0, 30_000),
                autoTrim = prefs[showBoolKey(podcastUuid, "auto_trim")]
                    ?: global.autoTrimByDefault,
            )
        }

    /**
     * The settings a show has been given for itself.
     *
     * Unlike [showPlaybackSettings] this does not fall back to the global
     * value: a null here means the show has not been given one, and so is
     * following the global setting.
     */
    fun showPlaybackOverrides(podcastUuid: String): Flow<ShowPlaybackOverrides> =
        context.settingsStore.data.map { prefs ->
            ShowPlaybackOverrides(
                volumeBoost = prefs[showBoolKey(podcastUuid, "volume_boost")],
                skipSilence = prefs[showBoolKey(podcastUuid, "skip_silence")],
                playbackSpeed = prefs[showSpeedKey(podcastUuid)],
                smartSpeed = prefs[showBoolKey(podcastUuid, "smart_speed")],
                crossfadeMs = prefs[showCrossfadeKey(podcastUuid)],
                autoTrim = prefs[showBoolKey(podcastUuid, "auto_trim")],
            )
        }

    suspend fun setShowPlaybackSettings(podcastUuid: String, settings: ShowPlaybackSettings) =
        edit {
            it[showBoolKey(podcastUuid, "volume_boost")] = settings.volumeBoost
            it[showBoolKey(podcastUuid, "skip_silence")] = settings.skipSilence
            it[showSpeedKey(podcastUuid)] = settings.playbackSpeed
            it[showBoolKey(podcastUuid, "smart_speed")] = settings.smartSpeed
            it[showCrossfadeKey(podcastUuid)] = settings.crossfadeMs
            it[showBoolKey(podcastUuid, "auto_trim")] = settings.autoTrim
        }

    /**
     * Sets one setting for a show, or clears it so it follows the global one.
     *
     * A null value removes the override, which is what "use the global
     * setting" means.
     */
    suspend fun setShowVolumeBoost(podcastUuid: String, value: Boolean?) = edit {
        val key = showBoolKey(podcastUuid, "volume_boost")
        if (value == null) it.remove(key) else it[key] = value
    }

    suspend fun setShowSkipSilence(podcastUuid: String, value: Boolean?) = edit {
        val key = showBoolKey(podcastUuid, "skip_silence")
        if (value == null) it.remove(key) else it[key] = value
    }

    suspend fun setShowSmartSpeed(podcastUuid: String, value: Boolean?) = edit {
        val key = showBoolKey(podcastUuid, "smart_speed")
        if (value == null) it.remove(key) else it[key] = value
    }

    suspend fun setShowAutoTrim(podcastUuid: String, value: Boolean?) = edit {
        val key = showBoolKey(podcastUuid, "auto_trim")
        if (value == null) it.remove(key) else it[key] = value
    }

    suspend fun setShowPlaybackSpeed(podcastUuid: String, value: Float?) = edit {
        val key = showSpeedKey(podcastUuid)
        if (value == null) it.remove(key) else it[key] = value.coerceIn(0.5f, 3.0f)
    }

    /** Puts every setting for a show back on the global value. */
    suspend fun clearShowPlaybackSettings(podcastUuid: String) = edit {
        it.remove(showBoolKey(podcastUuid, "volume_boost"))
        it.remove(showBoolKey(podcastUuid, "skip_silence"))
        it.remove(showBoolKey(podcastUuid, "smart_speed"))
        it.remove(showBoolKey(podcastUuid, "auto_trim"))
        it.remove(showSpeedKey(podcastUuid))
        it.remove(showCrossfadeKey(podcastUuid))
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsStore.edit(block)
    }
}
