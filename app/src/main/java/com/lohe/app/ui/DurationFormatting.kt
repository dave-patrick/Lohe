package com.lohe.app.ui

/** Compact human-readable duration such as `4d 3h 42m`. */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0L) return "0m"

    val totalMinutes = (seconds / 60L).coerceAtLeast(1L)
    val days = totalMinutes / (24L * 60L)
    val hours = (totalMinutes % (24L * 60L)) / 60L
    val minutes = totalMinutes % 60L

    return buildList {
        if (days > 0L) add("${days}d")
        if (hours > 0L) add("${hours}h")
        if (minutes > 0L) add("${minutes}m")
    }.joinToString(" ").ifEmpty { "0m" }
}
