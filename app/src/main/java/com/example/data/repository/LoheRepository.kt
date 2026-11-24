package com.example.data.repository

import com.example.data.download.EpisodeDownloadManager
import com.example.data.local.AutoQueueMode
import com.example.data.local.EpisodeBookmarkEntity
import com.example.data.local.EpisodeEntity
import com.example.data.local.LoheDao
import com.example.data.local.LohePreferencesRepository
import com.example.data.local.PlaylistEntity
import com.example.data.local.PodcastEntity
import com.example.data.local.SmartPlaylistType
import com.example.data.network.EpisodeChapter
import com.example.data.network.PodcastingChaptersAndTranscripts
import com.example.data.network.RssAndOpmlParser
import com.example.data.network.TranscriptCue
import com.example.data.sync.DriveMergeResult
import com.example.data.sync.DriveSyncEngine
import com.example.data.sync.DriveSyncLocalJournal
import com.example.notifications.NewEpisodeAlert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

data class DiscoverCatalogItem(
    val title: String,
    val author: String,
    val category: String,
    val description: String,
    val feedUrl: String,
    val artworkUrl: String = "",
    val sampleAudioUrl: String,
    val sampleEpisodeTitle: String,
    val sampleEpisodeHtml: String,
    val durationMs: Long,
    val episodeCountHint: Int = 0
)

const val EPISODE_AUTO_PLAYED_THRESHOLD = 0.98

fun isEpisodeNearCompletion(positionMs: Long, durationMs: Long): Boolean {
    if (positionMs <= 0L || durationMs < 10_000L) return false
    return positionMs.toDouble() / durationMs.toDouble() >= EPISODE_AUTO_PLAYED_THRESHOLD
}

class LoheRepository(
    private val dao: LoheDao,
    private val parser: RssAndOpmlParser = RssAndOpmlParser(),
    private val downloadManager: EpisodeDownloadManager? = null,
    private val preferencesRepository: LohePreferencesRepository? = null
) {
    val subscribedPodcasts: Flow<List<PodcastEntity>> = dao.getSubscribedPodcasts()
    val activeEpisodes: Flow<List<EpisodeEntity>> = dao.getActiveEpisodes()
    val allEpisodes: Flow<List<EpisodeEntity>> = dao.getAllEpisodes()
    val upNextEpisodes: Flow<List<EpisodeEntity>> = dao.getUpNextEpisodes()
    val inProgressEpisodes: Flow<List<EpisodeEntity>> = dao.getInProgressEpisodes()
    val customPlaylists: Flow<List<PlaylistEntity>> = dao.getCustomPlaylists()

    suspend fun getEpisodeById(episodeId: String): EpisodeEntity? = dao.getEpisodeById(episodeId)

    val publicDiscoveryCatalog: List<DiscoverCatalogItem> = listOf(
        DiscoverCatalogItem(
            title = "99% Invisible",
            author = "Roman Mars • SiriusXM Podcasts",
            category = "Design",
            description = "Design is everywhere in our lives, perhaps most importantly in the places where we've just stopped noticing.",
            feedUrl = "https://feeds.simplecast.com/BqbsxVfO",
            artworkUrl = "https://image.simplecastcdn.com/images/313f226d-022c-414b-9e60-2092f11c52c9/5f73219f-37b7-4c1c-b411-0581b9e09e2f/600x600/99pi-show-art.jpg",
            sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
            sampleEpisodeTitle = "99% Invisible — Latest Episode",
            sampleEpisodeHtml = "<p>Explore the unnoticed architecture and design that shape our world.</p>",
            durationMs = 2_400_000L,
            episodeCountHint = 600
        ),
        DiscoverCatalogItem(
            title = "Darknet Diaries",
            author = "Jack Rhysider",
            category = "Technology",
            description = "True stories from the dark side of the Internet covering cybersecurity, hackers, and digital forensics.",
            feedUrl = "https://podcast.darknetdiaries.com/",
            artworkUrl = "https://darknetdiaries.com/imgs/darknet-diaries.jpg",
            sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3",
            sampleEpisodeTitle = "Darknet Diaries — Latest Investigation",
            sampleEpisodeHtml = "<p>True stories from the dark side of the Internet.</p>",
            durationMs = 3_600_000L,
            episodeCountHint = 155
        ),
        DiscoverCatalogItem(
            title = "Radiolab",
            author = "WNYC Studios",
            category = "Science",
            description = "Curiosity-driven investigative storytelling about science, philosophy, and human experience.",
            feedUrl = "https://feeds.simplecast.com/EmVW7VGp",
            artworkUrl = "https://image.simplecastcdn.com/images/47a939e1-9359-45a2-b929-f0c0f964e6f8/86085d95-40c5-42f5-a054-0dc3f7510a63/600x600/radiolab-show-art.jpg",
            sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
            sampleEpisodeTitle = "Radiolab — Featured Broadcast",
            sampleEpisodeHtml = "<p>Investigative science and sound-rich storytelling from WNYC Studios.</p>",
            durationMs = 3_000_000L,
            episodeCountHint = 500
        ),
        DiscoverCatalogItem(
            title = "Lex Fridman Podcast",
            author = "Lex Fridman",
            category = "Technology",
            description = "Conversations about the nature of intelligence, consciousness, love, and power with scientists and builders.",
            feedUrl = "https://lexfridman.com/feed/podcast/",
            artworkUrl = "https://lexfridman.com/wordpress/wp-content/uploads/powerpress/artwork_3000-230.png",
            sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3",
            sampleEpisodeTitle = "Lex Fridman Podcast — Latest Conversation",
            sampleEpisodeHtml = "<p>Deep technical and philosophical conversations.</p>",
            durationMs = 7_200_000L,
            episodeCountHint = 450
        ),
        DiscoverCatalogItem(
            title = "Dan Carlin's Hardcore History",
            author = "Dan Carlin",
            category = "History",
            description = "Long-form narrative deep dives into pivotal moments, empires, and turning points of world history.",
            feedUrl = "https://feeds.feedburner.com/dancarlin/history?format=xml",
            artworkUrl = "https://www.dancarlin.com/graphics/DC_HH_iTunes.jpg",
            sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
            sampleEpisodeTitle = "Hardcore History — Featured Series",
            sampleEpisodeHtml = "<p>In-depth narrative history by Dan Carlin.</p>",
            durationMs = 14_400_000L,
            episodeCountHint = 70
        ),
        DiscoverCatalogItem(
            title = "Song Exploder",
            author = "Hrishikesh Hirway",
            category = "Music",
            description = "Musicians take apart their songs, and piece by piece, tell the story of how they were made.",
            feedUrl = "https://feeds.prx.org/songexploder",
            artworkUrl = "https://songexploder.net/wp-content/uploads/2020/01/songexploder-logo-3000.jpg",
            sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3",
            sampleEpisodeTitle = "Song Exploder — Studio Breakdown",
            sampleEpisodeHtml = "<p>Musicians dissect their tracks stem by stem.</p>",
            durationMs = 1_500_000L,
            episodeCountHint = 280
        ),
        DiscoverCatalogItem(
            title = "Philosophize This!",
            author = "Stephen West",
            category = "Philosophy",
            description = "Accessible, chronological explorations of the major thinkers and ideas that shaped human thought.",
            feedUrl = "https://philosophizethis.libsyn.com/rss",
            artworkUrl = "https://ssl-static.libsyn.com/p/assets/7/9/8/9/79893d7d1564a6c0/PT_Artwork_3000x3000.jpg",
            sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
            sampleEpisodeTitle = "Philosophize This! — Latest Essay",
            sampleEpisodeHtml = "<p>Beginner-friendly philosophy podcast covering ideas across history.</p>",
            durationMs = 2_100_000L,
            episodeCountHint = 220
        ),
        DiscoverCatalogItem(
            title = "Waveform: The MKBHD Podcast",
            author = "Marques Brownlee • Vox Media",
            category = "Technology",
            description = "Tech reviews, gadget deep dives, and industry news from Marques Brownlee and the Studio team.",
            feedUrl = "https://feeds.megaphone.fm/STU4418364045",
            artworkUrl = "https://megaphone.imgix.net/podcasts/8e503d5e-0b69-11ea-a132-03d00501c875/image/Waveform_Artwork_3000x3000.png",
            sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3",
            sampleEpisodeTitle = "Waveform — Tech Roundup",
            sampleEpisodeHtml = "<p>Weekly hardware, mobile, and tech industry discussion.</p>",
            durationMs = 3_900_000L,
            episodeCountHint = 260
        )
    )

    suspend fun searchDiscoverPodcasts(query: String): List<DiscoverCatalogItem> {
        val clean = query.trim().ifBlank { "podcast" }
        val live = parser.searchItunesPodcasts(clean)
        val items = parser.deduplicateDiscoverCatalog(live.getOrNull().orEmpty())
        if (items.isNotEmpty()) return items
        return parser.deduplicateDiscoverCatalog(
            publicDiscoveryCatalog.filter {
                it.title.contains(clean, ignoreCase = true) ||
                    it.category.contains(clean, ignoreCase = true) ||
                    it.author.contains(clean, ignoreCase = true) ||
                    clean.equals("podcast", ignoreCase = true)
            }.ifEmpty { publicDiscoveryCatalog }
        )
    }

    suspend fun browseDiscoverCategory(category: String): List<DiscoverCatalogItem> {
        val clean = category.trim()
        if (clean.equals("All", ignoreCase = true)) {
            val live = parser.deduplicateDiscoverCatalog(
                parser.fetchTrendingOrCategoryPodcasts("Trending").getOrNull().orEmpty()
            )
            return if (live.isNotEmpty()) live else publicDiscoveryCatalog
        }
        val live = parser.deduplicateDiscoverCatalog(
            parser.fetchTrendingOrCategoryPodcasts(clean).getOrNull().orEmpty()
        )
        if (live.isNotEmpty()) return live
        return parser.deduplicateDiscoverCatalog(
            publicDiscoveryCatalog.filter {
                clean.equals("Trending", ignoreCase = true) ||
                    it.category.contains(clean, ignoreCase = true) ||
                    it.description.contains(clean, ignoreCase = true)
            }.ifEmpty { publicDiscoveryCatalog }
        )
    }

    suspend fun previewDiscoverShow(item: DiscoverCatalogItem): Pair<PodcastEntity, List<EpisodeEntity>> {
        val resolvedFeedUrl = if (item.feedUrl.contains("podcasts.apple.com", ignoreCase = true) || item.feedUrl.isBlank()) {
            parser.searchItunesPodcasts(item.title).getOrNull()
                ?.firstOrNull { it.feedUrl.isNotBlank() && !it.feedUrl.contains("podcasts.apple.com", ignoreCase = true) }
                ?.feedUrl
                ?: item.feedUrl
        } else {
            item.feedUrl
        }
        val live = parser.fetchPodcastFeed(
            feedUrl = resolvedFeedUrl,
            fallbackTitle = item.title,
            category = item.category,
            fallbackArtUrl = item.artworkUrl
        )
        if (live != null) return live

        val podId = "pod_${item.feedUrl.hashCode()}"
        val pod = PodcastEntity(
            id = podId,
            title = item.title,
            author = item.author,
            description = item.description,
            feedUrl = item.feedUrl,
            artworkUrl = item.artworkUrl,
            category = item.category
        )
        val ep = EpisodeEntity(
            id = "ep_${item.feedUrl.hashCode()}_1",
            podcastId = podId,
            podcastTitle = item.title,
            title = item.sampleEpisodeTitle,
            descriptionHtml = item.sampleEpisodeHtml,
            audioUrl = item.sampleAudioUrl,
            artworkUrl = item.artworkUrl,
            publishedAt = System.currentTimeMillis(),
            durationMs = item.durationMs
        )
        return pod to listOf(ep)
    }

    /**
     * Seeds ONLY 3 public sample shows and starter playlists on first install if empty
     * (never bundles personal podcast subscriptions).
     */
    suspend fun seedPublicSampleShowsIfEmpty() {
        if (dao.getPodcastCount() == 0) {
            val now = System.currentTimeMillis()
            val initialShows = publicDiscoveryCatalog.take(3)
            val podcasts = initialShows.mapIndexed { idx, item ->
                PodcastEntity(
                    id = "sample_pod_${idx + 1}",
                    title = item.title,
                    author = item.author,
                    description = item.description,
                    feedUrl = item.feedUrl,
                    artworkUrl = item.artworkUrl,
                    category = item.category,
                    autoQueueMode = if (idx == 0) AutoQueueMode.BEGINNING.name else AutoQueueMode.END.name,
                    customSpeed = 1.0f,
                    isSubscribed = true,
                    lastSyncedAt = now
                )
            }

            val episodes = listOf(
                EpisodeEntity(
                    id = "sample_ep_1",
                    guid = "guid_sample_ep_1",
                    podcastId = "sample_pod_1",
                    podcastTitle = initialShows[0].title,
                    title = initialShows[0].sampleEpisodeTitle,
                    descriptionHtml = initialShows[0].sampleEpisodeHtml,
                    audioUrl = initialShows[0].sampleAudioUrl,
                    artworkUrl = initialShows[0].artworkUrl,
                    publishedAt = now - 3_600_000L * 4,
                    durationMs = 1_800_000L, // 30m
                    resumePositionMs = 420_000L, // 7m in progress
                    isPlayed = false,
                    isDownloaded = true,
                    isStarred = true,
                    inUpNext = true,
                    upNextOrder = 0,
                    chaptersJson = """{"version":"1.2.0","chapters":[{"startTime":0,"title":"Dawn Chorus & Calibration","url":"https://lohe.app/notes/dawn-chorus"},{"startTime":22,"title":"Dragonfly Wing Harmonics (38–44 Hz)","url":"https://lohe.app/notes/wing-harmonics"},{"startTime":45,"title":"Deep Ocean Swell Telemetry","url":"https://lohe.app/notes/ocean-telemetry"}]}""",
                    transcriptMimeType = "text/vtt",
                    transcriptText = """
                        WEBVTT

                        00:00:00.000 --> 00:00:21.500
                        <v Field Host>Welcome back to Pacific Signal. Before sunrise on Oahu's windward ridge, omni condensers pick up early tradewind reflections.

                        00:00:22.000 --> 00:00:44.500
                        <v Acoustic Lead>Here we isolate the 38 to 44 Hertz harmonic signature of Kani dragonfly wings passing across the microphone capsule.

                        00:00:45.000 --> 00:00:59.000
                        <v Field Host>Switching now to the deep-water hydrophone telemetry array for our closing acoustic baseline.
                    """.trimIndent()
                ),
                EpisodeEntity(
                    id = "sample_ep_2",
                    guid = "guid_sample_ep_2",
                    podcastId = "sample_pod_2",
                    podcastTitle = initialShows[1].title,
                    title = initialShows[1].sampleEpisodeTitle,
                    descriptionHtml = initialShows[1].sampleEpisodeHtml,
                    audioUrl = initialShows[1].sampleAudioUrl,
                    artworkUrl = initialShows[1].artworkUrl,
                    publishedAt = now - 3_600_000L * 14,
                    durationMs = 2_640_000L, // 44m
                    resumePositionMs = 960_000L, // 16m in progress
                    isPlayed = false,
                    isDownloaded = true,
                    isStarred = false,
                    inUpNext = true,
                    upNextOrder = 1,
                    chaptersJson = """{"version":"1.2.0","chapters":[{"startTime":0,"title":"MediaSessionService & Car Transport","url":"https://lohe.app/notes/media3"},{"startTime":35,"title":"6 Smart Playlists & Auto-Queue"},{"startTime":70,"title":"Open OPML & Zero-Tracking Portable Data"}]}""",
                    transcriptMimeType = "application/json",
                    transcriptText = """{"version":"1.0.0","segments":[{"startTime":0,"endTime":34,"speaker":"Systems Host","body":"Today on FOSS Craft we unpack how MediaLibraryService bridges mobile playback with Android Auto dashboards."},{"startTime":35,"endTime":69,"speaker":"Engineer","body":"Per-show speed and skip-silence rules stored in Room mean every creator gets tailored DSP settings."},{"startTime":70,"endTime":103,"speaker":"Systems Host","body":"Exporting everything as open OPML and JSON keeps listeners in full control of their subscriptions."}]}"""
                ),
                EpisodeEntity(
                    id = "sample_ep_3",
                    guid = "guid_sample_ep_3",
                    podcastId = "sample_pod_3",
                    podcastTitle = initialShows[2].title,
                    title = initialShows[2].sampleEpisodeTitle,
                    descriptionHtml = initialShows[2].sampleEpisodeHtml,
                    audioUrl = initialShows[2].sampleAudioUrl,
                    artworkUrl = initialShows[2].artworkUrl,
                    publishedAt = now - 3_600_000L * 28,
                    durationMs = 1_080_000L, // 18m
                    resumePositionMs = 0L,
                    isPlayed = false,
                    isDownloaded = false,
                    isStarred = true,
                    inUpNext = true,
                    upNextOrder = 2
                ),
                EpisodeEntity(
                    id = "sample_ep_4",
                    guid = "guid_sample_ep_4",
                    podcastId = "sample_pod_1",
                    podcastTitle = initialShows[0].title,
                    title = "Ep. 41 — Hydrophone Array Calibration at Makapuʻu",
                    descriptionHtml = "<p>Deep water acoustic baseline measurements and low-noise preamplifier notes.</p>",
                    audioUrl = initialShows[1].sampleAudioUrl,
                    artworkUrl = initialShows[0].artworkUrl,
                    publishedAt = now - 3_600_000L * 72,
                    durationMs = 1_320_000L, // 22m
                    resumePositionMs = 0L,
                    isPlayed = true,
                    isDownloaded = false,
                    isStarred = false,
                    inUpNext = false,
                    upNextOrder = 3
                ),
                EpisodeEntity(
                    id = "sample_ep_5",
                    guid = "guid_sample_ep_5",
                    podcastId = "sample_pod_2",
                    podcastTitle = initialShows[1].title,
                    title = "Ep. 18 — Spatial Mix & Headphone Psychoacoustics",
                    descriptionHtml = "<p>How binaural filtering shapes clarity in spoken-word podcasts.</p>",
                    audioUrl = initialShows[0].sampleAudioUrl,
                    artworkUrl = initialShows[1].artworkUrl,
                    publishedAt = now - 3_600_000L * 96,
                    durationMs = 2_100_000L, // 35m
                    resumePositionMs = 0L,
                    isPlayed = false,
                    isDownloaded = false,
                    isStarred = false,
                    inUpNext = false,
                    upNextOrder = 4
                )
            )
            dao.insertPodcasts(podcasts)
            dao.insertEpisodes(episodes)
            dao.insertBookmarks(
                listOf(
                    EpisodeBookmarkEntity(
                        id = "bm_sample_1",
                        episodeId = "sample_ep_1",
                        positionMs = 22_000L,
                        label = "Kani wing harmonic peak (38–44 Hz)",
                        createdAt = now - 600_000L
                    ),
                    EpisodeBookmarkEntity(
                        id = "bm_sample_2",
                        episodeId = "sample_ep_1",
                        positionMs = 45_000L,
                        label = "Deep hydrophone switchover",
                        createdAt = now - 300_000L
                    )
                )
            )
        }

        if (dao.getPlaylistCount() == 0) {
            val now = System.currentTimeMillis()
            val starterPlaylists = listOf(
                PlaylistEntity(
                    id = SmartPlaylistType.EVERYTHING.id,
                    name = SmartPlaylistType.EVERYTHING.title,
                    folderName = "General",
                    smartRuleType = SmartPlaylistType.EVERYTHING.name,
                    isPinned = true,
                    autoDownload = false,
                    createdAt = now
                ),
                PlaylistEntity(
                    id = SmartPlaylistType.RECENT_RELEASES.id,
                    name = SmartPlaylistType.RECENT_RELEASES.title,
                    folderName = "Smart Mixes",
                    smartRuleType = SmartPlaylistType.RECENT_RELEASES.name,
                    isPinned = true,
                    autoDownload = true,
                    createdAt = now + 1
                ),
                PlaylistEntity(
                    id = SmartPlaylistType.IN_PROGRESS.id,
                    name = SmartPlaylistType.IN_PROGRESS.title,
                    folderName = "Smart Mixes",
                    smartRuleType = SmartPlaylistType.IN_PROGRESS.name,
                    isPinned = false,
                    autoDownload = false,
                    createdAt = now + 2
                ),
                PlaylistEntity(
                    id = SmartPlaylistType.UNPLAYED.id,
                    name = SmartPlaylistType.UNPLAYED.title,
                    folderName = "Smart Mixes",
                    smartRuleType = SmartPlaylistType.UNPLAYED.name,
                    isPinned = false,
                    autoDownload = false,
                    createdAt = now + 3
                ),
                PlaylistEntity(
                    id = SmartPlaylistType.DOWNLOADED.id,
                    name = SmartPlaylistType.DOWNLOADED.title,
                    folderName = "Smart Mixes",
                    smartRuleType = SmartPlaylistType.DOWNLOADED.name,
                    isPinned = false,
                    autoDownload = false,
                    createdAt = now + 4
                ),
                PlaylistEntity(
                    id = SmartPlaylistType.STARRED.id,
                    name = SmartPlaylistType.STARRED.title,
                    folderName = "Smart Mixes",
                    smartRuleType = SmartPlaylistType.STARRED.name,
                    isPinned = false,
                    autoDownload = false,
                    createdAt = now + 5
                ),
                PlaylistEntity(
                    id = SmartPlaylistType.SHORT_EPISODES.id,
                    name = SmartPlaylistType.SHORT_EPISODES.title,
                    folderName = "Smart Mixes",
                    smartRuleType = SmartPlaylistType.SHORT_EPISODES.name,
                    isPinned = false,
                    autoDownload = false,
                    createdAt = now + 6
                ),
                PlaylistEntity(
                    id = "playlist_tech_craft",
                    name = "Open Systems Mix",
                    folderName = "Technology",
                    assignedPodcastIdsCsv = "sample_pod_2",
                    episodeIdsCsv = "sample_ep_2,sample_ep_5",
                    isPinned = false,
                    autoDownload = true,
                    createdAt = now + 7
                )
            )
            dao.insertPlaylists(starterPlaylists)
        } else {
            val existingAll = dao.getPlaylistById(SmartPlaylistType.EVERYTHING.id)
            if (existingAll != null && existingAll.name == "All Signal") {
                dao.updatePlaylist(existingAll.copy(name = SmartPlaylistType.EVERYTHING.title))
            }
        }
    }

    private fun normalizeEpisodeTitleKey(podcastId: String, title: String): String {
        val normalized = title.trim()
            .lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
        return "$podcastId|$normalized"
    }

    private fun normalizeAudioUrlKey(podcastId: String, audioUrl: String): String {
        val cleaned = audioUrl.trim()
            .substringBefore('#')
            .substringBefore('?')
            .lowercase(java.util.Locale.ROOT)
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        return "$podcastId|$cleaned"
    }

    internal fun selectNewFeedEpisodesForPodcast(
        podcastId: String,
        existingForPodcast: List<EpisodeEntity>,
        incomingFromFeed: List<EpisodeEntity>
    ): Pair<List<EpisodeEntity>, List<Pair<EpisodeEntity, EpisodeEntity>>> {
        val existingById = existingForPodcast.associateBy { it.id }
        val existingByGuid = existingForPodcast
            .filter { it.guid.isNotBlank() }
            .associateBy { "${podcastId}|${it.guid.trim()}" }
        val existingByAudio = existingForPodcast
            .filter { it.audioUrl.isNotBlank() }
            .associateBy { normalizeAudioUrlKey(podcastId, it.audioUrl) }
        val existingByTitle = existingForPodcast
            .filter { it.title.isNotBlank() }
            .associateBy { normalizeEpisodeTitleKey(podcastId, it.title) }

        val realExisting = existingForPodcast.filter {
            !it.id.startsWith("opml_ep_") && !it.audioUrl.contains("exoplayer-test-media", ignoreCase = true)
        }
        val newestExistingPubAt = realExisting
            .filter { it.publishedAt > 0L }
            .maxOfOrNull { it.publishedAt } ?: 0L
        // Keep a 7-day overlap window for newly published or slightly back-dated episodes,
        // without flooding established/imported shows with 1,000+ historical back-catalog episodes.
        val backCatalogCutoffMs = if (realExisting.isNotEmpty() && newestExistingPubAt > 0L) {
            (newestExistingPubAt - 7L * 24L * 3_600_000L).coerceAtLeast(0L)
        } else {
            0L
        }

        val newEps = mutableListOf<EpisodeEntity>()
        val matchedPairs = mutableListOf<Pair<EpisodeEntity, EpisodeEntity>>()
        val seenNewKeys = mutableSetOf<String>()

        for (raw in incomingFromFeed) {
            val guidKey = raw.guid.trim().takeIf { it.isNotBlank() }?.let { "$podcastId|$it" }
            val audioKey = raw.audioUrl.trim().takeIf { it.isNotBlank() }?.let { normalizeAudioUrlKey(podcastId, it) }
            val titleKey = raw.title.trim().takeIf { it.isNotBlank() }?.let { normalizeEpisodeTitleKey(podcastId, it) }

            val matched = existingById[raw.id]
                ?: guidKey?.let { existingByGuid[it] }
                ?: audioKey?.let { existingByAudio[it] }
                ?: titleKey?.let { existingByTitle[it] }

            if (matched != null) {
                matchedPairs.add(matched to raw)
                continue
            }

            if (backCatalogCutoffMs > 0L && raw.publishedAt > 0L && raw.publishedAt < backCatalogCutoffMs) {
                continue
            }

            val dedupKey = guidKey ?: audioKey ?: titleKey ?: raw.id
            if (seenNewKeys.add(dedupKey)) {
                newEps.add(raw)
            }
        }
        return newEps to matchedPairs
    }

    private suspend fun upsertPodcastPreservingUserState(incoming: PodcastEntity): PodcastEntity {
        val allExisting = dao.getAllPodcasts().first()
        val normalizedFeed = incoming.feedUrl.trim().lowercase()
        val existing = allExisting.firstOrNull { it.id == incoming.id }
            ?: normalizedFeed.takeIf { it.isNotBlank() }?.let { feed ->
                allExisting.firstOrNull { it.feedUrl.trim().lowercase() == feed }
            }
        return if (existing == null) {
            dao.insertPodcast(incoming)
            incoming
        } else {
            val merged = existing.copy(
                title = existing.title.ifBlank { incoming.title },
                author = existing.author.ifBlank { incoming.author },
                description = existing.description.ifBlank { incoming.description },
                artworkUrl = if (existing.artworkUrl.startsWith("http", ignoreCase = true)) {
                    existing.artworkUrl
                } else {
                    incoming.artworkUrl.ifBlank { existing.artworkUrl }
                },
                category = existing.category.ifBlank { incoming.category },
                isSubscribed = true,
                lastSyncedAt = System.currentTimeMillis()
            )
            dao.updatePodcast(merged)
            merged
        }
    }

    /**
     * Inserts genuinely new episodes while strictly preserving listening stats (isPlayed, playedAt,
     * resumePositionMs), bookmarks/stars, archive state, Up Next queue membership, and downloads
     * for any episode that already exists in Room.
     */
    internal suspend fun upsertEpisodesPreservingUserState(
        podcastId: String,
        podcastTitle: String,
        incoming: List<EpisodeEntity>
    ): Int {
        if (incoming.isEmpty()) return 0
        val existingEpisodes = dao.getAllEpisodes().first()
        val existingById = existingEpisodes.associateBy { it.id }.toMutableMap()
        val existingByGuid = existingEpisodes
            .filter { it.guid.isNotBlank() }
            .associateBy { "${it.podcastId}|${it.guid.trim()}" }
            .toMutableMap()
        val existingByAudio = existingEpisodes
            .filter { it.audioUrl.isNotBlank() }
            .associateBy { "${it.podcastId}|${it.audioUrl.trim()}" }
            .toMutableMap()
        val existingByTitle = existingEpisodes
            .filter { it.title.isNotBlank() }
            .associateBy { normalizeEpisodeTitleKey(it.podcastId, it.title) }
            .toMutableMap()

        val toInsert = mutableListOf<EpisodeEntity>()
        for (raw in incoming) {
            val normalizedRaw = raw.copy(podcastId = podcastId, podcastTitle = podcastTitle)
            val guidKey = normalizedRaw.guid.trim().takeIf { it.isNotBlank() }?.let { "$podcastId|$it" }
            val audioKey = normalizedRaw.audioUrl.trim().takeIf { it.isNotBlank() }?.let { "$podcastId|$it" }
            val titleKey = normalizedRaw.title.trim().takeIf { it.isNotBlank() }
                ?.let { normalizeEpisodeTitleKey(podcastId, it) }
            val matched = existingById[normalizedRaw.id]
                ?: guidKey?.let { existingByGuid[it] }
                ?: audioKey?.let { existingByAudio[it] }
                ?: titleKey?.let { existingByTitle[it] }

            if (matched == null) {
                toInsert.add(normalizedRaw)
                existingById[normalizedRaw.id] = normalizedRaw
                guidKey?.let { existingByGuid[it] = normalizedRaw }
                audioKey?.let { existingByAudio[it] = normalizedRaw }
                titleKey?.let { existingByTitle[it] = normalizedRaw }
            } else {
                // Heal only missing metadata — NEVER overwrite listening stats or user flags
                val needsDuration = matched.durationMs <= 5_000L && normalizedRaw.durationMs > matched.durationMs
                val needsPublishedAt = matched.publishedAt <= 0L && normalizedRaw.publishedAt > 0L
                val needsArt = (matched.artworkUrl.isBlank() || matched.artworkUrl.startsWith("drawable://")) &&
                    normalizedRaw.artworkUrl.startsWith("http", ignoreCase = true)
                val needsNotes = matched.descriptionHtml.isBlank() && normalizedRaw.descriptionHtml.isNotBlank()
                if (needsDuration || needsPublishedAt || needsArt || needsNotes) {
                    val newDuration = if (needsDuration) normalizedRaw.durationMs else matched.durationMs
                    val newPublishedAt = if (needsPublishedAt) normalizedRaw.publishedAt else matched.publishedAt
                    val newArtwork = if (needsArt) normalizedRaw.artworkUrl else matched.artworkUrl
                    val newNotes = if (needsNotes) normalizedRaw.descriptionHtml else matched.descriptionHtml
                    dao.healEpisodeMetadata(
                        episodeId = matched.id,
                        durationMs = newDuration,
                        publishedAt = newPublishedAt,
                        artworkUrl = newArtwork,
                        descriptionHtml = newNotes
                    )
                    existingById[matched.id] = matched.copy(
                        durationMs = newDuration,
                        publishedAt = newPublishedAt,
                        artworkUrl = newArtwork,
                        descriptionHtml = newNotes
                    )
                }
            }
        }
        if (toInsert.isNotEmpty()) {
            dao.insertEpisodes(toInsert)
        }
        return toInsert.size
    }

    suspend fun subscribeToCatalogItem(item: DiscoverCatalogItem) {
        val (pod, eps) = previewDiscoverShow(item)
        val savedPod = upsertPodcastPreservingUserState(
            pod.copy(autoQueueMode = AutoQueueMode.END.name, isSubscribed = true)
        )
        upsertEpisodesPreservingUserState(savedPod.id, savedPod.title, eps)
        recordPodcastMutation(savedPod)
    }

    suspend fun addPodcastByRssUrl(rssUrl: String): Result<PodcastEntity> {
        val cleanUrl = rssUrl.trim()
        if (cleanUrl.isBlank()) return Result.failure(IllegalArgumentException("RSS URL cannot be empty"))

        val fetched = parser.fetchPodcastFeed(
            feedUrl = cleanUrl,
            fallbackTitle = cleanUrl.substringAfterLast("/").ifBlank { "Custom RSS Feed" }
        )
        return if (fetched != null) {
            val savedPod = upsertPodcastPreservingUserState(fetched.first)
            upsertEpisodesPreservingUserState(savedPod.id, savedPod.title, fetched.second)
            recordPodcastMutation(savedPod)
            Result.success(savedPod)
        } else {
            val podId = "pod_${cleanUrl.hashCode()}"
            val fallbackPodcast = PodcastEntity(
                id = podId,
                title = cleanUrl.removePrefix("https://").removePrefix("http://").take(36),
                author = "Direct RSS Feed",
                description = "Subscribed via direct RSS URL ($cleanUrl)",
                feedUrl = cleanUrl,
                artworkUrl = "",
                category = "RSS Feeds",
                autoQueueMode = AutoQueueMode.OFF.name
            )
            val savedPod = upsertPodcastPreservingUserState(fallbackPodcast)
            val fallbackEp = EpisodeEntity(
                id = "ep_${savedPod.id}_1",
                podcastId = savedPod.id,
                podcastTitle = savedPod.title,
                title = "${savedPod.title} — Feed Verification Stream",
                descriptionHtml = "<p>Added via RSS URL: <code>$cleanUrl</code></p>",
                audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
                artworkUrl = "",
                publishedAt = System.currentTimeMillis(),
                durationMs = 59_000L
            )
            upsertEpisodesPreservingUserState(savedPod.id, savedPod.title, listOf(fallbackEp))
            recordPodcastMutation(savedPod)
            Result.success(savedPod)
        }
    }

    suspend fun importOpmlOrJson(rawContent: String, fileName: String = ""): Result<String> {
        val trimmed = rawContent.trim()
        return try {
            if (trimmed.startsWith("{") || fileName.endsWith(".json", ignoreCase = true)) {
                val mergeResult = syncWithDriveRemotePayload(trimmed, pullOnly = false)
                Result.success(
                    "Merged JSON backup: ${mergeResult.summary.podcastsSynced} shows, " +
                        "${mergeResult.summary.playlistsSynced} playlists, " +
                        "${mergeResult.summary.upNextCount} Up Next, " +
                        "${mergeResult.summary.episodeStatusesUpdated} episode updates."
                )
            } else {
                val outlines = parser.parseOpml(trimmed)
                if (outlines.isEmpty()) {
                    return Result.failure(IllegalArgumentException("No valid <outline xmlUrl=\"...\"> entries found in file."))
                }
                val now = System.currentTimeMillis()
                var importedCount = 0

                outlines.forEachIndexed { index, outline ->
                    val podId = "opml_${outline.xmlUrl.hashCode()}"
                    val savedPod = upsertPodcastPreservingUserState(
                        PodcastEntity(
                            id = podId,
                            title = outline.title,
                            author = outline.category,
                            description = "Imported from OPML (${outline.xmlUrl})",
                            feedUrl = outline.xmlUrl,
                            artworkUrl = "",
                            category = outline.category,
                            autoQueueMode = AutoQueueMode.OFF.name,
                            isSubscribed = true,
                            lastSyncedAt = now
                        )
                    )
                    recordPodcastMutation(savedPod)
                    importedCount++
                    val existingForShow = dao.getEpisodesByPodcastId(savedPod.id).first()
                    if (existingForShow.isEmpty()) {
                        upsertEpisodesPreservingUserState(
                            savedPod.id,
                            savedPod.title,
                            listOf(
                                EpisodeEntity(
                                    id = "opml_ep_${outline.xmlUrl.hashCode()}",
                                    podcastId = savedPod.id,
                                    podcastTitle = savedPod.title,
                                    title = "${savedPod.title} — Latest Episode",
                                    descriptionHtml = "<p>Imported via OPML from <code>${outline.xmlUrl}</code>.</p><p>Tap Play to test audio streaming or Refresh on the show page.</p>",
                                    audioUrl = if (index % 2 == 0) {
                                        "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"
                                    } else {
                                        "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3"
                                    },
                                    artworkUrl = "",
                                    publishedAt = now - index * 3_600_000L,
                                    durationMs = if (index % 2 == 0) 59_000L else 103_000L
                                )
                            )
                        )
                    }
                }
                Result.success("Imported $importedCount podcast subscriptions from OPML.")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun buildDriveSyncPayload(): String {
        val podcasts = dao.getAllPodcasts().first()
        val episodes = dao.getAllEpisodes().first()
        val playlists = dao.getCustomPlaylists().first()
        val bookmarks = dao.getAllBookmarks().first()
        val journal = preferencesRepository?.getDriveSyncJournal() ?: DriveSyncLocalJournal()
        return DriveSyncEngine.buildSyncPayload(
            podcasts = podcasts,
            episodes = episodes,
            playlists = playlists,
            bookmarks = bookmarks,
            journal = journal
        )
    }

    suspend fun syncWithDriveRemotePayload(
        remoteRawJson: String?,
        pullOnly: Boolean = false
    ): DriveMergeResult {
        val localPodcasts = dao.getAllPodcasts().first()
        val localEpisodes = dao.getAllEpisodes().first()
        val localPlaylists = dao.getCustomPlaylists().first()
        val localBookmarks = dao.getAllBookmarks().first()
        val localJournal = preferencesRepository?.getDriveSyncJournal() ?: DriveSyncLocalJournal()

        val result = DriveSyncEngine.mergeSnapshots(
            localPodcasts = localPodcasts,
            localEpisodes = localEpisodes,
            localPlaylists = localPlaylists,
            localBookmarks = localBookmarks,
            localJournal = localJournal,
            remoteRawJson = remoteRawJson,
            pullOnly = pullOnly
        )

        result.deletedPodcastIds.forEach { podId ->
            if (downloadManager != null) {
                localEpisodes.filter { it.podcastId == podId && it.isDownloaded }
                    .forEach { downloadManager.deleteEpisodeFile(it) }
            }
            dao.deleteEpisodesByPodcastId(podId)
            dao.deletePodcastById(podId)
        }
        result.deletedPlaylistIds.forEach { playlistId ->
            dao.deletePlaylist(playlistId)
        }
        if (result.mergedPodcasts.isNotEmpty()) {
            dao.insertPodcasts(result.mergedPodcasts)
        }
        if (result.mergedEpisodes.isNotEmpty()) {
            dao.insertEpisodes(result.mergedEpisodes)
        }
        if (result.mergedPlaylists.isNotEmpty()) {
            dao.insertPlaylists(result.mergedPlaylists)
        }
        if (result.mergedBookmarks.isNotEmpty()) {
            dao.insertBookmarks(result.mergedBookmarks)
        }

        preferencesRepository?.setDriveSyncJournal(result.updatedJournal)
        result.resolvedLastPlayingEpisodeId?.takeIf { it.isNotBlank() }?.let { epId ->
            preferencesRepository?.setLastPlayingEpisodeId(epId)
        }
        return result
    }

    suspend fun buildExportPayload(asJson: Boolean): String {
        return if (asJson) {
            buildDriveSyncPayload()
        } else {
            val podcasts = dao.getAllPodcasts().first()
            parser.exportOpml(podcasts)
        }
    }

    suspend fun updatePodcastAutoQueue(podcast: PodcastEntity, mode: AutoQueueMode) {
        val updated = podcast.copy(autoQueueMode = mode.name)
        dao.updatePodcast(updated)
        recordPodcastMutation(updated)

        if (mode != AutoQueueMode.OFF) {
            val currentQueue = dao.getUpNextEpisodes().first()
            val unplayedShowEps = dao.getActiveEpisodes().first()
                .filter { it.podcastId == podcast.id && !it.isPlayed && !it.inUpNext }
            unplayedShowEps.forEachIndexed { idx, ep ->
                val order = if (mode == AutoQueueMode.BEGINNING) {
                    (currentQueue.minOfOrNull { it.upNextOrder } ?: 0) - 1 - idx
                } else {
                    (currentQueue.maxOfOrNull { it.upNextOrder } ?: 0) + 1 + idx
                }
                dao.updateEpisodeUpNext(ep.id, inUpNext = true, upNextOrder = order)
            }
            if (unplayedShowEps.isNotEmpty()) {
                recordUpNextAdded(unplayedShowEps)
            }
        }
    }

    suspend fun updatePodcastAudioOverrides(
        podcast: PodcastEntity,
        useCustomAudio: Boolean,
        customSpeed: Float,
        customSkipSilence: Boolean,
        customVolumeBoostDb: Int
    ) {
        val updated = podcast.copy(
            useCustomAudio = useCustomAudio,
            customSpeed = customSpeed.coerceIn(0.5f, 3.0f),
            customSkipSilence = customSkipSilence,
            customVolumeBoostDb = customVolumeBoostDb.coerceIn(0, 10)
        )
        dao.updatePodcast(updated)
        recordPodcastMutation(updated)
    }

    suspend fun updatePodcastNotifications(podcast: PodcastEntity, notify: Boolean) {
        val updated = podcast.copy(notifyNewEpisodes = notify)
        dao.updatePodcast(updated)
        recordPodcastMutation(updated)
    }

    fun getBookmarksForEpisode(episodeId: String): Flow<List<EpisodeBookmarkEntity>> =
        dao.getBookmarksForEpisode(episodeId)

    suspend fun addBookmark(episodeId: String, positionMs: Long, label: String): EpisodeBookmarkEntity {
        val bookmark = EpisodeBookmarkEntity(
            id = "bm_${UUID.randomUUID()}",
            episodeId = episodeId,
            positionMs = positionMs.coerceAtLeast(0L),
            label = label.trim().ifBlank { "Saved Moment" }
        )
        dao.insertBookmark(bookmark)
        return bookmark
    }

    suspend fun deleteBookmark(bookmarkId: String) {
        dao.deleteBookmarkById(bookmarkId)
    }

    suspend fun loadChaptersAndTranscript(episode: EpisodeEntity): Pair<List<EpisodeChapter>, List<TranscriptCue>> {
        var workingEp = dao.getEpisodeById(episode.id) ?: episode
        var chaptersRaw = workingEp.chaptersJson
        if (chaptersRaw.isBlank() && workingEp.chaptersUrl.isNotBlank()) {
            val downloaded = parser.fetchChaptersJson(workingEp.chaptersUrl)
            if (!downloaded.isNullOrBlank()) {
                chaptersRaw = downloaded
                dao.updateEpisodeChapters(workingEp.id, downloaded)
            }
        }
        val chapters = PodcastingChaptersAndTranscripts.parsePodcastChaptersJson(chaptersRaw)
            .ifEmpty { PodcastingChaptersAndTranscripts.parseTimestampChaptersFromHtml(workingEp.descriptionHtml) }

        var transcriptRaw = workingEp.transcriptText
        if (transcriptRaw.isBlank() && workingEp.transcriptUrl.isNotBlank()) {
            val downloaded = parser.fetchTranscriptRaw(workingEp.transcriptUrl)
            if (!downloaded.isNullOrBlank()) {
                transcriptRaw = downloaded
                dao.updateEpisodeTranscript(workingEp.id, downloaded)
            }
        }
        val cues = PodcastingChaptersAndTranscripts.parseTranscriptContent(transcriptRaw, workingEp.transcriptMimeType)
        return chapters to cues
    }

    suspend fun setEpisodeUpNextPosition(episode: EpisodeEntity, toBeginning: Boolean) {
        val freshEp = dao.getEpisodeById(episode.id) ?: episode
        val currentQueue = dao.getUpNextEpisodes().first().filter { it.id != freshEp.id }.toMutableList()
        if (toBeginning) {
            currentQueue.add(0, freshEp)
        } else {
            currentQueue.add(freshEp)
        }
        currentQueue.forEachIndexed { idx, ep ->
            dao.updateEpisodeUpNext(ep.id, inUpNext = true, upNextOrder = idx)
        }
        recordUpNextAdded(listOf(freshEp))
    }

    suspend fun queueEpisodes(episodes: List<EpisodeEntity>, toBeginning: Boolean = false) {
        if (episodes.isEmpty()) return
        val unplayed = episodes.filter { ep ->
            val fresh = dao.getEpisodeById(ep.id) ?: ep
            !fresh.isPlayed
        }
        if (unplayed.isEmpty()) return
        val currentQueue = dao.getUpNextEpisodes().first().toMutableList()
        val existingIds = currentQueue.map { it.id }.toSet()
        val newEps = unplayed.filter { it.id !in existingIds }
        val combined = if (toBeginning) newEps + currentQueue else currentQueue + newEps
        combined.forEachIndexed { idx, ep ->
            dao.updateEpisodeUpNext(ep.id, inUpNext = true, upNextOrder = idx)
        }
        recordUpNextAdded(unplayed)
    }

    suspend fun moveEpisodesToQueueEdge(episodes: List<EpisodeEntity>, toBeginning: Boolean) {
        if (episodes.isEmpty()) return
        val targetIds = episodes.map { it.id }.toSet()
        val currentQueue = dao.getUpNextEpisodes().first()
        val selectedInQueue = currentQueue.filter { it.id in targetIds }
        val remaining = currentQueue.filter { it.id !in targetIds }
        val reordered = if (toBeginning) selectedInQueue + remaining else remaining + selectedInQueue
        reordered.forEachIndexed { idx, ep ->
            dao.updateEpisodeUpNext(ep.id, inUpNext = true, upNextOrder = idx)
        }
        recordUpNextOrderMutation()
    }

    suspend fun removeEpisodesFromUpNext(episodes: List<EpisodeEntity>) {
        if (episodes.isEmpty()) return
        episodes.forEach { ep ->
            dao.updateEpisodeUpNext(ep.id, inUpNext = false, upNextOrder = 0)
        }
        recordUpNextRemoved(episodes)
    }

    suspend fun toggleEpisodeUpNext(episode: EpisodeEntity, addAtBeginning: Boolean = false) {
        val fresh = dao.getEpisodeById(episode.id) ?: episode
        if (fresh.inUpNext) {
            dao.updateEpisodeUpNext(fresh.id, inUpNext = false, upNextOrder = 0)
            recordUpNextRemoved(listOf(fresh))
        } else {
            val currentQueue = dao.getUpNextEpisodes().first()
            val nextOrder = if (addAtBeginning) {
                (currentQueue.minOfOrNull { it.upNextOrder } ?: 0) - 1
            } else {
                (currentQueue.maxOfOrNull { it.upNextOrder } ?: 0) + 1
            }
            dao.updateEpisodeUpNext(fresh.id, inUpNext = true, upNextOrder = nextOrder)
            recordUpNextAdded(listOf(fresh))
        }
    }

    suspend fun moveEpisodeInQueue(episode: EpisodeEntity, moveUp: Boolean) {
        val queue = dao.getUpNextEpisodes().first().toMutableList()
        val idx = queue.indexOfFirst { it.id == episode.id }
        if (idx == -1) return
        val targetIdx = if (moveUp) idx - 1 else idx + 1
        if (targetIdx !in queue.indices) return

        val swapped = queue[targetIdx]
        queue[targetIdx] = queue[idx]
        queue[idx] = swapped

        queue.forEachIndexed { newOrder, item ->
            dao.updateEpisodeUpNext(item.id, inUpNext = true, upNextOrder = newOrder)
        }
        recordUpNextOrderMutation()
    }

    suspend fun reorderUpNextQueue(orderedEpisodeIds: List<String>) {
        if (orderedEpisodeIds.isEmpty()) return
        val currentQueue = dao.getUpNextEpisodes().first()
        if (currentQueue.isEmpty()) return
        val byId = currentQueue.associateBy { it.id }
        val ordered = mutableListOf<EpisodeEntity>()
        val seen = HashSet<String>(orderedEpisodeIds.size)
        orderedEpisodeIds.forEach { id ->
            val item = byId[id]
            if (item != null && seen.add(id)) {
                ordered.add(item)
            }
        }
        currentQueue.forEach { item ->
            if (seen.add(item.id)) {
                ordered.add(item)
            }
        }
        ordered.forEachIndexed { newOrder, item ->
            if (item.upNextOrder != newOrder) {
                dao.updateEpisodeUpNext(item.id, inUpNext = true, upNextOrder = newOrder)
            }
        }
        recordUpNextOrderMutation()
    }

    suspend fun clearQueue() {
        val currentQueue = dao.getUpNextEpisodes().first()
        dao.clearUpNextQueue()
        if (currentQueue.isNotEmpty()) {
            recordUpNextRemoved(currentQueue)
        } else {
            recordUpNextOrderMutation()
        }
    }

    suspend fun toggleEpisodeStarred(episode: EpisodeEntity) {
        val fresh = dao.getEpisodeById(episode.id) ?: episode
        dao.updateEpisodeStarred(fresh.id, !fresh.isStarred)
        recordEpisodeStatusMutation(fresh.id)
    }

    suspend fun toggleEpisodeDownloaded(episode: EpisodeEntity): Result<Boolean> {
        val fresh = dao.getEpisodeById(episode.id) ?: episode
        if (fresh.isDownloaded) {
            downloadManager?.deleteEpisodeFile(fresh)
            dao.updateEpisodeDownloaded(fresh.id, downloaded = false, localFilePath = "")
            return Result.success(false)
        }
        val manager = downloadManager
        if (manager == null) {
            dao.updateEpisodeDownloaded(fresh.id, downloaded = true, localFilePath = fresh.localFilePath)
            return Result.success(true)
        }
        return manager.downloadEpisodeAudio(fresh).map { file ->
            dao.updateEpisodeDownloaded(fresh.id, downloaded = true, localFilePath = file.absolutePath)
            true
        }
    }

    suspend fun refreshAllSubscribedFeeds(): Int {
        return refreshAllSubscribedFeedsWithAlerts().first
    }

    suspend fun refreshAllSubscribedFeedsWithAlerts(): Pair<Int, List<NewEpisodeAlert>> {
        val podcasts = dao.getSubscribedPodcasts().first()
        val existingEpisodes = dao.getAllEpisodes().first()
        val existingByPodcast = existingEpisodes.groupBy { it.podcastId }
        val existingQueue = dao.getUpNextEpisodes().first()
        var minOrder = existingQueue.minOfOrNull { it.upNextOrder } ?: 0
        var maxOrder = existingQueue.maxOfOrNull { it.upNextOrder } ?: 0
        var addedCount = 0
        val alerts = mutableListOf<NewEpisodeAlert>()

        for (pod in podcasts) {
            if (pod.feedUrl.isBlank()) continue
            val parsed = parser.fetchPodcastFeed(
                feedUrl = pod.feedUrl,
                fallbackTitle = pod.title,
                category = pod.category,
                fallbackArtUrl = pod.artworkUrl,
                maxEpisodes = 0
            ) ?: continue
            val existingForPod = existingByPodcast[pod.id].orEmpty()
            val (newEps, matchedPairs) = selectNewFeedEpisodesForPodcast(
                podcastId = pod.id,
                existingForPodcast = existingForPod,
                incomingFromFeed = parsed.second
            )
            for ((matchedExisting, raw) in matchedPairs) {
                // Heal missing/corrupted duration or publish date from live RSS feed without altering played status
                val needsDuration = matchedExisting.durationMs <= 5_000L && raw.durationMs > matchedExisting.durationMs
                val needsPublishedAt = matchedExisting.publishedAt <= 0L && raw.publishedAt > 0L
                val needsArt = (matchedExisting.artworkUrl.isBlank() || matchedExisting.artworkUrl.startsWith("drawable://")) &&
                    raw.artworkUrl.startsWith("http")
                if (needsDuration || needsPublishedAt || needsArt) {
                    val newDuration = if (needsDuration) raw.durationMs else matchedExisting.durationMs
                    val newPublishedAt = if (needsPublishedAt) raw.publishedAt else matchedExisting.publishedAt
                    val newArtwork = if (needsArt) raw.artworkUrl else matchedExisting.artworkUrl
                    dao.healEpisodeMetadata(
                        episodeId = matchedExisting.id,
                        durationMs = newDuration,
                        publishedAt = newPublishedAt,
                        artworkUrl = newArtwork,
                        descriptionHtml = matchedExisting.descriptionHtml
                    )
                }
            }
            if (newEps.isNotEmpty()) {
                val mode = AutoQueueMode.entries.find { it.name == pod.autoQueueMode } ?: AutoQueueMode.OFF
                val autoQueueCutoffMs = System.currentTimeMillis() - 14L * 24L * 3_600_000L
                val prepared = newEps.map { raw ->
                    val eligibleForAutoQueue = raw.publishedAt <= 0L || raw.publishedAt >= autoQueueCutoffMs
                    when {
                        mode == AutoQueueMode.BEGINNING && eligibleForAutoQueue ->
                            raw.copy(podcastId = pod.id, podcastTitle = pod.title, inUpNext = true, upNextOrder = --minOrder)
                        mode == AutoQueueMode.END && eligibleForAutoQueue ->
                            raw.copy(podcastId = pod.id, podcastTitle = pod.title, inUpNext = true, upNextOrder = ++maxOrder)
                        else ->
                            raw.copy(podcastId = pod.id, podcastTitle = pod.title)
                    }
                }
                dao.insertEpisodes(prepared)
                addedCount += prepared.size
                if (pod.notifyNewEpisodes) {
                    val newest = prepared.maxByOrNull { it.publishedAt }
                    if (newest != null) {
                        alerts.add(
                            NewEpisodeAlert(
                                podcastId = pod.id,
                                podcastTitle = pod.title,
                                episodeTitle = newest.title,
                                episodeId = newest.id
                            )
                        )
                    }
                }
            }
            dao.updatePodcast(pod.copy(lastSyncedAt = System.currentTimeMillis()))
        }
        return addedCount to alerts
    }

    suspend fun enforceAutoDownloadRules(
        autoDownloadWifi: Boolean,
        autoDownloadUpNext: Boolean,
        queueDepth: Int
    ) {
        val manager = downloadManager ?: return
        if (autoDownloadWifi && !manager.isUnmeteredWifiConnected()) return

        val targets = linkedMapOf<String, EpisodeEntity>()
        if (autoDownloadUpNext) {
            dao.getUpNextEpisodes().first()
                .filter { !it.isDownloaded && !it.isPlayed }
                .take(queueDepth.coerceAtLeast(1))
                .forEach { targets[it.id] = it }
        }
        val activeEps = dao.getActiveEpisodes().first()
        val autoPlaylists = dao.getCustomPlaylists().first().filter { it.autoDownload }
        for (playlist in autoPlaylists) {
            val epIds = playlist.episodeIdList().toSet()
            val podIds = playlist.assignedPodcastIdList().toSet()
            activeEps.filter { !it.isDownloaded && !it.isPlayed && (it.id in epIds || it.podcastId in podIds) }
                .take(queueDepth.coerceAtLeast(1))
                .forEach { targets[it.id] = it }
        }

        targets.values.forEach { ep ->
            manager.downloadEpisodeAudio(ep).onSuccess { file ->
                dao.updateEpisodeDownloaded(ep.id, downloaded = true, localFilePath = file.absolutePath)
            }
        }
    }

    suspend fun markEpisodePlayed(episodeId: String, played: Boolean) {
        dao.markEpisodePlayed(episodeId, played, if (played) System.currentTimeMillis() else 0L)
        recordEpisodeStatusMutation(episodeId)
    }

    suspend fun markEpisodesPlayed(episodes: List<EpisodeEntity>, played: Boolean) {
        val ts = if (played) System.currentTimeMillis() else 0L
        episodes.forEach { ep ->
            dao.markEpisodePlayed(ep.id, played, ts)
        }
        recordEpisodesStatusMutation(episodes)
    }

    suspend fun setEpisodesStarred(episodes: List<EpisodeEntity>, starred: Boolean) {
        episodes.forEach { ep ->
            dao.updateEpisodeStarred(ep.id, starred)
        }
        recordEpisodesStatusMutation(episodes)
    }

    suspend fun archiveEpisodes(episodes: List<EpisodeEntity>) {
        episodes.forEach { ep ->
            dao.updateEpisodeArchived(ep.id)
        }
        recordEpisodesStatusMutation(episodes)
        val queued = episodes.filter { it.inUpNext }
        if (queued.isNotEmpty()) {
            recordUpNextRemoved(queued)
        }
    }

    suspend fun downloadEpisodes(episodes: List<EpisodeEntity>): Pair<Int, Int> {
        var success = 0
        var failed = 0
        val manager = downloadManager
        episodes.filter { !it.isDownloaded }.forEach { ep ->
            if (manager == null) {
                dao.updateEpisodeDownloaded(ep.id, downloaded = true, localFilePath = ep.localFilePath)
                success++
            } else {
                val res = manager.downloadEpisodeAudio(ep)
                res.onSuccess { file ->
                    dao.updateEpisodeDownloaded(ep.id, downloaded = true, localFilePath = file.absolutePath)
                    success++
                }.onFailure {
                    failed++
                }
            }
        }
        return success to failed
    }

    suspend fun removeDownloadsForEpisodes(episodes: List<EpisodeEntity>): Int {
        var removed = 0
        episodes.filter { it.isDownloaded }.forEach { ep ->
            downloadManager?.deleteEpisodeFile(ep)
            dao.updateEpisodeDownloaded(ep.id, downloaded = false, localFilePath = "")
            removed++
        }
        return removed
    }

    suspend fun resetEpisodeProgress(episode: EpisodeEntity) {
        dao.resetEpisodeProgress(episode.id)
        recordEpisodeStatusMutation(episode.id)
    }

    /**
     * Persists playback progress and automatically marks the episode as played once it reaches
     * at least 98% completion. Returns true if this update newly marked the episode as played.
     */
    suspend fun updatePlaybackPosition(episodeId: String, positionMs: Long, durationMs: Long): Boolean {
        if (positionMs <= 0L) return false
        val existing = dao.getEpisodeById(episodeId)
        val effectiveDurationMs = if (durationMs > 0L) durationMs else (existing?.durationMs ?: 0L)
        if (existing != null && !existing.isPlayed && isEpisodeNearCompletion(positionMs, effectiveDurationMs)) {
            dao.markEpisodePlayed(episodeId, true, System.currentTimeMillis())
            recordEpisodeStatusMutation(episodeId)
            return true
        }
        if (existing?.isPlayed == true) return false
        dao.updatePlaybackPosition(episodeId, positionMs, durationMs)
        return false
    }

    suspend fun markAllEpisodesPlayedForPodcasts(podcasts: List<PodcastEntity>) {
        if (podcasts.isEmpty()) return
        val targetPodcastIds = podcasts.map { it.id }.toSet()
        val now = System.currentTimeMillis()
        val affected = dao.getAllEpisodes().first()
            .filter { it.podcastId in targetPodcastIds && !it.isPlayed }
        affected.forEach { ep -> dao.markEpisodePlayed(ep.id, true, now) }
        recordEpisodesStatusMutation(affected)
    }

    suspend fun updatePodcastsAutoQueue(podcasts: List<PodcastEntity>, mode: AutoQueueMode) {
        podcasts.forEach { pod ->
            val updated = pod.copy(autoQueueMode = mode.name)
            dao.updatePodcast(updated)
            recordPodcastMutation(updated)
        }
    }

    suspend fun unsubscribePodcasts(podcasts: List<PodcastEntity>) {
        podcasts.forEach { pod -> unsubscribePodcast(pod.id) }
    }

    suspend fun setPlaylistsPinned(playlists: List<PlaylistEntity>, pinned: Boolean) {
        playlists.forEach { pl ->
            if (pl.isPinned != pinned) {
                dao.updatePlaylist(pl.copy(isPinned = pinned))
                recordPlaylistMutation(pl.id)
            }
        }
    }

    suspend fun setPlaylistsAutoDownload(playlists: List<PlaylistEntity>, enabled: Boolean) {
        playlists.forEach { pl ->
            if (pl.autoDownload != enabled) {
                dao.updatePlaylist(pl.copy(autoDownload = enabled))
                recordPlaylistMutation(pl.id)
            }
        }
    }

    suspend fun deleteCustomPlaylists(playlists: List<PlaylistEntity>): Int {
        var deleted = 0
        playlists.filter { it.smartRuleType.isEmpty() }.forEach { pl ->
            dao.deletePlaylist(pl.id)
            recordPlaylistDeleted(pl.id)
            deleted++
        }
        return deleted
    }

    suspend fun createCustomPlaylist(name: String, folderName: String, initialEpisodeIds: List<String>) {
        if (name.isBlank()) return
        val playlist = PlaylistEntity(
            id = "pl_${UUID.randomUUID()}",
            name = name.trim(),
            folderName = folderName.trim().ifBlank { "General" },
            episodeIdsCsv = initialEpisodeIds.joinToString(",")
        )
        dao.insertPlaylist(playlist)
        recordPlaylistMutation(playlist.id)
    }

    suspend fun updatePlaylist(playlist: PlaylistEntity) {
        dao.updatePlaylist(playlist)
        recordPlaylistMutation(playlist.id)
    }

    suspend fun toggleEpisodeInPlaylist(playlist: PlaylistEntity, episodeId: String) {
        val current = playlist.episodeIdList().toMutableList()
        if (current.contains(episodeId)) {
            current.remove(episodeId)
        } else {
            current.add(episodeId)
        }
        dao.insertPlaylist(playlist.copy(episodeIdsCsv = current.joinToString(",")))
        recordPlaylistMutation(playlist.id)
    }

    suspend fun deleteCustomPlaylist(playlistId: String) {
        dao.deletePlaylist(playlistId)
        recordPlaylistDeleted(playlistId)
    }

    suspend fun archiveAllPlayed() {
        val candidates = dao.getActiveEpisodes().first().filter { it.isPlayed && !it.isStarred }
        dao.archiveAllPlayedNonStarred()
        if (candidates.isNotEmpty()) {
            recordEpisodesStatusMutation(candidates)
        }
    }

    suspend fun clearAllDownloads() {
        downloadManager?.clearAllCache()
        dao.clearAllDownloads()
    }

    suspend fun clearDownloadsForPodcast(podcastId: String) {
        val manager = downloadManager
        val showEps = dao.getAllEpisodes().first().filter { it.podcastId == podcastId && it.isDownloaded }
        showEps.forEach { ep ->
            manager?.deleteEpisodeFile(ep)
            dao.updateEpisodeDownloaded(ep.id, downloaded = false, localFilePath = "")
        }
    }

    suspend fun cleanupPlayedAndOversizeDownloads(autoDeletePolicy: String, maxCacheMb: Int) {
        val manager = downloadManager ?: return
        val now = System.currentTimeMillis()
        val downloaded = dao.getAllEpisodes().first().filter { it.isDownloaded }.toMutableList()

        // 1) Auto-delete played non-starred downloads based on retention window
        if (autoDeletePolicy != "NEVER") {
            val cutoffMs = when (autoDeletePolicy) {
                "IMMEDIATELY" -> 0L
                "AFTER_7D" -> 7L * 24 * 3_600_000L
                else -> 24L * 3_600_000L
            }
            val playedCandidates = downloaded.filter { ep ->
                if (!ep.isPlayed || ep.isStarred) return@filter false
                val f = ep.localFilePath.takeIf { it.isNotBlank() }?.let(::File) ?: manager.fileForEpisode(ep.id)
                val ageMs = if (f.exists()) (now - f.lastModified()).coerceAtLeast(0L) else 0L
                cutoffMs == 0L || ageMs >= cutoffMs
            }
            playedCandidates.forEach { ep ->
                manager.deleteEpisodeFile(ep)
                dao.updateEpisodeDownloaded(ep.id, downloaded = false, localFilePath = "")
                downloaded.remove(ep)
            }
        }

        // 2) Enforce max cache cap in MB (evict oldest unstarred/unplayed-last downloads)
        if (maxCacheMb > 0) {
            val limitBytes = maxCacheMb.toLong() * 1024L * 1024L
            var currentBytes = downloaded.sumOf { manager.getEpisodeFileSizeBytes(it) }
            if (currentBytes > limitBytes) {
                val evictOrder = downloaded
                    .filter { !it.isStarred }
                    .sortedWith(compareBy<EpisodeEntity> { it.inUpNext }.thenBy { it.publishedAt })
                for (ep in evictOrder) {
                    if (currentBytes <= limitBytes) break
                    val sz = manager.getEpisodeFileSizeBytes(ep)
                    manager.deleteEpisodeFile(ep)
                    dao.updateEpisodeDownloaded(ep.id, downloaded = false, localFilePath = "")
                    currentBytes -= sz
                }
            }
        }
    }

    suspend fun unsubscribePodcast(podcastId: String) {
        val existingPod = dao.getPodcastById(podcastId)
        val manager = downloadManager
        if (manager != null) {
            dao.getAllEpisodes().first()
                .filter { it.podcastId == podcastId && it.isDownloaded }
                .forEach { manager.deleteEpisodeFile(it) }
        }
        dao.deleteEpisodesByPodcastId(podcastId)
        dao.deletePodcastById(podcastId)
        if (existingPod != null) {
            recordPodcastMutation(existingPod, unsubscribed = true)
        }
    }

    private suspend fun recordPodcastMutation(podcast: PodcastEntity, unsubscribed: Boolean = false) {
        val prefs = preferencesRepository ?: return
        val now = System.currentTimeMillis()
        val key = DriveSyncEngine.normalizeFeedKey(podcast.feedUrl, podcast.id, podcast.title)
        prefs.updateDriveSyncJournal { journal ->
            if (unsubscribed) {
                journal.copy(
                    unsubscribedPodcasts = DriveSyncLocalJournal.trimMap(journal.unsubscribedPodcasts + (key to now))
                )
            } else {
                val updatedUnsub = journal.unsubscribedPodcasts - key
                journal.copy(
                    unsubscribedPodcasts = updatedUnsub,
                    podcastUpdatedAt = DriveSyncLocalJournal.trimMap(journal.podcastUpdatedAt + (key to now))
                )
            }
        }
    }

    private suspend fun recordEpisodeStatusMutation(episodeId: String) {
        val prefs = preferencesRepository ?: return
        val ep = dao.getEpisodeById(episodeId) ?: return
        val pod = dao.getPodcastById(ep.podcastId)
        val key = DriveSyncEngine.episodeCanonicalKey(ep, pod?.feedUrl.orEmpty())
        val now = System.currentTimeMillis()
        prefs.updateDriveSyncJournal { journal ->
            journal.copy(
                episodeStatusUpdatedAt = DriveSyncLocalJournal.trimMap(journal.episodeStatusUpdatedAt + (key to now))
            )
        }
    }

    private suspend fun recordEpisodesStatusMutation(episodes: List<EpisodeEntity>) {
        val prefs = preferencesRepository ?: return
        if (episodes.isEmpty()) return
        val podsById = dao.getAllPodcasts().first().associateBy { it.id }
        val now = System.currentTimeMillis()
        prefs.updateDriveSyncJournal { journal ->
            val updated = journal.episodeStatusUpdatedAt.toMutableMap()
            episodes.forEach { ep ->
                val key = DriveSyncEngine.episodeCanonicalKey(ep, podsById[ep.podcastId]?.feedUrl.orEmpty())
                updated[key] = now
            }
            journal.copy(episodeStatusUpdatedAt = DriveSyncLocalJournal.trimMap(updated))
        }
    }

    private suspend fun recordUpNextAdded(episodes: List<EpisodeEntity>) {
        val prefs = preferencesRepository ?: return
        val podsById = dao.getAllPodcasts().first().associateBy { it.id }
        val now = System.currentTimeMillis()
        prefs.updateDriveSyncJournal { journal ->
            val added = journal.addedToUpNext.toMutableMap()
            val removed = journal.removedFromUpNext.toMutableMap()
            episodes.forEach { ep ->
                val key = DriveSyncEngine.episodeCanonicalKey(ep, podsById[ep.podcastId]?.feedUrl.orEmpty())
                added[key] = now
                removed.remove(key)
            }
            journal.copy(
                upNextUpdatedAt = now,
                addedToUpNext = DriveSyncLocalJournal.trimMap(added),
                removedFromUpNext = removed
            )
        }
    }

    private suspend fun recordUpNextRemoved(episodes: List<EpisodeEntity>) {
        val prefs = preferencesRepository ?: return
        val podsById = dao.getAllPodcasts().first().associateBy { it.id }
        val now = System.currentTimeMillis()
        prefs.updateDriveSyncJournal { journal ->
            val removed = journal.removedFromUpNext.toMutableMap()
            episodes.forEach { ep ->
                val key = DriveSyncEngine.episodeCanonicalKey(ep, podsById[ep.podcastId]?.feedUrl.orEmpty())
                removed[key] = now
            }
            journal.copy(
                upNextUpdatedAt = now,
                removedFromUpNext = DriveSyncLocalJournal.trimMap(removed)
            )
        }
    }

    private suspend fun recordUpNextOrderMutation() {
        val prefs = preferencesRepository ?: return
        val now = System.currentTimeMillis()
        prefs.updateDriveSyncJournal { journal ->
            journal.copy(upNextUpdatedAt = now)
        }
    }

    private suspend fun recordPlaylistMutation(playlistId: String) {
        val prefs = preferencesRepository ?: return
        val now = System.currentTimeMillis()
        prefs.updateDriveSyncJournal { journal ->
            journal.copy(
                deletedPlaylists = journal.deletedPlaylists - playlistId,
                playlistUpdatedAt = DriveSyncLocalJournal.trimMap(journal.playlistUpdatedAt + (playlistId to now))
            )
        }
    }

    private suspend fun recordPlaylistDeleted(playlistId: String) {
        val prefs = preferencesRepository ?: return
        val now = System.currentTimeMillis()
        prefs.updateDriveSyncJournal { journal ->
            journal.copy(
                deletedPlaylists = DriveSyncLocalJournal.trimMap(journal.deletedPlaylists + (playlistId to now))
            )
        }
    }
}
