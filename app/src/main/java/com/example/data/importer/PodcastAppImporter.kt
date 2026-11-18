package com.example.data.importer

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.EpisodeEntity
import com.example.data.local.LoheDatabase
import com.example.data.local.PlaylistEntity
import com.example.data.local.PodcastEntity
import com.example.data.network.RssAndOpmlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.abs

enum class ImportSourceFormat(val label: String) {
    OPML("OPML Subscriptions"),
    ANTENNAPOD_DB("AntennaPod Database (.db)"),
    PODCAST_ADDICT_BACKUP("Podcast Addict Backup (.backup)"),
    LOHE_JSON("Lohe JSON Backup (.json)")
}

data class ImportProgressState(
    val isImporting: Boolean = false,
    val stageMessage: String = "",
    val processedItems: Int = 0,
    val totalItems: Int = 0,
    val progressFraction: Float = 0f
)

data class ImportResultSummary(
    val format: ImportSourceFormat,
    val showsAdded: Int,
    val episodesUpdated: Int,
    val itemsSkipped: Int
) {
    fun toDisplayBanner(): String {
        return "${format.label}: $showsAdded shows added, $episodesUpdated episodes updated, $itemsSkipped items skipped"
    }
}

private data class ParsedImportPayload(
    val format: ImportSourceFormat,
    val podcasts: List<PodcastEntity>,
    val episodes: List<EpisodeEntity>,
    val playlists: List<PlaylistEntity> = emptyList()
)

class PodcastAppImporter(
    private val context: Context,
    private val database: LoheDatabase,
    private val parser: RssAndOpmlParser = RssAndOpmlParser()
) {
    private val dao = database.loheDao()

    companion object {
        private val SQLITE_MAGIC = "SQLite format 3".toByteArray(Charsets.US_ASCII)

        /**
         * Positions within the last 10 seconds or past 95% of duration count as played.
         */
        fun isPositionConsideredPlayed(positionMs: Long, durationMs: Long): Boolean {
            if (durationMs <= 0L || positionMs <= 0L) return false
            val remainingMs = durationMs - positionMs
            val ratio = positionMs.toDouble() / durationMs.toDouble()
            return remainingMs <= 10_000L || ratio >= 0.95
        }

        fun normalizeUrl(raw: String): String {
            return raw.trim()
                .substringBefore('#')
                .trimEnd('/')
                .lowercase(Locale.ROOT)
        }

        fun normalizeTitle(raw: String): String {
            return raw.trim()
                .lowercase(Locale.ROOT)
                .replace(Regex("\\s+"), " ")
        }
    }

    suspend fun importFromUri(
        uri: Uri,
        onProgress: (ImportProgressState) -> Unit = {}
    ): Result<ImportResultSummary> = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("lohe_saf_import_", ".tmp", context.cacheDir)
        try {
            onProgress(
                ImportProgressState(
                    isImporting = true,
                    stageMessage = "Reading selected file...",
                    progressFraction = 0.05f
                )
            )
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalArgumentException("Unable to open selected file"))
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.length() == 0L) {
                return@withContext Result.failure(IllegalArgumentException("Selected file is empty"))
            }
            val nameHint = uri.lastPathSegment ?: tempFile.name
            importFromFile(tempFile, nameHint, onProgress)
        } catch (e: Exception) {
            onProgress(ImportProgressState(isImporting = false))
            Result.failure(e)
        } finally {
            tempFile.delete()
        }
    }

    suspend fun importFromFile(
        file: File,
        displayNameHint: String = file.name,
        onProgress: (ImportProgressState) -> Unit = {}
    ): Result<ImportResultSummary> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() == 0L) {
                return@withContext Result.failure(IllegalArgumentException("Import file is empty or missing"))
            }

            onProgress(
                ImportProgressState(
                    isImporting = true,
                    stageMessage = "Detecting backup format...",
                    progressFraction = 0.1f
                )
            )

            val format = detectFormat(file, displayNameHint)
            onProgress(
                ImportProgressState(
                    isImporting = true,
                    stageMessage = "Parsing ${format.label}...",
                    progressFraction = 0.25f
                )
            )

            val payload = when (format) {
                ImportSourceFormat.OPML -> parseOpmlFile(file)
                ImportSourceFormat.LOHE_JSON -> parseLoheJsonFile(file)
                ImportSourceFormat.ANTENNAPOD_DB -> parseAntennaPodSqliteFile(file)
                ImportSourceFormat.PODCAST_ADDICT_BACKUP -> parsePodcastAddictBackupFile(file)
            }

            val summary = applyImportInSingleTransaction(payload, onProgress)
            onProgress(
                ImportProgressState(
                    isImporting = false,
                    stageMessage = "Import complete",
                    processedItems = payload.podcasts.size + payload.episodes.size,
                    totalItems = payload.podcasts.size + payload.episodes.size,
                    progressFraction = 1.0f
                )
            )
            Result.success(summary)
        } catch (e: Exception) {
            onProgress(ImportProgressState(isImporting = false))
            Result.failure(e)
        }
    }

    private fun detectFormat(file: File, displayNameHint: String): ImportSourceFormat {
        val header = ByteArray(64)
        val readCount = FileInputStream(file).use { it.read(header) }
        if (readCount >= 4) {
            // ZIP magic bytes: PK\x03\x04
            val isZip = header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
            if (isZip) {
                return ImportSourceFormat.PODCAST_ADDICT_BACKUP
            }
        }
        if (readCount >= SQLITE_MAGIC.size) {
            val matchesSqlite = SQLITE_MAGIC.indices.all { i -> header[i] == SQLITE_MAGIC[i] }
            if (matchesSqlite) {
                return ImportSourceFormat.ANTENNAPOD_DB
            }
        }

        val textSample = file.bufferedReader().use { reader ->
            val buf = CharArray(2048)
            val n = reader.read(buf)
            if (n > 0) String(buf, 0, n).trimStart() else ""
        }

        val lowerHint = displayNameHint.lowercase(Locale.ROOT)
        return when {
            textSample.startsWith("{") || lowerHint.endsWith(".json") -> ImportSourceFormat.LOHE_JSON
            textSample.contains("<opml", ignoreCase = true) ||
                textSample.contains("<outline", ignoreCase = true) ||
                textSample.startsWith("<") ||
                lowerHint.endsWith(".opml") ||
                lowerHint.endsWith(".xml") -> ImportSourceFormat.OPML
            lowerHint.endsWith(".backup") || lowerHint.endsWith(".zip") -> ImportSourceFormat.PODCAST_ADDICT_BACKUP
            lowerHint.endsWith(".db") || lowerHint.endsWith(".sqlite") -> ImportSourceFormat.ANTENNAPOD_DB
            else -> throw IllegalArgumentException("Unsupported backup file format")
        }
    }

    private fun parseOpmlFile(file: File): ParsedImportPayload {
        val text = file.readText()
        val outlines = parser.parseOpml(text)
        if (outlines.isEmpty()) {
            throw IllegalArgumentException("No valid OPML podcast outlines found")
        }
        val podcasts = outlines.map { outline ->
            PodcastEntity(
                id = "opml_${outline.xmlUrl.hashCode()}",
                title = outline.title,
                author = "Imported via OPML",
                description = "Imported via OPML (${outline.xmlUrl})",
                feedUrl = outline.xmlUrl,
                artworkUrl = "drawable://lohe_logo_square",
                category = "Imported",
                isSubscribed = true
            )
        }
        return ParsedImportPayload(
            format = ImportSourceFormat.OPML,
            podcasts = podcasts,
            episodes = emptyList()
        )
    }

    private fun parseLoheJsonFile(file: File): ParsedImportPayload {
        val text = file.readText()
        val backup = parser.parseJsonBackup(text)
            ?: throw IllegalArgumentException("Invalid Lohe JSON backup format")
        return ParsedImportPayload(
            format = ImportSourceFormat.LOHE_JSON,
            podcasts = backup.podcasts,
            episodes = backup.episodes,
            playlists = backup.playlists
        )
    }

    private fun parsePodcastAddictBackupFile(zipFile: File): ParsedImportPayload {
        val extractedDbFile = File.createTempFile("lohe_pa_extracted_", ".db", context.cacheDir)
        try {
            var foundSqlite = false
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val candidateFile = File.createTempFile("lohe_pa_entry_", ".tmp", context.cacheDir)
                        try {
                            FileOutputStream(candidateFile).use { out ->
                                zis.copyTo(out)
                            }
                            if (isSqliteFile(candidateFile) ||
                                entry.name.endsWith(".db", ignoreCase = true) ||
                                entry.name.endsWith(".sqlite", ignoreCase = true)
                            ) {
                                if (isSqliteFile(candidateFile)) {
                                    candidateFile.copyTo(extractedDbFile, overwrite = true)
                                    foundSqlite = true
                                    break
                                }
                            }
                        } finally {
                            candidateFile.delete()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            if (!foundSqlite || !extractedDbFile.exists() || extractedDbFile.length() == 0L) {
                throw IllegalArgumentException("Podcast Addict .backup archive did not contain a valid SQLite database")
            }
            return parseSqliteDatabase(
                dbFile = extractedDbFile,
                format = ImportSourceFormat.PODCAST_ADDICT_BACKUP
            )
        } finally {
            extractedDbFile.delete()
        }
    }

    private fun parseAntennaPodSqliteFile(dbFile: File): ParsedImportPayload {
        if (!isSqliteFile(dbFile)) {
            throw IllegalArgumentException("File is not a valid AntennaPod SQLite database")
        }
        return parseSqliteDatabase(
            dbFile = dbFile,
            format = ImportSourceFormat.ANTENNAPOD_DB
        )
    }

    private fun isSqliteFile(file: File): Boolean {
        if (!file.exists() || file.length() < SQLITE_MAGIC.size) return false
        val header = ByteArray(SQLITE_MAGIC.size)
        val read = FileInputStream(file).use { it.read(header) }
        return read == SQLITE_MAGIC.size && SQLITE_MAGIC.indices.all { i -> header[i] == SQLITE_MAGIC[i] }
    }

    private fun parseSqliteDatabase(
        dbFile: File,
        format: ImportSourceFormat
    ): ParsedImportPayload {
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        try {
            val tableNames = getTableNames(db)
            val hasAntennaPodTables = tableNames.any { it.equals("Feeds", ignoreCase = true) } ||
                tableNames.any { it.equals("FeedItems", ignoreCase = true) }
            val hasPodcastAddictTables = tableNames.any { it.equals("podcasts", ignoreCase = true) } ||
                tableNames.any { it.equals("episodes", ignoreCase = true) }

            return when {
                format == ImportSourceFormat.ANTENNAPOD_DB && hasAntennaPodTables ->
                    readAntennaPodTables(db, format)
                format == ImportSourceFormat.PODCAST_ADDICT_BACKUP && hasPodcastAddictTables ->
                    readPodcastAddictTables(db, format)
                hasAntennaPodTables ->
                    readAntennaPodTables(db, format)
                hasPodcastAddictTables ->
                    readPodcastAddictTables(db, format)
                else ->
                    throw IllegalArgumentException("Unrecognized podcast database schema in ${format.label}")
            }
        } finally {
            db.close()
        }
    }

    private fun getTableNames(db: SQLiteDatabase): Set<String> {
        val result = mutableSetOf<String>()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) {
                    result.add(name)
                }
            }
        }
        return result
    }

    private data class AntennaPodMediaRow(
        val feedItemId: Long,
        val durationMs: Long,
        val positionMs: Long,
        val downloadUrl: String,
        val playedAtMs: Long
    )

    private fun readAntennaPodTables(
        db: SQLiteDatabase,
        format: ImportSourceFormat
    ): ParsedImportPayload {
        val tables = getTableNames(db)
        val feedsTable = tables.firstOrNull { it.equals("Feeds", ignoreCase = true) }
        val feedItemsTable = tables.firstOrNull { it.equals("FeedItems", ignoreCase = true) }
        val feedMediaTable = tables.firstOrNull { it.equals("FeedMedia", ignoreCase = true) }

        val feedIdToPodcast = mutableMapOf<Long, PodcastEntity>()
        val podcasts = mutableListOf<PodcastEntity>()

        if (feedsTable != null) {
            db.rawQuery("SELECT * FROM $feedsTable", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val rawId = cursor.getLongOrNull("id", "_id") ?: (podcasts.size + 1).toLong()
                    val customTitle = cursor.getStringOrBlank("custom_title")
                    val rawTitle = cursor.getStringOrBlank("title", "name")
                    val title = customTitle.ifBlank { rawTitle }.ifBlank { "AntennaPod Show #$rawId" }
                    val rssUrl = cursor.getStringOrBlank("download_url", "feed_url", "url", "rss_url")
                    val author = cursor.getStringOrBlank("author").ifBlank { "AntennaPod Import" }
                    val description = cursor.getStringOrBlank("description").ifBlank { "Imported from AntennaPod" }
                    val imageUrl = cursor.getStringOrBlank("image_url", "artwork_url")
                    val podcastId = if (rssUrl.isNotBlank()) {
                        "apod_pod_${normalizeUrl(rssUrl).hashCode()}"
                    } else {
                        "apod_pod_$rawId"
                    }
                    val entity = PodcastEntity(
                        id = podcastId,
                        title = title,
                        author = author,
                        description = description,
                        feedUrl = rssUrl,
                        artworkUrl = imageUrl.ifBlank { "drawable://lohe_logo_square" },
                        category = "Imported",
                        isSubscribed = true
                    )
                    feedIdToPodcast[rawId] = entity
                    podcasts.add(entity)
                }
            }
        }

        // Map FeedMedia by feeditem ID if FeedMedia table exists
        val mediaByFeedItemId = mutableMapOf<Long, AntennaPodMediaRow>()
        if (feedMediaTable != null) {
            db.rawQuery("SELECT * FROM $feedMediaTable", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val feedItemId = cursor.getLongOrNull("feeditem", "feed_item", "item_id", "id") ?: continue
                    val durationMs = cursor.getLongOrDefault(0L, "duration", "duration_ms")
                    val positionMs = cursor.getLongOrDefault(0L, "position", "position_ms", "resume_position")
                    val downloadUrl = cursor.getStringOrBlank("download_url", "enclosure_url", "audio_url", "file_url")
                    val completionDate = cursor.getLongOrDefault(0L, "playback_completion_date", "played_at", "played_date")
                    val lastPlayedTime = cursor.getLongOrDefault(0L, "last_played_time")
                    val playedAtMs = maxOf(completionDate, lastPlayedTime)
                    mediaByFeedItemId[feedItemId] = AntennaPodMediaRow(
                        feedItemId = feedItemId,
                        durationMs = durationMs.coerceAtLeast(0L),
                        positionMs = positionMs.coerceAtLeast(0L),
                        downloadUrl = downloadUrl,
                        playedAtMs = playedAtMs.coerceAtLeast(0L)
                    )
                }
            }
        }

        val episodes = mutableListOf<EpisodeEntity>()
        if (feedItemsTable != null) {
            db.rawQuery("SELECT * FROM $feedItemsTable", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val itemId = cursor.getLongOrNull("id", "_id") ?: (episodes.size + 1).toLong()
                    val feedId = cursor.getLongOrNull("feed", "feed_id", "podcast_id") ?: -1L
                    val parentPodcast = feedIdToPodcast[feedId]
                    val podcastId = parentPodcast?.id ?: "apod_pod_$feedId"
                    val podcastTitle = parentPodcast?.title ?: "Imported Show"

                    val title = cursor.getStringOrBlank("title", "name")
                    val guid = cursor.getStringOrBlank("item_identifier", "guid", "uid")
                    val pubDate = cursor.getLongOrDefault(0L, "PubDate", "pubDate", "pub_date", "published_at")
                    val readState = cursor.getIntOrDefault(0, "read", "played", "is_played")
                    val description = cursor.getStringOrBlank("description", "content_encoded")

                    val media = mediaByFeedItemId[itemId]
                    val audioUrl = (media?.downloadUrl ?: "").ifBlank {
                        cursor.getStringOrBlank("download_url", "enclosure_url", "audio_url", "media_url")
                    }
                    val durationMs = (media?.durationMs ?: 0L).takeIf { it > 0L }
                        ?: cursor.getLongOrDefault(0L, "duration", "duration_ms")
                    val positionMs = (media?.positionMs ?: 0L).takeIf { it > 0L }
                        ?: cursor.getLongOrDefault(0L, "position", "position_ms", "resume_position")
                    val itemPlayedAt = cursor.getLongOrDefault(
                        0L,
                        "playback_completion_date",
                        "last_played_time",
                        "played_at",
                        "played_date"
                    )
                    val playedAtMs = maxOf(media?.playedAtMs ?: 0L, itemPlayedAt).coerceAtLeast(0L)

                    val isMarkedPlayed = readState >= 1 ||
                        isPositionConsideredPlayed(positionMs, durationMs)

                    if (title.isBlank() && guid.isBlank() && audioUrl.isBlank()) {
                        continue
                    }

                    val epKey = guid.ifBlank { audioUrl.ifBlank { "${title}_$pubDate" } }
                    episodes.add(
                        EpisodeEntity(
                            id = "apod_ep_${(podcastId + "_" + epKey).hashCode()}",
                            podcastId = podcastId,
                            podcastTitle = podcastTitle,
                            guid = guid,
                            title = title.ifBlank { "Episode #$itemId" },
                            descriptionHtml = description,
                            audioUrl = audioUrl,
                            artworkUrl = parentPodcast?.artworkUrl ?: "drawable://lohe_logo_square",
                            publishedAt = pubDate.coerceAtLeast(0L),
                            durationMs = durationMs.coerceAtLeast(0L),
                            resumePositionMs = positionMs.coerceAtLeast(0L),
                            isPlayed = isMarkedPlayed,
                            playedAt = playedAtMs
                        )
                    )
                }
            }
        }

        return ParsedImportPayload(
            format = format,
            podcasts = podcasts,
            episodes = episodes
        )
    }

    private fun readPodcastAddictTables(
        db: SQLiteDatabase,
        format: ImportSourceFormat
    ): ParsedImportPayload {
        val tables = getTableNames(db)
        val podcastsTable = tables.firstOrNull {
            it.equals("podcasts", ignoreCase = true) || it.equals("feeds", ignoreCase = true)
        }
        val episodesTable = tables.firstOrNull {
            it.equals("episodes", ignoreCase = true) || it.equals("items", ignoreCase = true)
        }

        val showIdToPodcast = mutableMapOf<Long, PodcastEntity>()
        val podcasts = mutableListOf<PodcastEntity>()

        if (podcastsTable != null) {
            db.rawQuery("SELECT * FROM $podcastsTable", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val subscribedFlag = cursor.getIntOrDefault(1, "subscribed", "is_subscribed", "subscribed_status")
                    if (subscribedFlag == 0) continue

                    val rawId = cursor.getLongOrNull("_id", "id") ?: (podcasts.size + 1).toLong()
                    val title = cursor.getStringOrBlank("name", "title", "custom_name").ifBlank {
                        "Podcast Addict Show #$rawId"
                    }
                    val rssUrl = cursor.getStringOrBlank("feed_url", "url", "xml_url", "download_url", "rss_url")
                    val author = cursor.getStringOrBlank("author", "itunes_author").ifBlank { "Podcast Addict Import" }
                    val description = cursor.getStringOrBlank("description").ifBlank { "Imported from Podcast Addict" }
                    val artworkUrl = cursor.getStringOrBlank("thumbnail_url", "image_url", "artwork_url")
                    val podcastId = if (rssUrl.isNotBlank()) {
                        "pa_pod_${normalizeUrl(rssUrl).hashCode()}"
                    } else {
                        "pa_pod_$rawId"
                    }
                    val entity = PodcastEntity(
                        id = podcastId,
                        title = title,
                        author = author,
                        description = description,
                        feedUrl = rssUrl,
                        artworkUrl = artworkUrl.ifBlank { "drawable://lohe_logo_square" },
                        category = "Imported",
                        isSubscribed = true
                    )
                    showIdToPodcast[rawId] = entity
                    podcasts.add(entity)
                }
            }
        }

        val episodes = mutableListOf<EpisodeEntity>()
        if (episodesTable != null) {
            db.rawQuery("SELECT * FROM $episodesTable", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val rawEpId = cursor.getLongOrNull("_id", "id") ?: (episodes.size + 1).toLong()
                    val rawPodId = cursor.getLongOrNull("podcast_id", "feed_id", "feed") ?: -1L
                    val parentPodcast = showIdToPodcast[rawPodId]
                    val podcastId = parentPodcast?.id ?: "pa_pod_$rawPodId"
                    val podcastTitle = parentPodcast?.title ?: "Imported Show"

                    val title = cursor.getStringOrBlank("name", "title")
                    val guid = cursor.getStringOrBlank("uid", "guid", "item_identifier", "server_id")
                    val audioUrl = cursor.getStringOrBlank(
                        "content_url",
                        "enclosure_url",
                        "download_url",
                        "audio_url",
                        "media_url",
                        "url"
                    )
                    val pubDate = cursor.getLongOrDefault(
                        0L,
                        "publication_date",
                        "pub_date",
                        "pubDate",
                        "published_at",
                        "date"
                    )
                    val durationMs = cursor.getLongOrDefault(0L, "duration", "duration_ms")
                    val positionMs = cursor.getLongOrDefault(
                        0L,
                        "position_to_resume",
                        "position",
                        "resume_position",
                        "resume_position_ms",
                        "played_duration"
                    )
                    val seenStatus = cursor.getIntOrDefault(0, "seen_status", "played", "read", "is_played")
                    val playedAtMs = cursor.getLongOrDefault(
                        0L,
                        "playback_Date",
                        "playback_date",
                        "played_date",
                        "played_at",
                        "last_played_time"
                    )
                    val description = cursor.getStringOrBlank("description", "summary")
                    val isStarred = cursor.getIntOrDefault(0, "favorite", "is_starred", "starred") >= 1

                    if (title.isBlank() && guid.isBlank() && audioUrl.isBlank()) {
                        continue
                    }

                    val isMarkedPlayed = seenStatus >= 1 ||
                        isPositionConsideredPlayed(positionMs, durationMs)

                    val epKey = guid.ifBlank { audioUrl.ifBlank { "${title}_$pubDate" } }
                    episodes.add(
                        EpisodeEntity(
                            id = "pa_ep_${(podcastId + "_" + epKey).hashCode()}",
                            podcastId = podcastId,
                            podcastTitle = podcastTitle,
                            guid = guid,
                            title = title.ifBlank { "Episode #$rawEpId" },
                            descriptionHtml = description,
                            audioUrl = audioUrl,
                            artworkUrl = parentPodcast?.artworkUrl ?: "drawable://lohe_logo_square",
                            publishedAt = pubDate.coerceAtLeast(0L),
                            durationMs = durationMs.coerceAtLeast(0L),
                            resumePositionMs = positionMs.coerceAtLeast(0L),
                            isPlayed = isMarkedPlayed,
                            playedAt = playedAtMs.coerceAtLeast(0L),
                            isStarred = isStarred
                        )
                    )
                }
            }
        }

        return ParsedImportPayload(
            format = format,
            podcasts = podcasts,
            episodes = episodes
        )
    }

    private suspend fun applyImportInSingleTransaction(
        payload: ParsedImportPayload,
        onProgress: (ImportProgressState) -> Unit
    ): ImportResultSummary {
        var showsAdded = 0
        var episodesUpdated = 0
        var itemsSkipped = 0

        val totalWorkItems = (payload.podcasts.size + payload.episodes.size + payload.playlists.size).coerceAtLeast(1)
        var processedCount = 0

        database.withTransaction {
            val existingPodcasts = dao.getAllPodcasts().first().toMutableList()
            val existingEpisodes = dao.getAllEpisodes().first().toMutableList()

            // Map imported podcast ID -> resolved local podcast ID
            val importedPodIdToLocalPod = mutableMapOf<String, PodcastEntity>()

            for (importedPod in payload.podcasts) {
                processedCount++
                val normalizedImportRss = normalizeUrl(importedPod.feedUrl)
                val normalizedImportTitle = normalizeTitle(importedPod.title)

                val matchedPodcast = existingPodcasts.firstOrNull { existing ->
                    (normalizedImportRss.isNotBlank() && normalizeUrl(existing.feedUrl) == normalizedImportRss) ||
                        existing.id == importedPod.id
                } ?: existingPodcasts.firstOrNull { existing ->
                    normalizedImportTitle.isNotBlank() && normalizeTitle(existing.title) == normalizedImportTitle
                }

                if (matchedPodcast != null) {
                    if (!matchedPodcast.isSubscribed && importedPod.isSubscribed) {
                        val resubscribed = matchedPodcast.copy(
                            isSubscribed = true,
                            artworkUrl = matchedPodcast.artworkUrl.ifBlank { importedPod.artworkUrl }
                        )
                        dao.updatePodcast(resubscribed)
                        val idx = existingPodcasts.indexOfFirst { it.id == matchedPodcast.id }
                        if (idx >= 0) existingPodcasts[idx] = resubscribed
                        importedPodIdToLocalPod[importedPod.id] = resubscribed
                        showsAdded++
                    } else {
                        importedPodIdToLocalPod[importedPod.id] = matchedPodcast
                        itemsSkipped++
                    }
                } else if (importedPod.feedUrl.isNotBlank() || importedPod.title.isNotBlank()) {
                    val toInsert = importedPod.copy(isSubscribed = true)
                    dao.insertPodcast(toInsert)
                    existingPodcasts.add(toInsert)
                    importedPodIdToLocalPod[importedPod.id] = toInsert
                    showsAdded++
                } else {
                    itemsSkipped++
                }

                onProgress(
                    ImportProgressState(
                        isImporting = true,
                        stageMessage = "Importing subscriptions ($processedCount / $totalWorkItems)...",
                        processedItems = processedCount,
                        totalItems = totalWorkItems,
                        progressFraction = (0.3f + 0.3f * (processedCount.toFloat() / totalWorkItems)).coerceIn(0.3f, 0.6f)
                    )
                )
            }

            // Process episodes using strict priority:
            // 1. GUID first
            // 2. Enclosure URL second
            // 3. Title + publish date third
            for (importedEp in payload.episodes) {
                processedCount++
                val mappedParent = importedPodIdToLocalPod[importedEp.podcastId]
                val targetPodcastId = mappedParent?.id ?: importedEp.podcastId
                val targetPodcastTitle = mappedParent?.title ?: importedEp.podcastTitle

                val matchedExisting = findMatchingEpisode(
                    imported = importedEp,
                    targetPodcastId = targetPodcastId,
                    existingEpisodes = existingEpisodes
                )

                val effectiveDuration = maxOf(
                    matchedExisting?.durationMs ?: 0L,
                    importedEp.durationMs.coerceAtLeast(0L)
                )
                val importedPosition = importedEp.resumePositionMs.coerceAtLeast(0L)
                val importedCountsAsPlayed = importedEp.isPlayed ||
                    isPositionConsideredPlayed(importedPosition, effectiveDuration)
                val importedPlayedAt = importedEp.playedAt.coerceAtLeast(0L)

                if (matchedExisting != null) {
                    // Forward-only state merge:
                    // - never mark a played episode unplayed
                    // - never lower a saved position
                    // - never lower a played date
                    val mergedIsPlayed = matchedExisting.isPlayed || importedCountsAsPlayed
                    val mergedPositionMs = maxOf(matchedExisting.resumePositionMs, importedPosition)
                    val mergedPlayedAt = maxOf(matchedExisting.playedAt, importedPlayedAt)
                    val mergedStarred = matchedExisting.isStarred || importedEp.isStarred
                    val mergedInUpNext = matchedExisting.inUpNext || importedEp.inUpNext

                    val stateMovedForward = (!matchedExisting.isPlayed && mergedIsPlayed) ||
                        (mergedPositionMs > matchedExisting.resumePositionMs) ||
                        (mergedPlayedAt > matchedExisting.playedAt) ||
                        (!matchedExisting.isStarred && mergedStarred) ||
                        (!matchedExisting.inUpNext && mergedInUpNext)

                    val updatedEntity = matchedExisting.copy(
                        guid = matchedExisting.guid.ifBlank { importedEp.guid.trim() },
                        audioUrl = matchedExisting.audioUrl.ifBlank { importedEp.audioUrl.trim() },
                        durationMs = if (matchedExisting.durationMs > 0L) matchedExisting.durationMs else effectiveDuration,
                        isPlayed = mergedIsPlayed,
                        resumePositionMs = mergedPositionMs,
                        playedAt = mergedPlayedAt,
                        isStarred = mergedStarred,
                        inUpNext = mergedInUpNext
                    )

                    if (stateMovedForward) {
                        dao.updateEpisode(updatedEntity)
                        val idx = existingEpisodes.indexOfFirst { it.id == matchedExisting.id }
                        if (idx >= 0) existingEpisodes[idx] = updatedEntity
                        episodesUpdated++
                    } else {
                        if (updatedEntity != matchedExisting) {
                            // Backfill missing metadata (e.g., GUID) without counting as a state update
                            dao.updateEpisode(updatedEntity)
                            val idx = existingEpisodes.indexOfFirst { it.id == matchedExisting.id }
                            if (idx >= 0) existingEpisodes[idx] = updatedEntity
                        }
                        itemsSkipped++
                    }
                } else {
                    // Episode not yet in database
                    val hasMeaningfulIdentity = importedEp.guid.isNotBlank() ||
                        importedEp.audioUrl.isNotBlank() ||
                        importedEp.title.isNotBlank()

                    if (!hasMeaningfulIdentity) {
                        itemsSkipped++
                    } else {
                        val newEntity = importedEp.copy(
                            podcastId = targetPodcastId,
                            podcastTitle = targetPodcastTitle,
                            guid = importedEp.guid.trim(),
                            audioUrl = importedEp.audioUrl.trim(),
                            durationMs = effectiveDuration,
                            resumePositionMs = importedPosition,
                            isPlayed = importedCountsAsPlayed,
                            playedAt = importedPlayedAt
                        )
                        dao.insertEpisode(newEntity)
                        existingEpisodes.add(newEntity)

                        val hasForwardState = importedCountsAsPlayed ||
                            importedPosition > 0L ||
                            importedPlayedAt > 0L ||
                            importedEp.isStarred ||
                            importedEp.inUpNext

                        if (hasForwardState) {
                            episodesUpdated++
                        } else {
                            itemsSkipped++
                        }
                    }
                }

                onProgress(
                    ImportProgressState(
                        isImporting = true,
                        stageMessage = "Updating episodes ($processedCount / $totalWorkItems)...",
                        processedItems = processedCount,
                        totalItems = totalWorkItems,
                        progressFraction = (0.6f + 0.35f * (processedCount.toFloat() / totalWorkItems)).coerceIn(0.6f, 0.95f)
                    )
                )
            }

            if (payload.playlists.isNotEmpty()) {
                dao.insertPlaylists(payload.playlists)
            }
        }

        return ImportResultSummary(
            format = payload.format,
            showsAdded = showsAdded,
            episodesUpdated = episodesUpdated,
            itemsSkipped = itemsSkipped
        )
    }

    /**
     * Episode matching priority:
     * 1. Match by GUID first
     * 2. Then by enclosure URL
     * 3. Then by title plus publish date
     */
    internal fun findMatchingEpisode(
        imported: EpisodeEntity,
        targetPodcastId: String,
        existingEpisodes: List<EpisodeEntity>
    ): EpisodeEntity? {
        // 1. GUID match first
        val candidateGuid = imported.guid.trim()
        if (candidateGuid.isNotBlank()) {
            val sameShowByGuid = existingEpisodes.firstOrNull { existing ->
                existing.podcastId == targetPodcastId &&
                    (existing.guid.trim() == candidateGuid || existing.id.trim() == candidateGuid)
            }
            if (sameShowByGuid != null) return sameShowByGuid

            val anyShowByGuid = existingEpisodes.firstOrNull { existing ->
                existing.guid.trim() == candidateGuid || existing.id.trim() == candidateGuid
            }
            if (anyShowByGuid != null) return anyShowByGuid
        }

        // 2. Enclosure URL match second
        val candidateUrl = normalizeUrl(imported.audioUrl)
        if (candidateUrl.isNotBlank()) {
            val sameShowByUrl = existingEpisodes.firstOrNull { existing ->
                existing.podcastId == targetPodcastId &&
                    existing.audioUrl.isNotBlank() &&
                    normalizeUrl(existing.audioUrl) == candidateUrl
            }
            if (sameShowByUrl != null) return sameShowByUrl

            val anyShowByUrl = existingEpisodes.firstOrNull { existing ->
                existing.audioUrl.isNotBlank() &&
                    normalizeUrl(existing.audioUrl) == candidateUrl
            }
            if (anyShowByUrl != null) return anyShowByUrl
        }

        // 3. Title plus publish date match third
        val candidateTitle = normalizeTitle(imported.title)
        val candidatePubDate = imported.publishedAt
        if (candidateTitle.isNotBlank() && candidatePubDate > 0L) {
            // Exact timestamp match first (same show, then any show)
            val exactSameShow = existingEpisodes.firstOrNull { existing ->
                existing.podcastId == targetPodcastId &&
                    existing.publishedAt > 0L &&
                    normalizeTitle(existing.title) == candidateTitle &&
                    datesMatch(existing.publishedAt, candidatePubDate, exactOnly = true)
            }
            if (exactSameShow != null) return exactSameShow

            val exactAnyShow = existingEpisodes.firstOrNull { existing ->
                existing.publishedAt > 0L &&
                    normalizeTitle(existing.title) == candidateTitle &&
                    datesMatch(existing.publishedAt, candidatePubDate, exactOnly = true)
            }
            if (exactAnyShow != null) return exactAnyShow

            // Within 24h tolerance for same calendar publish day
            val sameDaySameShow = existingEpisodes.firstOrNull { existing ->
                existing.podcastId == targetPodcastId &&
                    existing.publishedAt > 0L &&
                    normalizeTitle(existing.title) == candidateTitle &&
                    datesMatch(existing.publishedAt, candidatePubDate, exactOnly = false)
            }
            if (sameDaySameShow != null) return sameDaySameShow

            val sameDayAnyShow = existingEpisodes.firstOrNull { existing ->
                existing.publishedAt > 0L &&
                    normalizeTitle(existing.title) == candidateTitle &&
                    datesMatch(existing.publishedAt, candidatePubDate, exactOnly = false)
            }
            if (sameDayAnyShow != null) return sameDayAnyShow
        }

        return null
    }

    private fun datesMatch(existingPub: Long, importedPub: Long, exactOnly: Boolean): Boolean {
        if (existingPub == importedPub) return true
        if (existingPub == importedPub * 1000L || existingPub * 1000L == importedPub) return true
        if (exactOnly) return false
        val toleranceMs = 86_400_000L // 24 hours
        if (abs(existingPub - importedPub) <= toleranceMs) return true
        if (abs(existingPub - importedPub * 1000L) <= toleranceMs) return true
        if (abs(existingPub * 1000L - importedPub) <= toleranceMs) return true
        return false
    }

    private fun Cursor.findColumnIndexIgnoreCase(vararg names: String): Int {
        val cols = columnNames
        for (target in names) {
            val idx = cols.indexOfFirst { it.equals(target, ignoreCase = true) }
            if (idx >= 0) return idx
        }
        return -1
    }

    private fun Cursor.getStringOrBlank(vararg names: String): String {
        val idx = findColumnIndexIgnoreCase(*names)
        if (idx < 0 || isNull(idx)) return ""
        return runCatching { getString(idx)?.trim().orEmpty() }.getOrDefault("")
    }

    private fun Cursor.getLongOrNull(vararg names: String): Long? {
        val idx = findColumnIndexIgnoreCase(*names)
        if (idx < 0 || isNull(idx)) return null
        return runCatching { getLong(idx) }.getOrNull()
    }

    private fun Cursor.getLongOrDefault(defaultValue: Long, vararg names: String): Long {
        return getLongOrNull(*names) ?: defaultValue
    }

    private fun Cursor.getIntOrDefault(defaultValue: Int, vararg names: String): Int {
        val idx = findColumnIndexIgnoreCase(*names)
        if (idx < 0 || isNull(idx)) return defaultValue
        return runCatching { getInt(idx) }.getOrDefault(defaultValue)
    }
}
