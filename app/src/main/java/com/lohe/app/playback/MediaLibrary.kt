package com.lohe.app.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lohe.app.data.PlaylistRepository
import com.lohe.app.data.SubscriptionRepository
import com.lohe.app.data.db.entity.EpisodeEntity
import com.lohe.app.data.db.entity.PodcastEntity
import kotlinx.coroutines.flow.first

/**
 * The library as Android Auto sees it.
 *
 * Android Auto (and Car Assistant, and the Google Assistant media surface)
 * browse an app through the `MediaBrowserService` interface. Without a
 * browse tree they see an empty player: something that plays audio, but with
 * nothing to choose from. This is the tree they choose from.
 *
 * The shape is deliberately shallow — three shelves, two levels deep — because
 * a car screen is small and a list of four thousand episodes is not a list
 * anyone scrolls:
 *
 * ```
 * Lohe
 *  ├── Continue listening
 *  ├── Shows
 *  │    └── 49ers Rush Podcast
 *  │         └── episode, episode, episode
 *  └── Playlists
 *       └── 49ers
 *            └── episode, episode
 * ```
 */
object MediaLibrary {

    const val ROOT = "lohe-root"
    const val CONTINUE = "lohe-continue"
    const val UP_NEXT = "lohe-up-next"
    const val DOWNLOADS = "lohe-downloads"
    const val SHOWS = "lohe-shows"
    const val PLAYLISTS = "lohe-playlists"

    private const val SHOW_PREFIX = "lohe-show:"
    private const val PLAYLIST_PREFIX = "lohe-playlist:"

    fun showId(podcastUuid: String): String = SHOW_PREFIX + podcastUuid

    fun playlistId(playlistId: String): String = PLAYLIST_PREFIX + playlistId

    fun isShow(id: String): Boolean = id.startsWith(SHOW_PREFIX)

    fun isPlaylist(id: String): Boolean = id.startsWith(PLAYLIST_PREFIX)

    fun showUuidOf(id: String): String = id.removePrefix(SHOW_PREFIX)

    fun playlistIdOf(id: String): String = id.removePrefix(PLAYLIST_PREFIX)

    /**
     * How many episodes a show offers in the car.
     *
     * The library has thousands of episodes per show; a browse tree with all
     * of them in it is slow to build and slower to scroll. The recent ones are
     * the ones a listener in a car wants.
     */
    const val EPISODES_PER_SHOW = 100
}

/**
 * Turns the library into the items Android Auto browses.
 *
 * Kept apart from the service so the shape of the tree can be read on its
 * own, and so the service is left with nothing but the mechanics of serving
 * it.
 */
class LibraryCatalog(
    private val repository: SubscriptionRepository,
    private val playlistRepository: PlaylistRepository,
) {

    suspend fun rootItem(): MediaItem = browsable(
        mediaId = MediaLibrary.ROOT,
        title = "Lohe",
        artworkUrl = null,
    )

    /** The children of a node, or an empty list for anything unknown. */
    suspend fun children(parentId: String): List<MediaItem> = when {
        parentId == MediaLibrary.ROOT -> listOf(
            browsable(MediaLibrary.CONTINUE, "Continue listening", null),
            browsable(MediaLibrary.UP_NEXT, "Up Next", null),
            browsable(MediaLibrary.DOWNLOADS, "Downloads", null),
            browsable(MediaLibrary.SHOWS, "Shows", null),
            browsable(MediaLibrary.PLAYLISTS, "Playlists", null),
        )

        parentId == MediaLibrary.CONTINUE -> {
            val shows = showsByUuid()
            repository.observeInProgress().first()
                .filterNot { it.isDeleted || it.isArchived }
                .map { playable(it, shows) }
        }

        parentId == MediaLibrary.UP_NEXT -> {
            val shows = showsByUuid()
            repository.observeQueue().first()
                .filterNot { it.isDeleted || it.isArchived }
                .map { playable(it, shows) }
        }

        parentId == MediaLibrary.DOWNLOADS -> {
            val shows = showsByUuid()
            repository.observeDownloads().first()
                .mapNotNull { repository.episodeOnce(it.uuid) }
                .filterNot { it.isDeleted || it.isArchived }
                .map { playable(it, shows) }
        }

        parentId == MediaLibrary.SHOWS -> repository.observePodcasts().first()
            .map { browsable(MediaLibrary.showId(it.uuid), it.title, it.artworkUrl) }

        parentId == MediaLibrary.PLAYLISTS -> playlistRepository.playlists.value
            .map { browsable(MediaLibrary.playlistId(it.id), it.name, null) }

        MediaLibrary.isShow(parentId) -> {
            val shows = showsByUuid()
            repository.observeEpisodesFor(MediaLibrary.showUuidOf(parentId)).first()
                .filterNot { it.isDeleted }
                .take(MediaLibrary.EPISODES_PER_SHOW)
                .map { playable(it, shows) }
        }

        MediaLibrary.isPlaylist(parentId) -> {
            val shows = showsByUuid()
            val playlist = playlistRepository.playlists.value
                .firstOrNull { it.id == MediaLibrary.playlistIdOf(parentId) }
            playlist?.episodeUuids
                ?.let { repository.episodesOnce(it) }
                ?.map { playable(it, shows) }
                .orEmpty()
        }

        else -> emptyList()
    }

    /**
     * The item for a single id, for a browser asking about one row.
     *
     * Episode ids are the episode's own uuid; show and playlist ids are the
     * browsable nodes above.
     */
    suspend fun item(mediaId: String): MediaItem? {
        val shows = showsByUuid()
        return when {
            mediaId == MediaLibrary.ROOT -> rootItem()
            mediaId == MediaLibrary.CONTINUE ->
                browsable(MediaLibrary.CONTINUE, "Continue listening", null)
            mediaId == MediaLibrary.UP_NEXT ->
                browsable(MediaLibrary.UP_NEXT, "Up Next", null)
            mediaId == MediaLibrary.DOWNLOADS ->
                browsable(MediaLibrary.DOWNLOADS, "Downloads", null)
            mediaId == MediaLibrary.SHOWS ->
                browsable(MediaLibrary.SHOWS, "Shows", null)
            mediaId == MediaLibrary.PLAYLISTS ->
                browsable(MediaLibrary.PLAYLISTS, "Playlists", null)

            MediaLibrary.isShow(mediaId) -> {
                val show = shows[MediaLibrary.showUuidOf(mediaId)] ?: return null
                browsable(MediaLibrary.showId(show.uuid), show.title, show.artworkUrl)
            }

            MediaLibrary.isPlaylist(mediaId) -> {
                val playlist = playlistRepository.playlists.value
                    .firstOrNull { it.id == MediaLibrary.playlistIdOf(mediaId) }
                    ?: return null
                browsable(MediaLibrary.playlistId(playlist.id), playlist.name, null)
            }

            else -> playable(mediaId, shows)
        }
    }

    /**
     * The playable form of an episode, looked up by uuid.
     *
     * This is what makes a tap in the car work. A browser sends back the
     * item it was given — an id and nothing else — so the real URI, the show
     * it belongs to and the artwork have to be looked up again here.
     */
    suspend fun playable(mediaId: String, shows: Map<String, PodcastEntity>): MediaItem? {
        val episode = repository.episodeOnce(mediaId) ?: return null
        return playable(episode, shows)
    }

    /** Resolves a play request using only that episode and its show. */
    suspend fun playable(mediaId: String): MediaItem? {
        val episode = repository.episodeOnce(mediaId) ?: return null
        val show = repository.getPodcast(episode.podcastUuid)
        return playable(episode, if (show == null) emptyMap() else mapOf(show.uuid to show))
    }

    /** Podcast and episode matches for Android Auto voice and text search. */
    suspend fun search(query: String, limit: Int = 200): List<MediaItem> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()

        val shows = showsByUuid()
        val matchingShows = repository.observeSearchResults(cleanQuery).first()
            .take(limit.coerceAtLeast(0))
            .map { browsable(MediaLibrary.showId(it.uuid), it.title, it.artworkUrl) }
        val remaining = (limit - matchingShows.size).coerceAtLeast(0)
        val matchingEpisodes = if (remaining == 0) emptyList() else {
            repository.searchEpisodes(cleanQuery, remaining)
                .map { playable(it, shows) }
        }
        return matchingShows + matchingEpisodes
    }

    private fun playable(episode: EpisodeEntity, shows: Map<String, PodcastEntity>): MediaItem {
        val show = shows[episode.podcastUuid]
        val artwork = episode.artworkUrl?.takeIf { it.isNotBlank() } ?: show?.artworkUrl
        // fromUri, not setUri: the request metadata is what carries the
        // address to a browser, and setUri only fills in the local
        // configuration.
        return MediaItem.fromUri(episode.playbackUri)
            .buildUpon()
            .setMediaId(episode.uuid)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtist(show?.title)
                    .setAlbumTitle(show?.title)
                    .setDurationMs(episode.durationSeconds?.times(1000L))
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .apply {
                        artwork?.takeIf { it.isNotBlank() }?.let { setArtworkUri(Uri.parse(it)) }
                    }
                    .build(),
            )
            .build()
    }

    private fun browsable(mediaId: String, title: String, artworkUrl: String?): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .apply {
                        artworkUrl?.takeIf { it.isNotBlank() }?.let { setArtworkUri(Uri.parse(it)) }
                    }
                    .build(),
            )
            .build()

    suspend fun showsByUuid(): Map<String, PodcastEntity> =
        repository.observePodcasts().first().associateBy { it.uuid }
}
