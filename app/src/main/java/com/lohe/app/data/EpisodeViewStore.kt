package com.lohe.app.data

import android.content.SharedPreferences

/** How one podcast's episode list is viewed. */
data class EpisodeView(
    val filter: EpisodeFilter.Filter = EpisodeFilter.Filter.ALL,
    val order: EpisodeFilter.Order = EpisodeFilter.Order.NEWEST_FIRST,
    val hidePlayed: Boolean = false,
) {
    /** `FILTER|ORDER|hide`, one short string per podcast. */
    fun encode(): String = "${filter.name}|${order.name}|${if (hidePlayed) 1 else 0}"

    companion object {
        /** Reads [raw], falling back to the defaults for anything missing or unknown. */
        fun decode(raw: String?): EpisodeView {
            val parts = raw?.split('|') ?: return EpisodeView()
            return EpisodeView(
                filter = parts.getOrNull(0)
                    ?.let { name -> EpisodeFilter.Filter.entries.firstOrNull { it.name == name } }
                    ?: EpisodeFilter.Filter.ALL,
                order = parts.getOrNull(1)
                    ?.let { name -> EpisodeFilter.Order.entries.firstOrNull { it.name == name } }
                    ?: EpisodeFilter.Order.NEWEST_FIRST,
                hidePlayed = parts.getOrNull(2) == "1",
            )
        }
    }
}

/** Remembers the view the listener chose in each podcast. */
class EpisodeViewStore(private val prefs: SharedPreferences) {
    fun load(podcastUuid: String): EpisodeView =
        EpisodeView.decode(prefs.getString(podcastUuid, null))

    fun save(podcastUuid: String, view: EpisodeView) {
        prefs.edit().putString(podcastUuid, view.encode()).apply()
    }
}
