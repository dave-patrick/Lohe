package com.example.data.network

import android.util.Xml
import com.example.data.local.AutoQueueMode
import com.example.data.local.EpisodeBookmarkEntity
import com.example.data.local.EpisodeEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PodcastEntity
import com.example.data.repository.DiscoverCatalogItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class OpmlOutlineItem(
    val title: String,
    val xmlUrl: String,
    val htmlUrl: String = "",
    val category: String = "Podcast"
)

data class ParsedLibraryBackup(
    val podcasts: List<PodcastEntity>,
    val episodes: List<EpisodeEntity>,
    val playlists: List<PlaylistEntity>,
    val bookmarks: List<EpisodeBookmarkEntity> = emptyList()
)

class RssAndOpmlParser(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    /**
     * Queries Apple's public iTunes Podcast Search API directly from the device.
     */
    suspend fun searchItunesPodcasts(term: String): Result<List<DiscoverCatalogItem>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(term.trim(), "UTF-8")
            val url = "https://itunes.apple.com/search?media=podcast&limit=24&term=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Lohe/0.12.0 (Android; +https://lohe.app)")
                .build()
            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(IllegalStateException("HTTP ${response.code}"))
                response.body?.string() ?: return@withContext Result.failure(IllegalStateException("Empty body"))
            }
            val root = JSONObject(body)
            val arr = root.optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<DiscoverCatalogItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val feedUrl = obj.optString("feedUrl", "").trim()
                if (feedUrl.isBlank()) continue
                val title = obj.optString("collectionName", obj.optString("trackName", "Podcast"))
                val author = obj.optString("artistName", "Independent Publisher")
                val artwork = obj.optString("artworkUrl600", obj.optString("artworkUrl100", ""))
                val genre = obj.optString("primaryGenreName", "Podcasts")
                val trackCount = obj.optInt("trackCount", 0)
                list.add(
                    DiscoverCatalogItem(
                        title = title,
                        author = author,
                        category = genre,
                        description = "$genre • ${if (trackCount > 0) "$trackCount episodes" else "Public podcast feed"}",
                        feedUrl = feedUrl,
                        artworkUrl = artwork,
                        sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
                        sampleEpisodeTitle = "$title — Latest Episode",
                        sampleEpisodeHtml = "<p>Open show preview to fetch live RSS episodes from <code>$feedUrl</code>.</p>",
                        durationMs = 59_000L,
                        episodeCountHint = trackCount
                    )
                )
            }
            Result.success(deduplicateDiscoverCatalog(list))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches live Apple Podcasts top charts or category highlights (public, zero-auth).
     */
    suspend fun fetchTrendingOrCategoryPodcasts(category: String): Result<List<DiscoverCatalogItem>> =
        withContext(Dispatchers.IO) {
            val clean = category.trim()
            if (clean.equals("Trending", ignoreCase = true) || clean.equals("All", ignoreCase = true) || clean.isBlank()) {
                val topRes = runCatching {
                    val req = Request.Builder()
                        .url("https://rss.marketingtools.apple.com/api/v2/us/podcasts/top/20/podcasts.json")
                        .header("User-Agent", "Lohe/0.12.0 (Android; +https://lohe.app)")
                        .build()
                    val body = httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        resp.body?.string() ?: error("Empty body")
                    }
                    val results = JSONObject(body)
                        .optJSONObject("feed")
                        ?.optJSONArray("results")
                        ?: JSONArray()
                    val chartEntries = mutableListOf<Triple<String, String, String>>() // id, title, fallbackArt
                    val chartMeta = mutableMapOf<String, Triple<String, String, String>>() // id -> (author, genre, chartUrl)
                    for (i in 0 until results.length()) {
                        val obj = results.optJSONObject(i) ?: continue
                        val id = obj.optString("id", "").trim()
                        val title = obj.optString("name", "").trim()
                        val author = obj.optString("artistName", "Independent Publisher").trim()
                        val art = obj.optString("artworkUrl100", "").trim()
                            .replace("100x100bb", "600x600bb")
                        val genre = obj.optJSONArray("genres")?.optJSONObject(0)?.optString("name", "Trending") ?: "Trending"
                        val chartUrl = obj.optString("url", "").trim()
                        if (title.isBlank()) continue
                        chartEntries.add(Triple(id, title, art))
                        chartMeta[id] = Triple(author, genre, chartUrl)
                    }

                    // Batch-hydrate real RSS feed URLs and high-res 600x600 artwork via iTunes Lookup
                    val lookupById = mutableMapOf<String, JSONObject>()
                    val ids = chartEntries.map { it.first }.filter { it.isNotBlank() }
                    if (ids.isNotEmpty()) {
                        runCatching {
                            val lookupReq = Request.Builder()
                                .url("https://itunes.apple.com/lookup?id=${ids.joinToString(",")}")
                                .header("User-Agent", "Lohe/0.12.0 (Android; +https://lohe.app)")
                                .build()
                            val lookupBody = httpClient.newCall(lookupReq).execute().use { resp ->
                                if (resp.isSuccessful) resp.body?.string() else null
                            }
                            if (!lookupBody.isNullOrBlank()) {
                                val arr = JSONObject(lookupBody).optJSONArray("results") ?: JSONArray()
                                for (j in 0 until arr.length()) {
                                    val o = arr.optJSONObject(j) ?: continue
                                    val cid = o.optString("collectionId", o.optString("trackId", ""))
                                    if (cid.isNotBlank()) lookupById[cid] = o
                                }
                            }
                        }
                    }

                    deduplicateDiscoverCatalog(
                        buildList {
                            chartEntries.forEachIndexed { idx, (id, title, fallbackArt) ->
                                val (author, genre, chartUrl) = chartMeta[id] ?: Triple("Podcast", "Trending", "")
                                val lookedUp = lookupById[id]
                                val resolvedFeed = lookedUp?.optString("feedUrl", "")?.takeIf { it.isNotBlank() } ?: chartUrl
                                val resolvedArt = lookedUp?.optString("artworkUrl600", "")?.takeIf { it.isNotBlank() }
                                    ?: fallbackArt
                                val resolvedGenre = lookedUp?.optString("primaryGenreName", "")?.takeIf { it.isNotBlank() }
                                    ?: genre
                                val epCount = lookedUp?.optInt("trackCount", 0) ?: 0
                                add(
                                    DiscoverCatalogItem(
                                        title = title,
                                        author = lookedUp?.optString("artistName", author)?.ifBlank { author } ?: author,
                                        category = resolvedGenre,
                                        description = "Top Chart #${idx + 1} • $resolvedGenre${if (epCount > 0) " • $epCount episodes" else ""}",
                                        feedUrl = resolvedFeed,
                                        artworkUrl = resolvedArt,
                                        sampleAudioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
                                        sampleEpisodeTitle = "$title — Latest Episode",
                                        sampleEpisodeHtml = "<p>Discovered from live Apple Top Charts. Open preview or subscribe to load full RSS episodes.</p>",
                                        durationMs = 1_800_000L,
                                        episodeCountHint = epCount
                                    )
                                )
                            }
                        }
                    )
                }
                if (topRes.isSuccess && topRes.getOrNull()?.isNotEmpty() == true) {
                    return@withContext topRes
                }
            }
            searchItunesPodcasts(if (clean.isBlank() || clean.equals("Trending", true)) "top podcasts" else clean)
        }

    fun deduplicateDiscoverCatalog(items: List<DiscoverCatalogItem>): List<DiscoverCatalogItem> {
        val seenFeeds = HashSet<String>(items.size)
        val seenMeta = HashSet<String>(items.size)
        return items.filter { item ->
            val feedKey = item.feedUrl.trim().lowercase(Locale.ROOT)
            val titleKey = item.title.trim().lowercase(Locale.ROOT)
            val authorKey = item.author.trim().lowercase(Locale.ROOT)
            val metaKey = if (titleKey.isNotBlank()) "$titleKey|$authorKey" else ""

            val duplicateFeed = feedKey.isNotBlank() && !seenFeeds.add(feedKey)
            val duplicateMeta = metaKey.isNotBlank() && !seenMeta.add(metaKey)
            !duplicateFeed && !duplicateMeta
        }
    }

    fun parseOpml(xmlContent: String): List<OpmlOutlineItem> {
        val items = mutableListOf<OpmlOutlineItem>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlContent.trim()))

            var currentFolder = "Subscribed"
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG &&
                    parser.name.equals("outline", ignoreCase = true)
                ) {
                    val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                        ?: parser.getAttributeValue(null, "xmlurl")
                        ?: parser.getAttributeValue(null, "url")
                    val title = parser.getAttributeValue(null, "title")
                        ?: parser.getAttributeValue(null, "text")
                        ?: "Untitled Feed"
                    val htmlUrl = parser.getAttributeValue(null, "htmlUrl") ?: ""

                    if (!xmlUrl.isNullOrBlank()) {
                        items.add(
                            OpmlOutlineItem(
                                title = title.trim(),
                                xmlUrl = xmlUrl.trim(),
                                htmlUrl = htmlUrl.trim(),
                                category = currentFolder
                            )
                        )
                    } else if (title.isNotBlank()) {
                        currentFolder = title.trim()
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            val regex = Regex("""<outline[^>]*xmlUrl=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
            val titleRegex = Regex("""(?:title|text)=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            regex.findAll(xmlContent).forEach { match ->
                val tag = match.value
                val url = match.groupValues[1]
                val title = titleRegex.find(tag)?.groupValues?.getOrNull(1) ?: url
                items.add(OpmlOutlineItem(title = title, xmlUrl = url))
            }
        }
        return items.distinctBy { it.xmlUrl }
    }

    fun exportOpml(podcasts: List<PodcastEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<opml version="2.0">""")
        sb.appendLine("""  <head>""")
        sb.appendLine("""    <title>Lohe Podcast Subscriptions (v0.12.0)</title>""")
        sb.appendLine("""  </head>""")
        sb.appendLine("""  <body>""")
        podcasts.forEach { podcast ->
            val safeTitle = escapeXml(podcast.title)
            val safeUrl = escapeXml(podcast.feedUrl)
            sb.appendLine("""    <outline type="rss" text="$safeTitle" title="$safeTitle" xmlUrl="$safeUrl" />""")
        }
        sb.appendLine("""  </body>""")
        sb.appendLine("""</opml>""")
        return sb.toString()
    }

    fun parseJsonBackup(jsonText: String): ParsedLibraryBackup {
        val podcasts = mutableListOf<PodcastEntity>()
        val episodes = mutableListOf<EpisodeEntity>()
        val playlists = mutableListOf<PlaylistEntity>()

        val root = JSONObject(jsonText.trim())
        val podcastArray = root.optJSONArray("podcasts")
            ?: root.optJSONArray("subscriptions")
            ?: root.optJSONArray("shows")
            ?: JSONArray()

        for (i in 0 until podcastArray.length()) {
            val obj = podcastArray.optJSONObject(i) ?: continue
            val feedUrl = obj.optString("feedUrl", obj.optString("xmlUrl", obj.optString("url", "")))
            val id = obj.optString("id", if (feedUrl.isNotBlank()) "pod_${feedUrl.hashCode()}" else UUID.randomUUID().toString())
            val title = obj.optString("title", obj.optString("name", "Imported Podcast"))
            podcasts.add(
                PodcastEntity(
                    id = id,
                    title = title,
                    author = obj.optString("author", obj.optString("publisher", "Independent Creator")),
                    description = obj.optString("description", "Imported from Lohe JSON backup"),
                    feedUrl = feedUrl.ifBlank { "https://example.org/feed/$id.xml" },
                    artworkUrl = obj.optString("artworkUrl", obj.optString("image", "")),
                    category = obj.optString("category", "Subscribed"),
                    autoQueueMode = obj.optString("autoQueueMode", AutoQueueMode.OFF.name),
                    useCustomAudio = obj.optBoolean("useCustomAudio", false),
                    customSpeed = obj.optDouble("customSpeed", 1.0).toFloat(),
                    customSkipSilence = obj.optBoolean("customSkipSilence", false),
                    customVolumeBoostDb = obj.optInt("customVolumeBoostDb", 0),
                    notifyNewEpisodes = obj.optBoolean("notifyNewEpisodes", true),
                    isSubscribed = obj.optBoolean("isSubscribed", true)
                )
            )
        }

        val episodeArray = root.optJSONArray("episodes") ?: JSONArray()
        for (i in 0 until episodeArray.length()) {
            val obj = episodeArray.optJSONObject(i) ?: continue
            val id = obj.optString("id", UUID.randomUUID().toString())
            val podcastId = obj.optString("podcastId", podcasts.firstOrNull()?.id ?: "pod_imported")
            episodes.add(
                EpisodeEntity(
                    id = id,
                    podcastId = podcastId,
                    podcastTitle = obj.optString("podcastTitle", "Imported Show"),
                    title = obj.optString("title", "Imported Episode"),
                    descriptionHtml = obj.optString("descriptionHtml", obj.optString("description", "<p>Imported episode notes.</p>")),
                    audioUrl = obj.optString("audioUrl", obj.optString("enclosureUrl", "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3")),
                    artworkUrl = obj.optString("artworkUrl", ""),
                    publishedAt = obj.optLong("publishedAt", System.currentTimeMillis() - i * 86_400_000L),
                    durationMs = obj.optLong("durationMs", 1_200_000L),
                    resumePositionMs = obj.optLong("resumePositionMs", obj.optLong("positionMs", 0L)),
                    isPlayed = obj.optBoolean("isPlayed", false),
                    isArchived = obj.optBoolean("isArchived", false),
                    isDownloaded = obj.optBoolean("isDownloaded", false),
                    isStarred = obj.optBoolean("isStarred", false),
                    inUpNext = obj.optBoolean("inUpNext", false),
                    upNextOrder = obj.optInt("upNextOrder", i),
                    chaptersUrl = obj.optString("chaptersUrl", ""),
                    chaptersJson = obj.optString("chaptersJson", ""),
                    transcriptUrl = obj.optString("transcriptUrl", ""),
                    transcriptMimeType = obj.optString("transcriptMimeType", ""),
                    transcriptText = obj.optString("transcriptText", ""),
                    guid = obj.optString("guid", obj.optString("itemIdentifier", "")),
                    playedAt = obj.optLong("playedAt", obj.optLong("playedDate", 0L))
                )
            )
        }

        val playlistArray = root.optJSONArray("playlists") ?: root.optJSONArray("customPlaylists") ?: JSONArray()
        for (i in 0 until playlistArray.length()) {
            val obj = playlistArray.optJSONObject(i) ?: continue
            playlists.add(
                PlaylistEntity(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", obj.optString("title", "Imported Playlist")),
                    folderName = obj.optString("folderName", obj.optString("folder", "General")),
                    episodeIdsCsv = obj.optString("episodeIdsCsv", ""),
                    assignedPodcastIdsCsv = obj.optString("assignedPodcastIdsCsv", ""),
                    smartRuleType = obj.optString("smartRuleType", ""),
                    isPinned = obj.optBoolean("isPinned", false),
                    autoDownload = obj.optBoolean("autoDownload", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }

        val bookmarks = mutableListOf<EpisodeBookmarkEntity>()
        val bookmarkArray = root.optJSONArray("bookmarks") ?: root.optJSONArray("episodeBookmarks") ?: JSONArray()
        for (i in 0 until bookmarkArray.length()) {
            val obj = bookmarkArray.optJSONObject(i) ?: continue
            val episodeId = obj.optString("episodeId", "").trim()
            if (episodeId.isBlank()) continue
            bookmarks.add(
                EpisodeBookmarkEntity(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    episodeId = episodeId,
                    positionMs = obj.optLong("positionMs", 0L).coerceAtLeast(0L),
                    label = obj.optString("label", "Bookmark"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }

        return ParsedLibraryBackup(podcasts, episodes, playlists, bookmarks)
    }

    fun exportJsonBackup(
        podcasts: List<PodcastEntity>,
        episodes: List<EpisodeEntity>,
        playlists: List<PlaylistEntity>,
        bookmarks: List<EpisodeBookmarkEntity> = emptyList()
    ): String {
        val root = JSONObject()
        root.put("app", "Lohe Android Preview")
        root.put("packageId", "com.lohe.app.preview")
            root.put("version", "0.12.0")
            root.put("build", "2026.12.17.120")
            root.put("exportedAt", System.currentTimeMillis())

            val podArr = JSONArray()
            podcasts.forEach { p ->
                podArr.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("title", p.title)
                        put("author", p.author)
                        put("description", p.description)
                        put("feedUrl", p.feedUrl)
                        put("artworkUrl", p.artworkUrl)
                        put("category", p.category)
                        put("autoQueueMode", p.autoQueueMode)
                        put("useCustomAudio", p.useCustomAudio)
                        put("customSpeed", p.customSpeed.toDouble())
                        put("customSkipSilence", p.customSkipSilence)
                        put("customVolumeBoostDb", p.customVolumeBoostDb)
                        put("notifyNewEpisodes", p.notifyNewEpisodes)
                        put("isSubscribed", p.isSubscribed)
                    }
                )
            }
        root.put("podcasts", podArr)

        val epArr = JSONArray()
        episodes.forEach { e ->
            epArr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("podcastId", e.podcastId)
                    put("podcastTitle", e.podcastTitle)
                    put("title", e.title)
                    put("descriptionHtml", e.descriptionHtml)
                    put("audioUrl", e.audioUrl)
                    put("artworkUrl", e.artworkUrl)
                    put("publishedAt", e.publishedAt)
                    put("durationMs", e.durationMs)
                    put("resumePositionMs", e.resumePositionMs)
                    put("isPlayed", e.isPlayed)
                    put("isArchived", e.isArchived)
                    put("isDownloaded", e.isDownloaded)
                    put("isStarred", e.isStarred)
                    put("inUpNext", e.inUpNext)
                    put("upNextOrder", e.upNextOrder)
                    put("chaptersUrl", e.chaptersUrl)
                    put("chaptersJson", e.chaptersJson)
                    put("transcriptUrl", e.transcriptUrl)
                    put("transcriptMimeType", e.transcriptMimeType)
                    put("transcriptText", e.transcriptText)
                    put("guid", e.guid)
                    put("playedAt", e.playedAt)
                }
            )
        }
        root.put("episodes", epArr)

        val plArr = JSONArray()
        playlists.forEach { pl ->
            plArr.put(
                JSONObject().apply {
                    put("id", pl.id)
                    put("name", pl.name)
                    put("folderName", pl.folderName)
                    put("episodeIdsCsv", pl.episodeIdsCsv)
                    put("assignedPodcastIdsCsv", pl.assignedPodcastIdsCsv)
                    put("smartRuleType", pl.smartRuleType)
                    put("isPinned", pl.isPinned)
                    put("autoDownload", pl.autoDownload)
                    put("createdAt", pl.createdAt)
                }
            )
        }
        root.put("playlists", plArr)

        val bmArr = JSONArray()
        bookmarks.forEach { bm ->
            bmArr.put(
                JSONObject().apply {
                    put("id", bm.id)
                    put("episodeId", bm.episodeId)
                    put("positionMs", bm.positionMs)
                    put("label", bm.label)
                    put("createdAt", bm.createdAt)
                }
            )
        }
        root.put("bookmarks", bmArr)

        return root.toString(2)
    }

    suspend fun fetchPodcastFeed(
        feedUrl: String,
        fallbackTitle: String = "Podcast Feed",
        category: String = "Podcasts",
        fallbackArtUrl: String = "",
        maxEpisodes: Int = 0 // 0 or negative = sync all episodes in feed
    ): Pair<PodcastEntity, List<EpisodeEntity>>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(feedUrl)
                .header("User-Agent", "Lohe/0.12.0 (Android; +https://lohe.app)")
                .build()
            val xml = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string() ?: return@withContext null
            }
            parseRssFeedXml(xml, feedUrl, fallbackTitle, category, fallbackArtUrl, maxEpisodes)
        } catch (_: Exception) {
            null
        }
    }

    fun parseRssFeedXml(
        xmlContent: String,
        feedUrl: String,
        fallbackTitle: String = "Subscribed Show",
        category: String = "Podcasts",
        fallbackArtUrl: String = "",
        maxEpisodes: Int = 0 // 0 or negative = parse all episodes in feed
    ): Pair<PodcastEntity, List<EpisodeEntity>> {
        val podcastId = "pod_${feedUrl.trim().lowercase().hashCode()}"
        var channelTitle = fallbackTitle
        var channelAuthor = "Podcast Publisher"
        var channelDesc = "Subscribed via RSS"
        var channelArt = fallbackArtUrl

        val episodes = mutableListOf<EpisodeEntity>()

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlContent.trim()))

            var inItem = false
            var itemTitle = ""
            var itemDesc = ""
            var itemAudioUrl = ""
            var itemArtUrl = ""
            var itemPubDate = System.currentTimeMillis()
            var itemDurationMs = 1_500_000L
            var itemGuid = ""
            var itemChaptersUrl = ""
            var itemTranscriptUrl = ""
            var itemTranscriptMime = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.lowercase(Locale.ROOT)
                        if (name == "item" || name == "entry") {
                            inItem = true
                            itemTitle = ""
                            itemDesc = ""
                            itemAudioUrl = ""
                            itemArtUrl = ""
                            itemPubDate = System.currentTimeMillis() - (episodes.size * 86_400_000L)
                            itemDurationMs = 1_500_000L
                            itemGuid = ""
                            itemChaptersUrl = ""
                            itemTranscriptUrl = ""
                            itemTranscriptMime = ""
                        } else if (!inItem) {
                            when (name) {
                                "title" -> {
                                    val t = parser.nextText().trim()
                                    if (t.isNotBlank()) channelTitle = t
                                }
                                "description", "itunes:summary", "subtitle" -> {
                                    val d = parser.nextText().trim()
                                    if (d.isNotBlank()) channelDesc = d
                                }
                                "itunes:author", "author", "managingeditor" -> {
                                    val a = parser.nextText().trim()
                                    if (a.isNotBlank()) channelAuthor = a
                                }
                                "itunes:image" -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    if (!href.isNullOrBlank()) channelArt = href
                                }
                            }
                        } else {
                            when (name) {
                                "title" -> itemTitle = parser.nextText().trim()
                                "description", "content:encoded", "itunes:summary", "summary" -> {
                                    val text = parser.nextText().trim()
                                    if (text.length > itemDesc.length) itemDesc = text
                                }
                                "enclosure" -> {
                                    val url = parser.getAttributeValue(null, "url")
                                    if (!url.isNullOrBlank()) itemAudioUrl = url.trim()
                                }
                                "itunes:image" -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    if (!href.isNullOrBlank()) itemArtUrl = href.trim()
                                }
                                "itunes:duration" -> {
                                    itemDurationMs = parseDurationToMs(parser.nextText().trim())
                                }
                                "pubdate", "published" -> {
                                    itemPubDate = parseRfc822Date(parser.nextText().trim())
                                }
                                "guid", "id" -> {
                                    itemGuid = parser.nextText().trim()
                                }
                                "podcast:chapters" -> {
                                    val url = parser.getAttributeValue(null, "url")
                                    if (!url.isNullOrBlank()) itemChaptersUrl = url.trim()
                                }
                                "podcast:transcript" -> {
                                    val url = parser.getAttributeValue(null, "url")
                                    val mime = parser.getAttributeValue(null, "type") ?: ""
                                    // Prefer VTT, JSON, or SRT over HTML when multiple transcript tags exist
                                    if (!url.isNullOrBlank() && (itemTranscriptUrl.isBlank() || mime.contains("vtt") || mime.contains("json") || mime.contains("srt"))) {
                                        itemTranscriptUrl = url.trim()
                                        itemTranscriptMime = mime.trim()
                                    }
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val name = parser.name.lowercase(Locale.ROOT)
                        if ((name == "item" || name == "entry") && inItem) {
                            inItem = false
                            val withinLimit = maxEpisodes <= 0 || episodes.size < maxEpisodes
                            if (itemTitle.isNotBlank() && withinLimit) {
                                val epId = "ep_${(itemGuid.ifBlank { "$feedUrl#$itemTitle" }).hashCode()}"
                                episodes.add(
                                    EpisodeEntity(
                                        id = epId,
                                        podcastId = podcastId,
                                        podcastTitle = channelTitle,
                                        title = itemTitle,
                                        descriptionHtml = itemDesc.ifBlank { "<p>Episode from $channelTitle.</p>" },
                                        audioUrl = itemAudioUrl.ifBlank {
                                            "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"
                                        },
                                        artworkUrl = itemArtUrl.ifBlank { channelArt },
                                        publishedAt = itemPubDate,
                                        durationMs = itemDurationMs,
                                        chaptersUrl = itemChaptersUrl,
                                        transcriptUrl = itemTranscriptUrl,
                                        transcriptMimeType = itemTranscriptMime,
                                        guid = itemGuid
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Keep partial parse
        }

        val podcast = PodcastEntity(
            id = podcastId,
            title = channelTitle,
            author = channelAuthor,
            description = channelDesc,
            feedUrl = feedUrl,
            artworkUrl = channelArt,
            category = category,
            autoQueueMode = AutoQueueMode.OFF.name,
            customSpeed = 1.0f,
            isSubscribed = true
        )

        if (episodes.isEmpty()) {
            episodes.add(
                EpisodeEntity(
                    id = "ep_${podcastId}_latest",
                    podcastId = podcastId,
                    podcastTitle = channelTitle,
                    title = "$channelTitle — Latest Signal Dispatch",
                    descriptionHtml = "<p>Subscribed via OPML / RSS feed: <code>$feedUrl</code>.</p><p>Tap Play to stream audio via Media3 ExoPlayer.</p>",
                    audioUrl = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
                    artworkUrl = channelArt,
                    publishedAt = System.currentTimeMillis() - 3_600_000L,
                    durationMs = 59_000L
                )
            )
        }

        return podcast to episodes
    }

    suspend fun fetchChaptersJson(chaptersUrl: String): String? =
        PodcastingChaptersAndTranscripts.fetchTextUrl(httpClient, chaptersUrl)

    suspend fun fetchTranscriptRaw(transcriptUrl: String): String? =
        PodcastingChaptersAndTranscripts.fetchTextUrl(httpClient, transcriptUrl)

    private fun parseDurationToMs(raw: String): Long {
        if (raw.isBlank()) return 1_200_000L
        val parts = raw.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            1 -> parts[0] * 1000L
            else -> 1_200_000L
        }.coerceAtLeast(30_000L)
    }

    private fun parseRfc822Date(raw: String): Long {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val parsed = sdf.parse(raw)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }

    private fun escapeXml(input: String): String =
        input
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
