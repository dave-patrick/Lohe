package com.lohe.app.data

/**
 * How the library is ordered.
 *
 * A library is a pile, and a pile has more than one sensible order. Which
 * one is right depends on what the listener is doing: looking for a show
 * they half-remember the name of, or looking for the show that has been
 * quiet longest.
 */
enum class PodcastSort(val label: String) {
    /** Alphabetical, by title. */
    TITLE("Title"),

    /** The shows with the most episodes. */
    MOST_EPISODES("Most episodes"),

    /** The shows with the newest episode. */
    RECENT("Recently updated"),
}

/**
 * How the playlists are ordered.
 *
 * [MANUAL] is the default because the playlists can be dragged into an
 * order, and a sort that ignored that would throw the listener's work away
 * every time they opened the screen.
 */
enum class PlaylistSort(val label: String) {
    /** The order the listener arranged them in. */
    MANUAL("Manual"),

    /** Alphabetical, by name. */
    NAME("Name"),

    /** The most recently made first. */
    NEWEST("Newest"),

    /** The ones with the most episodes. */
    MOST_EPISODES("Most episodes"),
}
