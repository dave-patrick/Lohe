package com.lohe.app.dev

import com.lohe.app.data.PodcastSort
import com.lohe.app.data.PlaylistSort
import com.lohe.app.ui.theme.Appearance
import com.lohe.app.ui.theme.SettingsStore

/**
 * The settings the dev surface is allowed to change, and how.
 *
 * Shared by the MCP server and the REST surface so that a setting behaves
 * the same whichever door it came through — and so that an unknown key is
 * refused the same way too.
 */
object DevSettingsBridge {

    /** The keys that can be written, with the kind of value each one takes. */
    val WRITABLE = listOf(
        "appearance" to "light or dark",
        "volumeBoostDefault" to "true or false",
        "voiceEnhancementDefault" to "true or false",
        "skipSilenceDefault" to "true or false",
        "smartSpeedDefault" to "true or false",
        "autoTrimDefault" to "true or false",
        "defaultPlaybackSpeed" to "a number between 0.5 and 3.0",
        "skipBackSeconds" to "a number between 5 and 60",
        "skipForwardSeconds" to "a number between 5 and 120",
        "autoArchiveHours" to "-1 (off), 0, 1, 24, or 168",
        "autoDownloadQueue" to "true or false",
        "sleepTimerMinutes" to "a number between 1 and 1440",
        "podcastSort" to PodcastSort.entries.joinToString(", ") { it.name },
        "playlistSort" to PlaylistSort.entries.joinToString(", ") { it.name },
    )

    /**
     * Writes one setting.
     *
     * [value] arrives as text because that is what both doors carry: a JSON
     * number, a JSON boolean and a JSON string all have one honest reading
     * once they are spelled out.
     */
    suspend fun apply(settings: SettingsStore, key: String, value: String) {
        when (key) {
            "appearance" -> settings.setAppearance(
                if (value.equals("light", ignoreCase = true)) Appearance.LIGHT else Appearance.DARK,
            )
            "volumeBoostDefault" -> settings.setVolumeBoostDefault(value.toBooleanStrict())
            "voiceEnhancementDefault" -> settings.setVoiceEnhancementDefault(value.toBooleanStrict())
            "skipSilenceDefault" -> settings.setSkipSilenceDefault(value.toBooleanStrict())
            "smartSpeedDefault" -> settings.setSmartSpeedDefault(value.toBooleanStrict())
            "autoTrimDefault" -> settings.setAutoTrimDefault(value.toBooleanStrict())
            "defaultPlaybackSpeed" -> settings.setDefaultPlaybackSpeed(value.toFloatStrict())
            "skipBackSeconds" -> settings.setSkipBackSeconds(value.toIntStrict())
            "skipForwardSeconds" -> settings.setSkipForwardSeconds(value.toIntStrict())
            "autoArchiveHours" -> settings.setAutoArchiveHours(value.toIntStrict())
            "autoDownloadQueue" -> settings.setAutoDownloadQueue(value.toBooleanStrict())
            "sleepTimerMinutes" -> settings.setSleepTimerMinutes(value.toIntStrict())
            "podcastSort" -> settings.setPodcastSort(
                PodcastSort.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Unknown podcastSort: $value"),
            )
            "playlistSort" -> settings.setPlaylistSort(
                PlaylistSort.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Unknown playlistSort: $value"),
            )
            else -> throw IllegalArgumentException("Unknown setting: $key")
        }
    }

    private fun String.toBooleanStrict(): Boolean =
        this.trim().lowercase().let {
            when (it) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("Expected true or false, got \"$this\"")
            }
        }

    private fun String.toFloatStrict(): Float =
        this.trim().toFloatOrNull() ?: throw IllegalArgumentException("Expected a number, got \"$this\"")

    private fun String.toIntStrict(): Int =
        this.trim().toIntOrNull() ?: throw IllegalArgumentException("Expected a number, got \"$this\"")
}
