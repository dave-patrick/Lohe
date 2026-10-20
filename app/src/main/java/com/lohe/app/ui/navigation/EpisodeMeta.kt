package com.lohe.app.ui.navigation

import com.lohe.app.data.db.entity.EpisodeSummary
import kotlin.math.roundToInt

/**
 * Secondary lines for episode lists.
 *
 * Kept pure and separate from the composables so the wording and the rounding
 * rules can be tested directly — the previous inline version printed the audio
 * MIME type ("MPEG"), which tells a listener nothing.
 */
object EpisodeMeta {

    private const val MINUTE = 60
    private const val HOUR = 3600

    /** Milliseconds in a day. Distinct from [HOUR], which is in seconds. */
    private const val DAY_MS = 86_400_000L

    /** Feeds older than ~year 5138 can't store real millisecond dates, so a
     *  timestamp under 100 billion is treated as Unix seconds and scaled up. */
    private const val SECONDS_SCALE_THRESHOLD = 100_000_000_000L

    /**
     * Normalises a feed epoch that some publishers store as Unix seconds
     * instead of Unix milliseconds.
     */
    private fun toEpochMillis(epochMillis: Long): Long =
        if (epochMillis <= 0L) epochMillis else if (epochMillis < SECONDS_SCALE_THRESHOLD) epochMillis * 1000L else epochMillis

    /**
     * Coarse runtime, e.g. "24 min", "1 hr 5 min".
     *
     * Returns an empty string for an unknown or implausible duration: a list
     * row reads better with a gap than with "0 min" or a fabricated length.
     */
    fun duration(seconds: Int?): String {
        if (seconds == null || seconds <= 0) return ""
        if (seconds < MINUTE) return "${seconds}s"
        val hours = seconds / HOUR
        val minutes = (seconds % HOUR) / MINUTE
        return when {
            hours == 0 -> "${minutes} min"
            minutes == 0 -> "$hours hr"
            else -> "$hours hr $minutes min"
        }
    }

    /**
     * Publication date relative to [now] where that is clearer than a date.
     *
     * Anything under three days reads better as "Today"/"Yesterday"/"4d ago";
     * older than that a listener is looking for the date, not the distance.
     */
    fun published(epochMillis: Long, now: Long): String {
        val ms = toEpochMillis(epochMillis)
        if (ms <= 0L) return ""
        val days = ((now - ms) / DAY_MS).toInt()
        return when {
            days <= 0 -> "Today"
            days == 1 -> "Yesterday"
            days < 7 -> "${days}d ago"
            else -> absoluteDate(ms, now)
        }
    }

    /** "HH:mm" today, "12 Oct" within this year, "12 Oct 2024" for older. */
    fun publishedCompact(epochMillis: Long, now: Long): String {
        val ms = toEpochMillis(epochMillis)
        if (ms <= 0L) return ""
        val days = ((now - ms) / DAY_MS).toInt()
        return when {
            days <= 0 -> timeOfDay(ms)
            days == 1 -> "Yesterday"
            days < 7 -> "${days}d ago"
            else -> absoluteDate(ms, now)
        }
    }

    /** "12 Oct" within this year, "12 Oct 2024" for older. */
    fun absoluteDate(epochMillis: Long, now: Long): String {
        val ms = toEpochMillis(epochMillis)
        if (ms <= 0L) return ""
        val month = MONTHS[monthIndex(ms)]
        val day = dayOfMonth(ms)
        return if (year(ms) == year(now)) "$day $month" else "$day $month ${year(ms)}"
    }

    /** The "09:45" portion of a publication timestamp. */
    fun timeOfDay(epochMillis: Long): String {
        val ms = toEpochMillis(epochMillis)
        val instant = java.time.Instant.ofEpochMilli(ms)
        val local = instant.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        return "%02d:%02d".format(local.hour, local.minute)
    }

    /** "72% played" or "New". */
    fun progress(episode: EpisodeSummary): String {
        val duration = episode.durationSeconds ?: return "New"
        if (duration <= 0) return "New"
        if (episode.isPlayed) return "Played"
        if (episode.playedUpToSeconds <= 0) return "New"
        val pct = (episode.playedUpToSeconds.toFloat() / duration * 100).roundToInt()
        return "$pct% played"
    }

    /**
     * The full metadata line: progress while part-played, otherwise date and
     * runtime.
     */
    fun line(episode: EpisodeSummary, now: Long): String {
        val runtime = duration(episode.durationSeconds)

        // While something is part-played, how much is left matters more than
        // when it was published.
        if (episode.isInProgress && episode.durationSeconds != null) {
            val left = (episode.durationSeconds - episode.playedUpToSeconds.toInt())
                .coerceAtLeast(0)
            val remaining = duration(left)
            if (remaining.isNotEmpty()) {
                return if (runtime.isEmpty()) "$remaining left" else "$remaining left of $runtime"
            }
        }

        val date = published(episode.published, now)
        return when {
            date.isNotEmpty() && runtime.isNotEmpty() -> "$date · $runtime"
            date.isNotEmpty() -> date
            else -> runtime
        }
    }

    // Calendar maths is delegated to java.time, which is available from API 26
    // — this app's minSdk — so no desugaring is required.
    private fun monthIndex(epochMillis: Long): Int =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneOffset.UTC).monthValue - 1

    private fun dayOfMonth(epochMillis: Long): Int =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneOffset.UTC).dayOfMonth

    private fun year(epochMillis: Long): Int =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneOffset.UTC).year

    private fun year(epochMillis: Long, now: Long): Boolean = year(epochMillis) == year(now)

    private val MONTHS = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
}