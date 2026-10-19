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
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "lohe-settings",
)

/** User-tunable playback behaviour, with defaults chosen to need no setup. */
data class PlaybackSettings(
    val appearance: Appearance = Appearance.DARK,
    val volumeBoostByDefault: Boolean = false,
    val skipSilenceByDefault: Boolean = false,
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
        val SPEED_CHOICES = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    }
}

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
        val defaultSpeed = floatPreferencesKey("default_playback_speed")
        val skipBack = intPreferencesKey("skip_back")
        val skipForward = intPreferencesKey("skip_forward")
        val markPlayedAtEnd = booleanPreferencesKey("mark_played_at_end")
        val autoDownloadQueue = booleanPreferencesKey("auto_download_queue")
        val sleepTimerMinutes = intPreferencesKey("sleep_timer_minutes")
        val podcastSort = stringPreferencesKey("podcast_sort")
        val playlistSort = stringPreferencesKey("playlist_sort")
    }

    val settings: Flow<PlaybackSettings> = context.settingsStore.data.map { prefs ->
        PlaybackSettings(
            appearance = when (prefs[Keys.appearance]) {
                Appearance.LIGHT.name -> Appearance.LIGHT
                else -> Appearance.DARK
            },
            volumeBoostByDefault = prefs[Keys.volumeBoost] ?: false,
            skipSilenceByDefault = prefs[Keys.skipSilence] ?: false,
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

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsStore.edit(block)
    }
}
