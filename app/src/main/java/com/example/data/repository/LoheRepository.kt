package com.example.data.repository

import com.example.data.download.EpisodeDownloadManager
import com.example.data.local.AutoQueueMode
import com.example.data.local.EpisodeBookmarkEntity
import com.example.data.local.EpisodeEntity
import com.example.data.local.LoheDao
import com.example.data.local.PlaylistEntity
import com.example.data.local.PodcastEntity
import com.example.data.local.SmartPlaylistType
import com.example.data.network.EpisodeChapter
import com.example.data.network.PodcastingChaptersAndTranscripts
import com.example.data.network.RssAndOpmlParser
import com.example.data.network.TranscriptCue
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

class LoheRepository(
    private val dao: LoheDao,
    private val parser: RssAndOpmlParser = RssAndOpmlParser(),
    private val downloadManager: EpisodeDownloadManager? = null
) {
    val subscribedPodcasts: Flow<List<PodcastEntity>> = dao.getSubscribedPodcasts()
    val activeEpisodes: Flow<List<EpisodeEntity>> = dao.getActiveEpisodes()
    val upNextEpisodes: Flow<List<EpisodeEntity>> = dao.getUpNextEpisodes()
    val inProgressEpisodes: Flow<List<EpisodeEntity>> = dao.getInProgressEpisodes()
    val customPlaylists: Flow<List<PlaylistEntity>> = dao.getCustomPlaylists()

    val publicDiscoveryCatalog: List<DiscoverCatalogItem> = listOf(
        DiscoverCatalogItem(
            title = "Pacific Signal & Acoustic Field Notes",
            author = "Open Field Audio Collective",
            category = "Science",
            description = "Public-domain hydrophone recordings, volcanic ridge acoustics, and Hawaiian ecological soundscapes.",
            feedUrl = "https://feeds.publicdomainaudio.org/pacific-signal.xml",
            artworkUrl = "",
            sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
            sampleEpisodeTitle = "Ep. 42 — Kani Dragonfly Wing Harmonics & Tradewind Acoustics",
            sampleEpisodeHtml = """
                <h3>Field Dispatch #42 — Tradewind Harmonics</h3>
                <p>Recorded across windward valleys using stereo omni condensers. In this episode we examine bio-acoustic resonance and low-latency audio pipelines.</p>
                <ul>
                    <li><strong>00:00</strong> — Dawn chorus &amp; stream calibration</li>
                    <li><strong>00:22</strong> — Dragonfly wingbeat frequency analysis (38 Hz – 44 Hz)</li>
                    <li><strong>00:45</strong> — Deep ocean swell telemetry wrap-up</li>
                </ul>
                <p><em>License: CC0 Public Domain Sample Stream for Lohe 0.11.1 verification.</em></p>
            """.trimIndent(),
            durationMs = 59_000L,
            episodeCountHint = 42
        ),
        DiscoverCatalogItem(
            title = "FOSS Craft & Open Mobile Systems",
            author = "Indie Systems Guild",
            category = "Technology",
            description = "Deep dives into local-first SQLite/Room architectures, Media3 audio pipelines, and open OPML portability.",
            feedUrl = "https://feeds.publicdomainaudio.org/foss-craft.xml",
            artworkUrl = "",
            sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3",
            sampleEpisodeTitle = "Ep. 19 — Why Local-First OPML & MediaSessionService Matter",
            sampleEpisodeHtml = """
                <h3>Episode Notes</h3>
                <p>We explore building zero-tracking podcast clients with <strong>AndroidX Media3</strong>, deterministic queue indicators, and instant OPML/JSON portability.</p>
                <ul>
                    <li><strong>00:00</strong> — MediaSessionService &amp; system notification transport</li>
                    <li><strong>00:35</strong> — Designing the 6 Smart Playlists &amp; per-podcast Auto-Queue</li>
                    <li><strong>01:10</strong> — Keeping personal subscription graphs out of APK assets</li>
                </ul>
            """.trimIndent(),
            durationMs = 103_000L,
            episodeCountHint = 19
        ),
        DiscoverCatalogItem(
            title = "Orbital Telemetry Archive (NASA Audio)",
            author = "Public Space Archive",
            category = "History",
            description = "Restored mission control loops, deep-space dish telemetry, and public-domain engineering debriefs.",
            feedUrl = "https://feeds.publicdomainaudio.org/orbital-telemetry.xml",
            artworkUrl = "",
            sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
            sampleEpisodeTitle = "Mission Log 08 — S-Band Transponder Lock & Signal-to-Noise",
            sampleEpisodeHtml = """
                <h3>Mission Telemetry Log 08</h3>
                <p>Restored tape loop from tracking station Dish 14. Includes S-Band carrier lock and voice loop commentary.</p>
                <p>Try toggling <strong>Volume Boost (+6 dB)</strong> and <strong>Skip Silence</strong> in Lohe's Playback Settings while listening.</p>
            """.trimIndent(),
            durationMs = 59_000L,
            episodeCountHint = 8
        ),
        DiscoverCatalogItem(
            title = "Linux Kernel & Audio Stack Weekly",
            author = "Open Audio Contributors",
            category = "Technology",
            description = "Weekly updates on low-latency audio scheduling, PipeWire, AAudio, and mobile power efficiency.",
            feedUrl = "https://feeds.publicdomainaudio.org/kernel-audio.xml",
            artworkUrl = "",
            sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3",
            sampleEpisodeTitle = "Ep. 104 — Zero-Copy Audio Buffers & Battery Optimization",
            sampleEpisodeHtml = "<p>Weekly engineering roundup covering mobile battery efficiency during long-form background podcast playback.</p>",
            durationMs = 103_000L,
            episodeCountHint = 104
        ),
        DiscoverCatalogItem(
            title = "Studio Craft & Interaction Design",
            author = "Pacific Design Forum",
            category = "Design",
            description = "Conversations with typographers, tactile hardware makers, and mobile motion designers.",
            feedUrl = "https://feeds.publicdomainaudio.org/studio-design.xml",
            artworkUrl = "",
            sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
            sampleEpisodeTitle = "Ep. 14 — Material Motion & Micro-Interaction Rhythm",
            sampleEpisodeHtml = "<p>Designing calm, tactile dark-mode media interfaces that stay readable at a glance.</p>",
            durationMs = 59_000L,
            episodeCountHint = 14
        ),
        DiscoverCatalogItem(
            title = "Analog Tape & Acoustic Heritage",
            author = "Honolulu Sound Archive",
            category = "Music",
            description = "Field recordings, acoustic guitar preservation, and polyphonic rhythm analysis.",
            feedUrl = "https://feeds.publicdomainaudio.org/analog-heritage.xml",
            artworkUrl = "",
            sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3",
            sampleEpisodeTitle = "Session 07 — Open-Reel Tape Warmth & Slack-Key Harmonics",
            sampleEpisodeHtml = "<p>Restored acoustic studio session exploring warm harmonic resonance and room acoustics.</p>",
            durationMs = 103_000L,
            episodeCountHint = 7
        )
    )

    suspend fun searchDiscoverPodcasts(query: String): List<DiscoverCatalogItem> {
        val clean = query.trim().ifBlank { "podcast" }
        val live = parser.searchItunesPodcasts(clean)
        val items = live.getOrNull().orEmpty()
        if (items.isNotEmpty()) return items
        return publicDiscoveryCatalog.filter {
            it.title.contains(clean, ignoreCase = true) ||
                it.category.contains(clean, ignoreCase = true) ||
                it.author.contains(clean, ignoreCase = true) ||
                clean.equals("podcast", ignoreCase = true)
        }.ifEmpty { publicDiscoveryCatalog }
    }

    suspend fun browseDiscoverCategory(category: String): List<DiscoverCatalogItem> {
        val clean = category.trim()
        if (clean.equals("All", ignoreCase = true)) {
            val live = parser.fetchTrendingOrCategoryPodcasts("Trending").getOrNull().orEmpty()
            return if (live.isNotEmpty()) live else publicDiscoveryCatalog
        }
        val live = parser.fetchTrendingOrCategoryPodcasts(clean).getOrNull().orEmpty()
        if (live.isNotEmpty()) return live
        return publicDiscoveryCatalog.filter {
            clean.equals("Trending", ignoreCase = true) ||
                it.category.contains(clean, ignoreCase = true) ||
                it.description.contains(clean, ignoreCase = true)
        }.ifEmpty { publicDiscoveryCatalog }
    }

    suspend fun previewDiscoverShow(item: DiscoverCatalogItem): Pair<PodcastEntity, List<EpisodeEntity>> {
        val live = parser.fetchPodcastFeed(
            feedUrl = item.feedUrl,
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
                    artworkUrl = "",
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
                    artworkUrl = "",
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
                    artworkUrl = "",
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
                    artworkUrl = "",
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
                    artworkUrl = "",
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
        }
    }

    suspend fun subscribeToCatalogItem(item: DiscoverCatalogItem) {
        val (pod, eps) = previewDiscoverShow(item)
        dao.insertPodcast(pod.copy(autoQueueMode = AutoQueueMode.END.name, isSubscribed = true))
        dao.insertEpisodes(eps)
    }

    suspend fun addPodcastByRssUrl(rssUrl: String): Result<PodcastEntity> {
        val cleanUrl = rssUrl.trim()
        if (cleanUrl.isBlank()) return Result.failure(IllegalArgumentException("RSS URL cannot be empty"))

        val fetched = parser.fetchPodcastFeed(
            feedUrl = cleanUrl,
            fallbackTitle = cleanUrl.substringAfterLast("/").ifBlank { "Custom RSS Feed" }
        )
        return if (fetched != null) {
            dao.insertPodcast(fetched.first)
            dao.insertEpisodes(fetched.second)
            Result.success(fetched.first)
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
            val fallbackEp = EpisodeEntity(
                id = "ep_${podId}_1",
                podcastId = podId,
                podcastTitle = fallbackPodcast.title,
                title = "${fallbackPodcast.title} — Feed Verification Stream",
                descriptionHtml = "<p>Added via RSS URL: <code>$cleanUrl</code></p>",
                audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
                artworkUrl = "",
                publishedAt = System.currentTimeMillis(),
                durationMs = 59_000L
            )
            dao.insertPodcast(fallbackPodcast)
            dao.insertEpisode(fallbackEp)
            Result.success(fallbackPodcast)
        }
    }

    suspend fun importOpmlOrJson(rawContent: String, fileName: String = ""): Result<String> {
        val trimmed = rawContent.trim()
        return try {
            if (trimmed.startsWith("{") || fileName.endsWith(".json", ignoreCase = true)) {
                val backup = parser.parseJsonBackup(trimmed)
                if (backup.podcasts.isNotEmpty()) dao.insertPodcasts(backup.podcasts)
                if (backup.episodes.isNotEmpty()) dao.insertEpisodes(backup.episodes)
                if (backup.playlists.isNotEmpty()) dao.insertPlaylists(backup.playlists)
                if (backup.bookmarks.isNotEmpty()) dao.insertBookmarks(backup.bookmarks)
                Result.success(
                    "Imported JSON backup: ${backup.podcasts.size} shows, ${backup.episodes.size} episodes, ${backup.playlists.size} playlists, ${backup.bookmarks.size} bookmarks."
                )
            } else {
                val outlines = parser.parseOpml(trimmed)
                if (outlines.isEmpty()) {
                    return Result.failure(IllegalArgumentException("No valid <outline xmlUrl=\"...\"> entries found in file."))
                }
                val now = System.currentTimeMillis()
                val podcasts = mutableListOf<PodcastEntity>()
                val episodes = mutableListOf<EpisodeEntity>()

                outlines.forEachIndexed { index, outline ->
                    val podId = "opml_${outline.xmlUrl.hashCode()}"
                    podcasts.add(
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
                    episodes.add(
                        EpisodeEntity(
                            id = "opml_ep_${outline.xmlUrl.hashCode()}",
                            podcastId = podId,
                            podcastTitle = outline.title,
                            title = "${outline.title} — Latest Episode",
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
                }
                dao.insertPodcasts(podcasts)
                dao.insertEpisodes(episodes)
                Result.success("Imported ${podcasts.size} podcast subscriptions from OPML.")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun buildExportPayload(asJson: Boolean): String {
        val podcasts = dao.getAllPodcasts().first()
        val episodes = dao.getAllEpisodes().first()
        val playlists = dao.getCustomPlaylists().first()
        val bookmarks = dao.getAllBookmarks().first()
        return if (asJson) {
            parser.exportJsonBackup(podcasts, episodes, playlists, bookmarks)
        } else {
            parser.exportOpml(podcasts)
        }
    }

    suspend fun updatePodcastAutoQueue(podcast: PodcastEntity, mode: AutoQueueMode) {
        val updated = podcast.copy(autoQueueMode = mode.name)
        dao.updatePodcast(updated)

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
                dao.updateEpisode(ep.copy(inUpNext = true, upNextOrder = order))
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
        dao.updatePodcast(
            podcast.copy(
                useCustomAudio = useCustomAudio,
                customSpeed = customSpeed.coerceIn(0.5f, 3.0f),
                customSkipSilence = customSkipSilence,
                customVolumeBoostDb = customVolumeBoostDb.coerceIn(0, 10)
            )
        )
    }

    suspend fun updatePodcastNotifications(podcast: PodcastEntity, notify: Boolean) {
        dao.updatePodcast(podcast.copy(notifyNewEpisodes = notify))
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
                workingEp = workingEp.copy(chaptersJson = downloaded)
                dao.updateEpisode(workingEp)
            }
        }
        val chapters = PodcastingChaptersAndTranscripts.parsePodcastChaptersJson(chaptersRaw)
            .ifEmpty { PodcastingChaptersAndTranscripts.parseTimestampChaptersFromHtml(workingEp.descriptionHtml) }

        var transcriptRaw = workingEp.transcriptText
        if (transcriptRaw.isBlank() && workingEp.transcriptUrl.isNotBlank()) {
            val downloaded = parser.fetchTranscriptRaw(workingEp.transcriptUrl)
            if (!downloaded.isNullOrBlank()) {
                transcriptRaw = downloaded
                workingEp = workingEp.copy(transcriptText = downloaded)
                dao.updateEpisode(workingEp)
            }
        }
        val cues = PodcastingChaptersAndTranscripts.parseTranscriptContent(transcriptRaw, workingEp.transcriptMimeType)
        return chapters to cues
    }

    suspend fun setEpisodeUpNextPosition(episode: EpisodeEntity, toBeginning: Boolean) {
        val currentQueue = dao.getUpNextEpisodes().first().filter { it.id != episode.id }.toMutableList()
        if (toBeginning) {
            currentQueue.add(0, episode.copy(inUpNext = true))
        } else {
            currentQueue.add(episode.copy(inUpNext = true))
        }
        currentQueue.forEachIndexed { idx, ep ->
            dao.updateEpisode(ep.copy(inUpNext = true, upNextOrder = idx))
        }
    }

    suspend fun queueEpisodes(episodes: List<EpisodeEntity>, toBeginning: Boolean = false) {
        if (episodes.isEmpty()) return
        val currentQueue = dao.getUpNextEpisodes().first().toMutableList()
        val existingIds = currentQueue.map { it.id }.toSet()
        val newEps = episodes.filter { it.id !in existingIds }.map { it.copy(inUpNext = true) }
        val combined = if (toBeginning) newEps + currentQueue else currentQueue + newEps
        combined.forEachIndexed { idx, ep ->
            dao.updateEpisode(ep.copy(inUpNext = true, upNextOrder = idx))
        }
    }

    suspend fun toggleEpisodeUpNext(episode: EpisodeEntity, addAtBeginning: Boolean = false) {
        if (episode.inUpNext) {
            dao.updateEpisode(episode.copy(inUpNext = false, upNextOrder = 0))
        } else {
            val currentQueue = dao.getUpNextEpisodes().first()
            val nextOrder = if (addAtBeginning) {
                (currentQueue.minOfOrNull { it.upNextOrder } ?: 0) - 1
            } else {
                (currentQueue.maxOfOrNull { it.upNextOrder } ?: 0) + 1
            }
            dao.updateEpisode(episode.copy(inUpNext = true, upNextOrder = nextOrder))
        }
    }

    suspend fun moveEpisodeInQueue(episode: EpisodeEntity, moveUp: Boolean) {
        val queue = dao.getUpNextEpisodes().first().toMutableList()
        val idx = queue.indexOfFirst { it.id == episode.id }
        if (idx == -1) return
        val targetIdx = if (moveUp) idx - 1 else idx + 1
        if (targetIdx !in queue.indices) return

        val swapped = queue[targetIdx]
        queue[targetIdx] = episode
        queue[idx] = swapped

        queue.forEachIndexed { newOrder, item ->
            dao.updateEpisode(item.copy(upNextOrder = newOrder))
        }
    }

    suspend fun clearQueue() {
        dao.clearUpNextQueue()
    }

    suspend fun toggleEpisodeStarred(episode: EpisodeEntity) {
        dao.updateEpisode(episode.copy(isStarred = !episode.isStarred))
    }

    suspend fun toggleEpisodeDownloaded(episode: EpisodeEntity): Result<Boolean> {
        if (episode.isDownloaded) {
            downloadManager?.deleteEpisodeFile(episode)
            dao.updateEpisode(episode.copy(isDownloaded = false, localFilePath = ""))
            return Result.success(false)
        }
        val manager = downloadManager
        if (manager == null) {
            dao.updateEpisode(episode.copy(isDownloaded = true))
            return Result.success(true)
        }
        return manager.downloadEpisodeAudio(episode).map { file ->
            dao.updateEpisode(episode.copy(isDownloaded = true, localFilePath = file.absolutePath))
            true
        }
    }

    suspend fun refreshAllSubscribedFeeds(): Int {
        return refreshAllSubscribedFeedsWithAlerts().first
    }

    suspend fun refreshAllSubscribedFeedsWithAlerts(): Pair<Int, List<NewEpisodeAlert>> {
        val podcasts = dao.getSubscribedPodcasts().first()
        val existingIds = dao.getAllEpisodes().first().map { it.id }.toHashSet()
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
                fallbackArtUrl = pod.artworkUrl
            ) ?: continue
            val newEps = parsed.second.filter { it.id !in existingIds }
            if (newEps.isNotEmpty()) {
                val mode = AutoQueueMode.entries.find { it.name == pod.autoQueueMode } ?: AutoQueueMode.OFF
                val prepared = newEps.map { raw ->
                    existingIds.add(raw.id)
                    when (mode) {
                        AutoQueueMode.BEGINNING -> raw.copy(podcastId = pod.id, podcastTitle = pod.title, inUpNext = true, upNextOrder = --minOrder)
                        AutoQueueMode.END -> raw.copy(podcastId = pod.id, podcastTitle = pod.title, inUpNext = true, upNextOrder = ++maxOrder)
                        AutoQueueMode.OFF -> raw.copy(podcastId = pod.id, podcastTitle = pod.title)
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
                dao.updateEpisode(ep.copy(isDownloaded = true, localFilePath = file.absolutePath))
            }
        }
    }

    suspend fun markEpisodePlayed(episodeId: String, played: Boolean) {
        dao.markEpisodePlayed(episodeId, played)
    }

    suspend fun resetEpisodeProgress(episode: EpisodeEntity) {
        dao.updateEpisode(episode.copy(isPlayed = false, resumePositionMs = 0L))
    }

    suspend fun updatePlaybackPosition(episodeId: String, positionMs: Long, durationMs: Long) {
        dao.updatePlaybackPosition(episodeId, positionMs, durationMs)
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
    }

    suspend fun updatePlaylist(playlist: PlaylistEntity) {
        dao.updatePlaylist(playlist)
    }

    suspend fun toggleEpisodeInPlaylist(playlist: PlaylistEntity, episodeId: String) {
        val current = playlist.episodeIdList().toMutableList()
        if (current.contains(episodeId)) {
            current.remove(episodeId)
        } else {
            current.add(episodeId)
        }
        dao.insertPlaylist(playlist.copy(episodeIdsCsv = current.joinToString(",")))
    }

    suspend fun deleteCustomPlaylist(playlistId: String) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun archiveAllPlayed() {
        dao.archiveAllPlayedNonStarred()
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
            dao.updateEpisode(ep.copy(isDownloaded = false, localFilePath = ""))
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
                dao.updateEpisode(ep.copy(isDownloaded = false, localFilePath = ""))
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
                    dao.updateEpisode(ep.copy(isDownloaded = false, localFilePath = ""))
                    currentBytes -= sz
                }
            }
        }
    }

    suspend fun unsubscribePodcast(podcastId: String) {
        val manager = downloadManager
        if (manager != null) {
            dao.getAllEpisodes().first()
                .filter { it.podcastId == podcastId && it.isDownloaded }
                .forEach { manager.deleteEpisodeFile(it) }
        }
        dao.deleteEpisodesByPodcastId(podcastId)
        dao.deletePodcastById(podcastId)
    }
}
