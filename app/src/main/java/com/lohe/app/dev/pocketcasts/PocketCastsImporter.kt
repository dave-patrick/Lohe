package com.lohe.app.dev.pocketcasts

import android.util.Log
import com.lohe.app.data.PlaylistRepository
import com.lohe.app.data.SubscriptionRepository
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.EpisodeSummary
import com.lohe.app.data.db.entity.PodcastEntity
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/**
 * Brings Lohe's library into line with Pocket Casts'.
 *
 * Pocket Casts is the source of truth: the same episodes, in the same state,
 * in the same playlists. This walks that state into Lohe — subscriptions,
 * per-episode listening state, stars, deletions, folders, playlists and the
 * up-next queue — and reports what it did and what it could not do.
 *
 * The matching is by identity, not by name alone:
 *
 * - a show is the same show when their titles agree, and by feed URL when
 *   both have one;
 * - an episode is the same episode when its media URL agrees, or failing that
 *   when its title agrees within the same show.
 *
 * Nothing here is destructive by default. A show Pocket Casts has and Lohe
 * does not is only fetched when [subscribeMissingShows] is set, and a folder
 * assignment is only cleared when [replaceFolders] is set. The report says
 * what was left alone.
 *
 * The work comes in three phases — the shows, the episodes, then the
 * playlists — because a library of tens of thousands of episodes is too big
 * to arrive in one piece, and because the playlists can only be written once
 * the episodes they name have been matched.
 */
class PocketCastsImporter(
    private val repository: SubscriptionRepository,
    private val playlists: PlaylistRepository,
    /** Fetch the feeds of shows Pocket Casts has and Lohe does not. */
    private val subscribeMissingShows: Boolean = false,
    /**
     * Clear Lohe's folder assignments for shows Pocket Casts files elsewhere.
     *
     * Off by default: a folder assignment is the user's own arrangement, and
     * dropping it because the other app has no folder for the show would lose
     * work the import was never asked to undo.
     */
    private val replaceFolders: Boolean = false,
) {

    /**
     * Matches the shows and, when asked, fetches the ones Lohe does not have.
     */
    suspend fun applyShows(library: PocketCastsLibrary): ShowReport {
        val loheShows = repository.observePodcasts().first()
        val matching = matchShows(library, loheShows)
        PocketCastsImportSession.showMatches = matching.matches
        return ShowReport(
            matched = matching.matches.size,
            subscribed = matching.subscribed,
            unmatched = matching.unmatched,
        )
    }

    /**
     * Applies the state of every episode Pocket Casts knows about.
     */
    suspend fun applyEpisodes(library: PocketCastsLibrary): EpisodeReport {
        val showMatches = PocketCastsImportSession.showMatches
        // The episodes of each show are read once. A show with hundreds of
        // episodes would otherwise be read once per episode.
        val rowsByShow = mutableMapOf<String, List<EpisodeEntity>>()
        var matched = 0
        var unmatched = 0
        var played = 0
        var inProgress = 0
        var unplayed = 0
        var starred = 0
        var archived = 0

        library.episodes.forEach { episode ->
            val loheShowUuid = showMatches[episode.podcastUuid]
            if (loheShowUuid == null) {
                unmatched++
                return@forEach
            }
            val rows = rowsByShow.getOrPut(loheShowUuid) {
                repository.allEpisodesOnce(loheShowUuid)
            }
            val row = matchEpisode(episode, rows)
            if (row == null) {
                unmatched++
                return@forEach
            }
            matched++
            PocketCastsImportSession.episodeMatches[episode.uuid] = row.uuid

            when (episode.status) {
                "played" -> {
                    PocketCastsImportSession.played += row.uuid
                    if (!row.isPlayed) {
                        repository.markPlayed(row.uuid)
                        played++
                    }
                }
                "in_progress" -> {
                    PocketCastsImportSession.inProgress += row.uuid
                    val position = episode.playedUpToSeconds
                    if (row.playingStatus != EpisodeEntity.IN_PROGRESS || row.playedUpToSeconds != position) {
                        repository.applyProgress(
                            row.uuid,
                            position,
                            episode.durationSeconds?.toLong() ?: row.durationSeconds?.toLong() ?: 0L,
                        )
                        inProgress++
                    }
                }
                else -> {
                    if (row.playingStatus != EpisodeEntity.UNPLAYED || row.playedUpToSeconds != 0L) {
                        repository.applyUnplayed(row.uuid)
                        unplayed++
                    }
                }
            }

            if (episode.starred != row.starred) {
                repository.setStarred(row.uuid, episode.starred)
                if (episode.starred) starred++
            }
            if (episode.starred) {
                if (row.uuid !in PocketCastsImportSession.starred) {
                    PocketCastsImportSession.starred += row.uuid
                }
            }

            // Pocket Casts' "deleted" flag means *archived*: the account has
            // auto-archive on, so nearly every episode the listener has
            // finished with is filed away there. Lohe's own "deleted" means
            // something else — the listener removed it — and there is no
            // Pocket Casts counterpart for that, so it is left alone.
            if (episode.archived != row.isArchived) {
                repository.setArchived(row.uuid, episode.archived)
                if (episode.archived) archived++
            }
        }

        return EpisodeReport(
            matched = matched,
            unmatched = unmatched,
            played = played,
            inProgress = inProgress,
            unplayed = unplayed,
            starred = starred,
            archived = archived,
        )
    }

    /**
     * Applies the folders, the playlists and the up-next queue.
     *
     * The queue is written after the episodes: an episode marked played above
     * is taken out of the queue, and a queue written before that would lose it.
     */
    suspend fun applyPlaylists(library: PocketCastsLibrary): PlaylistReport {
        val episodeMatches = PocketCastsImportSession.episodeMatches

        repository.clearAllQueue()
        var queued = 0
        library.upNext.forEach { uuid ->
            val loheUuid = episodeMatches[uuid] ?: return@forEach
            repository.enqueue(loheUuid)
            queued++
        }

        val folders = applyFolders(library, PocketCastsImportSession.showMatches)
        val written = applyPlaylistRules(library, episodeMatches)

        return PlaylistReport(
            foldersApplied = folders.applied,
            foldersCleared = folders.cleared,
            playlistsApplied = written.applied,
            playlistEpisodesApplied = written.episodes,
            playlistEpisodesUnmatched = written.unmatched,
            playlistsSkipped = written.skipped,
            queued = queued,
        )
    }

    /**
     * Syncs played/unplayed state for episodes that exist in both apps.
     *
     * Matches episodes by title within the same show, and updates Lohe's
     * playing status to match Pocket Casts'. This repairs mismatches left
     * behind when the initial import matched an episode by title to the
     * wrong row, or when playback state changed after the import.
     *
     * Returns how many episodes were changed in each direction.
     */
    suspend fun syncPlayedState(library: PocketCastsLibrary): PlayedSyncReport {
        val loheShows = repository.observePodcasts().first()
        val loheShowMap = loheShows.associateBy { it.title.trim().lowercase() }

        // Build PC show UUID -> Lohe show UUID mapping by title.
        val pcShowToLoheShow = mutableMapOf<String, String>()
        library.shows.forEach { show ->
            loheShowMap[show.title.trim().lowercase()]?.let { pcShowToLoheShow[show.uuid] = it.uuid }
        }

        // Load all Lohe episodes once, keyed by (Lohe show UUID, media URL filename) and (Lohe show UUID, episode title).
        val loheEpisodes = repository.observeAllEpisodes().first()
        val loheByShowAndUrl = mutableMapOf<String, MutableMap<String, EpisodeSummary>>()
        val loheByShowAndTitle = mutableMapOf<String, MutableMap<String, EpisodeSummary>>()
        loheEpisodes.forEach { ep ->
            val urlKey = ep.audioUrl.substringAfterLast('/').trim().lowercase()
            loheByShowAndUrl[ep.podcastUuid]?.put(urlKey, ep)
                ?: run { loheByShowAndUrl[ep.podcastUuid] = mutableMapOf(urlKey to ep) }
            val titleKey = ep.title.trim().lowercase()
            loheByShowAndTitle[ep.podcastUuid]?.put(titleKey, ep)
                ?: run { loheByShowAndTitle[ep.podcastUuid] = mutableMapOf(titleKey to ep) }
        }

        var playedToUnplayed = 0
        var unplayedToPlayed = 0
        var matched = 0

        library.episodes.forEach { episode ->
            val title = episode.title?.trim() ?: return@forEach
            if (title.isEmpty()) return@forEach

            val loheShowUuid = pcShowToLoheShow[episode.podcastUuid] ?: return@forEach

            // Try matching by media URL filename first (more reliable), then by title as fallback.
            val mediaFilename = episode.mediaUrl?.substringAfterLast('/')?.trim()?.lowercase()
            val loheMatch = if (mediaFilename?.isNotEmpty() == true) {
                loheByShowAndUrl[loheShowUuid]?.get(mediaFilename)
            } else {
                null
            } ?: loheByShowAndTitle[loheShowUuid]?.get(title.lowercase())

            if (loheMatch == null) return@forEach

            matched++

            val pcPlayed = episode.status == "played"
            val lohePlayed = loheMatch.playingStatus == EpisodeEntity.PLAYED

            when {
                pcPlayed && !lohePlayed -> {
                    repository.markPlayed(loheMatch.uuid)
                    playedToUnplayed++
                }
                !pcPlayed && lohePlayed -> {
                    repository.applyUnplayed(loheMatch.uuid)
                    unplayedToPlayed++
                }
            }
        }

        android.util.Log.e("PocketCastsImporter", "syncPlayedState: library shows=${library.shows.size}, episodes=${library.episodes.size}, showMatches=${pcShowToLoheShow.size}, episodeMatches=$matched, markedPlayed=$playedToUnplayed, markedUnplayed=$unplayedToPlayed")

        return PlayedSyncReport(matched, playedToUnplayed, unplayedToPlayed)
    }

    /**
     * Applies the whole library in one go, for a library small enough to
     * arrive in a single request.
     */
    suspend fun apply(library: PocketCastsLibrary): ImportReport {
        val shows = applyShows(library)
        val episodes = applyEpisodes(library)
        val playlistsReport = applyPlaylists(library)
        return ImportReport(
            showsMatched = shows.matched,
            showsSubscribed = shows.subscribed,
            showsUnmatched = shows.unmatched,
            episodesMatched = episodes.matched,
            episodesUnmatched = episodes.unmatched,
            markedPlayed = episodes.played,
            markedInProgress = episodes.inProgress,
            markedUnplayed = episodes.unplayed,
            starred = episodes.starred,
            archived = episodes.archived,
            queued = playlistsReport.queued,
            foldersApplied = playlistsReport.foldersApplied,
            foldersCleared = playlistsReport.foldersCleared,
            playlistsApplied = playlistsReport.playlistsApplied,
            playlistEpisodesApplied = playlistsReport.playlistEpisodesApplied,
            playlistEpisodesUnmatched = playlistsReport.playlistEpisodesUnmatched,
            playlistsSkipped = playlistsReport.playlistsSkipped,
        )
    }

    /**
     * Works out which Pocket Casts show is which Lohe show.
     *
     * The title is the signal that works across both apps: Pocket Casts
     * knows a podcast by its name, Lohe by its feed. The feed is used when
     * both have one, as a second opinion.
     */
    private suspend fun matchShows(
        library: PocketCastsLibrary,
        loheShows: List<PodcastEntity>,
    ): ShowMatching {
        val byTitle = loheShows.groupBy { it.title.trim().lowercase() }
        val byFeed = loheShows
            .filter { it.feedUrl.isNotBlank() }
            .associateBy { normaliseFeed(it.feedUrl) }

        val matches = mutableMapOf<String, String>()
        val unmatched = mutableListOf<String>()
        var fetched = 0

        library.shows.forEach { show ->
            val byTitleMatch = byTitle[show.title.trim().lowercase()]?.firstOrNull()
            val byFeedMatch = show.feedUrl?.let { byFeed[normaliseFeed(it)] }
            val match = byFeedMatch ?: byTitleMatch
            if (match != null) {
                matches[show.uuid] = match.uuid
                return@forEach
            }
            if (subscribeMissingShows && !show.feedUrl.isNullOrBlank()) {
                val imported = repository.importFeed(show.feedUrl).getOrNull()
                if (imported != null) {
                    matches[show.uuid] = imported.uuid
                    fetched++
                    return@forEach
                }
            }
            unmatched += show.title
        }

        return ShowMatching(matches, fetched, unmatched)
    }

    /**
     * The Lohe episode that is the same episode as [episode].
     *
     * The media URL is what identifies an episode across two apps; the title
     * is the fallback for the feeds that serve the same file under a
     * different name.
     */
    private fun matchEpisode(episode: PcEpisode, rows: List<EpisodeEntity>): EpisodeEntity? {
        episode.mediaUrl?.let { media ->
            if (media.isNotBlank()) {
                rows.firstOrNull { it.audioUrl == media }?.let { return it }
                val normalised = normaliseMedia(media)
                rows.firstOrNull { normaliseMedia(it.audioUrl) == normalised }?.let { return it }
            }
        }
        val title = episode.title?.trim()?.lowercase() ?: return null
        if (title.isEmpty()) return null
        val candidates = rows.filter { it.title.trim().lowercase() == title }
        if (candidates.size == 1) return candidates.first()
        if (candidates.isEmpty()) return null
        // Several episodes share a title; the publication date breaks the tie.
        val published = episode.publishedMs ?: return candidates.first()
        return candidates.minByOrNull { abs(normalisePublished(it.published) - published) }
    }

    /**
     * Applies the folders.
     *
     * A show filed in a Pocket Casts folder is filed in the same folder here;
     * with [replaceFolders] on, a show Pocket Casts files nowhere loses its
     * folder here too.
     */
    private fun applyFolders(
        library: PocketCastsLibrary,
        showMatches: Map<String, String>,
    ): FolderResult {
        var applied = 0
        var cleared = 0
        val wanted = mutableMapOf<String, String>()

        library.folders.forEach { folder ->
            folder.podcastUuids.forEach { podcastUuid ->
                val loheUuid = showMatches[podcastUuid] ?: return@forEach
                wanted[loheUuid] = folder.name
            }
        }

        playlists.podcastFolders.value.forEach { (loheUuid, folderName) ->
            val wantedFolder = wanted[loheUuid]
            if (wantedFolder != null) {
                if (wantedFolder != folderName) {
                    playlists.setPodcastFolder(loheUuid, wantedFolder)
                    applied++
                }
            } else if (replaceFolders) {
                playlists.setPodcastFolder(loheUuid, "")
                cleared++
            }
        }

        wanted.forEach { (loheUuid, folderName) ->
            if (loheUuid !in playlists.podcastFolders.value) {
                playlists.setPodcastFolder(loheUuid, folderName)
                applied++
            }
        }

        return FolderResult(applied, cleared)
    }

    /**
     * Applies the playlists.
     *
     * A Pocket Casts playlist that follows a set of shows becomes a Lohe
     * playlist that follows the same shows, and one bounded by a length
     * becomes a Lohe playlist bounded by the same length. The two apps then
     * collect the same episodes on their own.
     *
     * A playlist that filters by the *state* of the library — the played
     * ones, the starred ones — has no rule to copy, so it is written as the
     * list of episodes it would have collected. The downloaded ones cannot be
     * copied at all: a download is a file on one device, not a fact about
     * an episode, so those are reported and left alone.
     */
    private fun applyPlaylistRules(
        library: PocketCastsLibrary,
        episodeMatches: Map<String, String>,
    ): PlaylistResult {
        var applied = 0
        var episodeCount = 0
        val unmatched = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        library.playlists.forEach { playlist ->
            val uuids = playlist.episodeUuids.mapNotNull { uuid ->
                episodeMatches[uuid] ?: run {
                    unmatched += uuid
                    null
                }
            }
            val followed = playlist.podcastUuids.mapNotNull { PocketCastsImportSession.showMatches[it] }
            val explicit = when (playlist.kind) {
                "state" -> when {
                    playlist.finished -> PocketCastsImportSession.played.toList()
                    playlist.starred -> PocketCastsImportSession.starred.toList()
                    else -> {
                        skipped += playlist.name
                        return@forEach
                    }
                }
                "progress" -> PocketCastsImportSession.inProgress.toList()
                "skip" -> {
                    // Nothing to copy: the set is a fact about this device's
                    // files, or about a list Lohe keeps already.
                    skipped += playlist.name
                    return@forEach
                }
                else -> uuids
            }

            val existing = playlists.playlists.value.firstOrNull {
                it.name.trim().equals(playlist.name.trim(), ignoreCase = true)
            }
            if (existing == null) {
                playlists.createPlaylist(
                    name = playlist.name,
                    initialEpisodes = explicit,
                    shows = if (playlist.isRule) followed else emptyList(),
                    minDurationMinutes = if (playlist.isRule) playlist.minDurationMinutes else 0,
                    maxDurationMinutes = if (playlist.isRule) playlist.maxDurationMinutes else 0,
                    finished = playlist.finished,
                )
            } else {
                playlists.reorderEpisodes(existing.id, explicit)
                if (playlist.isRule) {
                    playlists.setPlaylistShows(existing.id, followed)
                    playlists.setPlaylistDuration(
                        existing.id,
                        playlist.minDurationMinutes,
                        playlist.maxDurationMinutes,
                    )
                    playlists.setPlaylistFinished(existing.id, playlist.finished)
                } else {
                    // A fixed list of episodes. The rules that would show
                    // something else entirely are cleared, so the list is
                    // what the playlist holds.
                    playlists.setPlaylistShows(existing.id, emptyList())
                    playlists.setPlaylistDuration(existing.id, 0, 0)
                    playlists.setPlaylistFolder(existing.id, "")
                    playlists.setPlaylistFinished(existing.id, false)
                }
            }
            applied++
            episodeCount += explicit.size
        }

        return PlaylistResult(applied, episodeCount, unmatched, skipped)
    }
    private data class ShowMatching(
        val matches: Map<String, String>,
        val subscribed: Int,
        val unmatched: List<String>,
    )

    private data class FolderResult(val applied: Int, val cleared: Int)

    private data class PlaylistResult(
        val applied: Int,
        val episodes: Int,
        val unmatched: List<String>,
        val skipped: List<String> = emptyList(),
    )

    companion object {

        /**
         * Feed URLs are compared without their trailing slash and query, so
         * that the same feed written two ways is still the same feed.
         */
        fun normaliseFeed(url: String): String =
            url.trim()
                .substringBefore('?')
                .substringBefore('#')
                .trimEnd('/')
                .lowercase()

        /**
         * Media URLs are compared the same way, but keep their path's case:
         * a file name can differ by case and still be a different file.
         *
         * Tracking redirects are stripped: Pocket Casts (and some feeds) wrap
         * the real media URL in a redirector such as
         * `tracking.swap.fm/track/<id>/<actual-url>`. Both apps should end up
         * at the same `<actual-url>`, so the wrapper is removed before
         * comparison.
         */
        fun normaliseMedia(url: String): String {
            var result = url.trim()
                .substringBefore('?')
                .substringBefore('#')
                .trimEnd('/')
            // Strip a tracking redirect wrapper: <domain>/track/<id>/<actual-url>
            val trackIdx = result.indexOf("/track/")
            if (trackIdx >= 0) {
                val afterTrack = result.substring(trackIdx + "/track/".length)
                val idEnd = afterTrack.indexOf('/')
                if (idEnd >= 0) {
                    result = afterTrack.substring(idEnd + 1)
                }
            }
            return result
        }
    }
}

/**
 * What one import did, and what it could not do.
 */
data class ImportReport(
    val showsMatched: Int = 0,
    val showsSubscribed: Int = 0,
    val showsUnmatched: List<String> = emptyList(),
    val episodesMatched: Int = 0,
    val episodesUnmatched: Int = 0,
    val markedPlayed: Int = 0,
    val markedInProgress: Int = 0,
    val markedUnplayed: Int = 0,
    val starred: Int = 0,
    val archived: Int = 0,
    val queued: Int = 0,
    val foldersApplied: Int = 0,
    val foldersCleared: Int =  0,
    val playlistsApplied: Int = 0,
    val playlistEpisodesApplied: Int = 0,
    val playlistEpisodesUnmatched: List<String> = emptyList(),
    val playlistsSkipped: List<String> = emptyList(),
) {
    /** A one-line summary, for a person reading a log. */
    fun summary(): String = buildString {
        append("shows: $showsMatched matched")
        if (showsSubscribed > 0) append(", $showsSubscribed subscribed")
        if (showsUnmatched.isNotEmpty()) append(", ${showsUnmatched.size} unmatched")
        append(" | episodes: $episodesMatched matched")
        if (episodesUnmatched > 0) append(", $episodesUnmatched unmatched")
        append(" | played $markedPlayed, in progress $markedInProgress, unplayed $markedUnplayed")
        append(" | starred $starred, archived $archived")
        append(" | folders: $foldersApplied applied, $foldersCleared cleared")
        append(" | playlists: $playlistsApplied written, $playlistEpisodesApplied episodes")
        append(" | queued $queued")
    }
}

/** What the show phase did. */
data class ShowReport(
    val matched: Int = 0,
    val subscribed: Int = 0,
    val unmatched: List<String> = emptyList(),
)

/** What the episode phase did. */
data class EpisodeReport(
    val matched: Int = 0,
    val unmatched: Int = 0,
    val played: Int = 0,
    val inProgress: Int = 0,
    val unplayed: Int = 0,
    val starred: Int = 0,
    val archived: Int = 0,
)

/** What the playlist phase did. */
data class PlaylistReport(
    val foldersApplied: Int = 0,
    val foldersCleared: Int = 0,
    val playlistsApplied: Int = 0,
    val playlistEpisodesApplied: Int = 0,
    val playlistEpisodesUnmatched: List<String> = emptyList(),
    /** The playlists that could not be copied, and why they were left alone. */
    val playlistsSkipped: List<String> = emptyList(),
    val queued: Int = 0,
)

/** What the played-state sync phase did. */
data class PlayedSyncReport(
    val matched: Int = 0,
    val markedPlayed: Int = 0,
    val markedUnplayed: Int = 0,
)

/**
 * The matches made so far, kept between the phases of one import.
 *
 * A library of tens of thousands of episodes is imported in pieces, and the
 * playlists can only be written once the episodes they name have been
 * matched — so the matches outlive the request that made them.
 */
object PocketCastsImportSession {
    /** Pocket Casts show uuid -> Lohe show uuid. */
    var showMatches: Map<String, String> = emptyMap()

    /** Pocket Casts episode uuid -> Lohe episode uuid. */
    val episodeMatches: MutableMap<String, String> = mutableMapOf()

    /** The Lohe episodes that are listened to, in the order they were applied. */
    val played: MutableList<String> = mutableListOf()

    /** The Lohe episodes that are starred. */
    val starred: MutableList<String> = mutableListOf()

    /** The Lohe episodes that are part-played. */
    val inProgress: MutableList<String> = mutableListOf()

    /** Forgets everything, for an import that starts again. */
    fun reset() {
        showMatches = emptyMap()
        episodeMatches.clear()
        played.clear()
        starred.clear()
        inProgress.clear()
    }
}

/**
 * Normalises a stored publication date to epoch millis.
 *
 * The database holds feed dates in seconds, because that is what the feed
 * parser produces, and local files in millis. Comparing one with the other
 * needs them on the same scale.
 */
internal fun normalisePublished(published: Long): Long =
    if (published in 1..99_999_999_999L) published * 1000L else published
