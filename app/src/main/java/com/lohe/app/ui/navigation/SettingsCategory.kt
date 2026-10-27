package com.lohe.app.ui.navigation

enum class SettingsCategory(val route: String, val title: String, val summary: String) {
    APPEARANCE("appearance", "Appearance", "Dark or light theme"),
    PLAYBACK("playback", "Playback", "Speed, skip buttons, silence, and finishing episodes"),
    FLOATING_PLAYER("floating-player", "Floating player", "Cross-app controls, size, transparency, and battery setup"),
    ARCHIVING("archiving", "Archiving", "Archive delay and downloaded-copy cleanup"),
    DOWNLOADS("downloads", "Downloads", "Automatic downloads for Up Next"),
    PODCAST_UPDATES("podcast-updates", "Podcast updates", "Choose how often Lohe checks for new episodes"),
    DEBUG("debug", "Diagnostics", "Optional local bug reports");

    companion object {
        fun fromRoute(route: String?): SettingsCategory? = entries.firstOrNull { it.route == route }
    }
}
