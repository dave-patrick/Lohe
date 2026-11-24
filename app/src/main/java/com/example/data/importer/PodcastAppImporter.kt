package com.example.data.importer

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.AutoQueueMode
import com.example.data.local.EpisodeBookmarkEntity
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
    LOHE_JSON("Lohe JSON Backup (.json)"),
    LOHE_DB("Lohe Database (.db)")
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
    val itemsSkipped: Int,
    val playlistsImported: Int = 0,
    val bookmarksImported: Int = 0
) {
    fun toDisplayBanner(): String {
        val extras = buildList {
            if (playlistsImported > 0) add("$playlistsImported playlists")
            if (bookmarksImported > 0) add("$bookmarksImported bookmarks")
        }.joinToString(", ")
        val suffix = if (extras.isNotEmpty()) ", $extras" else ""
        return "${format.label}: $showsAdded shows added, $episodesUpdated episodes updated, $itemsSkipped items skipped$suffix"
    }
}

private data class ParsedImportPayload(
    val format: ImportSourceFormat,
    val podcasts: List<PodcastEntity>,
    val episodes: List<EpisodeEntity>,
    val playlists: List<PlaylistEntity> = emptyList(),
    val bookmarks: List<EpisodeBookmarkEntity> = emptyList()
)

class PodcastAppImporter(
    private val context: Context,
    private val database: LoheDatabase,
    private val parser: RssAndOpmlParser = RssAndOpmlParser()
) {
    private val dao = database.loheDao()

    companion object {
        private val SQLITE_MAGIC = "SQLite format 3".toByteArray(Charsets.US_ASCII)
        private val WHITESPACE_REGEX = Regex("\\s+")

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
                .replace(WHITESPACE_REGEX, " ")
        }

        /**
         * Normalizes Unix epoch timestamps so seconds (10-digit) are promoted to milliseconds.
         */
        fun normalizeEpochMillis(raw: Long): Long {
            if (raw <= 0L) return 0L
            return if (raw < 100_000_000_000L) raw * 1000L else raw
        }

        /**
         * Parses durations formatted as "HH:MM:SS", "MM:SS", or numeric milliseconds/seconds.
         */
        fun parseDurationTextToMs(raw: String): Long {
            val clean = raw.trim()
            if (clean.isEmpty()) return 0L
            if (clean.contains(":")) {
                val parts = clean.split(":").mapNotNull { it.trim().toLongOrNull() }
                return when (parts.size) {
                    3 -> ((parts[0] * 3600L) + (parts[1] * 60L) + parts[2]) * 1000L
                    2 -> ((parts[0] * 60L) + parts[1]) * 1000L
                    else -> 0L
                }.coerceAtLeast(0L)
            }
            val numeric = clean.toLongOrNull() ?: return 0L
            return numeric.coerceAtLeast(0L)
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
                ImportSourceFormat.LOHE_DB,
                ImportSourceFormat.ANTENNAPOD_DB -> parseSqliteFileAutoDetect(file, format)
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
            playlists = backup.playlists,
            bookmarks = backup.bookmarks
        )
    }

    /**
     * Safely flushes Room's WAL journal and copies the active Lohe SQLite database
     * to a user-selected SAF URI so it can be migrated into Lohe Play Edition.
     */
    suspend fun exportLoheDatabaseToUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val tempExport = File.createTempFile("lohe_db_export_", ".db", context.cacheDir)
        try {
            exportLoheDatabaseToFile(tempExport).getOrThrow()
            val out = context.contentResolver.openOutputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Unable to open export destination"))
            out.use { output ->
                FileInputStream(tempExport).use { input ->
                    input.copyTo(output)
                }
            }
            Result.success("Exported Lohe SQLite database (${tempExport.length() / 1024} KB)")
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempExport.delete()
        }
    }

    suspend fun exportLoheDatabaseToFile(destFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Checkpoint WAL so all committed rows are materialized into the primary .db file
            val writableDb = database.openHelper.writableDatabase
            writableDb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                if (cursor.moveToFirst()) {
                    // Checkpoint completed
                }
            }
            val dbPath = writableDb.path
            val sourceFile = if (!dbPath.isNullOrBlank() && dbPath != ":memory:") {
                File(dbPath)
            } else {
                context.getDatabasePath("lohe_preview.db")
            }
            if (!sourceFile.exists() || sourceFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Local Lohe database file was not found"))
            }
            destFile.parentFile?.mkdirs()
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    private fun parseSqliteFileAutoDetect(dbFile: File, hintFormat: ImportSourceFormat): ParsedImportPayload {
        if (!isSqliteFile(dbFile)) {
            throw IllegalArgumentException("File is not a valid SQLite podcast database")
        }
        return parseSqliteDatabase(
            dbFile = dbFile,
            format = hintFormat
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
            val isLoheDb = isLoheSqliteDatabase(db, tableNames)
            val hasAntennaPodTables = tableNames.any { it.equals("Feeds", ignoreCase = true) } ||
                tableNames.any { it.equals("FeedItems", ignoreCase = true) }
            val hasPodcastAddictTables = tableNames.any { it.equals("podcasts", ignoreCase = true) } ||
                tableNames.any { it.equals("episodes", ignoreCase = true) }

            return when {
                isLoheDb ->
                    readLoheSqliteTables(db)
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

    private fun isLoheSqliteDatabase(db: SQLiteDatabase, tableNames: Set<String>): Boolean {
        val hasPodcasts = tableNames.any { it.equals("podcasts", ignoreCase = true) }
        val hasEpisodes = tableNames.any { it.equals("episodes", ignoreCase = true) }
        if (!hasPodcasts && !hasEpisodes) return false

        // Lohe-specific Room tables
        if (tableNames.any { it.equals("custom_playlists", ignoreCase = true) } ||
            tableNames.any { it.equals("episode_bookmarks", ignoreCase = true) }
        ) {
            return true
        }

        // Distinguish Lohe camelCase Room columns from Podcast Addict snake_case columns
        if (hasPodcasts) {
            val podCols = getColumnNames(db, "podcasts")
            if ("feedUrl" in podCols || "autoQueueMode" in podCols || "isSubscribed" in podCols) {
                return true
            }
        }
        if (hasEpisodes) {
            val epCols = getColumnNames(db, "episodes")
            if ("podcastId" in epCols || "resumePositionMs" in epCols || "inUpNext" in epCols) {
                return true
            }
        }
        return false
    }

    private fun getColumnNames(db: SQLiteDatabase, tableName: String): Set<String> {
        val cols = mutableSetOf<String>()
        runCatching {
            db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.let { cols.add(it) }
                    }
                }
            }
        }
        return cols
    }

    private fun readLoheSqliteTables(db: SQLiteDatabase): ParsedImportPayload {
        val tables = getTableNames(db)
        val podcasts = mutableListOf<PodcastEntity>()
        val episodes = mutableListOf<EpisodeEntity>()
        val playlists = mutableListOf<PlaylistEntity>()
        val bookmarks = mutableListOf<EpisodeBookmarkEntity>()

        val podcastsTable = tables.firstOrNull { it.equals("podcasts", ignoreCase = true) }
        if (podcastsTable != null) {
            db.rawQuery("SELECT * FROM $podcastsTable", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getStringOrBlank("id").ifBlank { continue }
                    val title = cursor.getStringOrBlank("title").ifBlank { "Migrated Podcast" }
                    val feedUrl = cursor.getStringOrBlank("feedUrl")
                    podcasts.add(
                        PodcastEntity(
                            id = id,
                            title = title,
                            author = cursor.getStringOrBlank("author").ifBlank { "Independent Creator" },
                            description = cursor.getStringOrBlank("description"),
                            feedUrl = feedUrl,
                            artworkUrl = cursor.getStringOrBlank("artworkUrl"),
                            category = cursor.getStringOrBlank("category").ifBlank { "Podcasts" },
                            autoQueueMode = cursor.getStringOrBlank("autoQueueMode").ifBlank { AutoQueueMode.OFF.name },
                            useCustomAudio = cursor.getIntOrDefault(0, "useCustomAudio") != 0,
                            customSpeed = cursor.getDoubleOrDefault(1.0, "customSpeed").toFloat().coerceIn(0.5f, 3.0f),
                            customSkipSilence = cursor.getIntOrDefault(0, "customSkipSilence") != 0,
                            customVolumeBoostDb = cursor.getIntOrDefault(0, "customVolumeBoostDb"),
                            notifyNewEpisodes = cursor.getIntOrDefault(1, "notifyNewEpisodes") != 0,
                            isSubscribed = cursor.getIntOrDefault(1, "isSubscribed") != 0,
                            lastSyncedAt = cursor.getLongOrDefault(System.currentTimeMillis(), "lastSyncedAt")
                        )
                    )
                }
            }
        }

        val episodesTable = tables.firstOrNull { it.equals("episodes", ignoreCase = true) }
        if (episodesTable != null) {
            db.rawQuery("SELECT * FROM $episodesTable", null).use { cursor ->
                val idxId = cursor.findColumnIndexIgnoreCase("id")
                val idxPodId = cursor.findColumnIndexIgnoreCase("podcastId")
                val idxPodTitle = cursor.findColumnIndexIgnoreCase("podcastTitle")
                val idxTitle = cursor.findColumnIndexIgnoreCase("title")
                val idxDesc = cursor.findColumnIndexIgnoreCase("descriptionHtml")
                val idxAudio = cursor.findColumnIndexIgnoreCase("audioUrl")
                val idxArt = cursor.findColumnIndexIgnoreCase("artworkUrl")
                val idxPub = cursor.findColumnIndexIgnoreCase("publishedAt")
                val idxDur = cursor.findColumnIndexIgnoreCase("durationMs")
                val idxResume = cursor.findColumnIndexIgnoreCase("resumePositionMs")
                val idxPlayed = cursor.findColumnIndexIgnoreCase("isPlayed")
                val idxArchived = cursor.findColumnIndexIgnoreCase("isArchived")
                val idxStarred = cursor.findColumnIndexIgnoreCase("isStarred")
                val idxUpNext = cursor.findColumnIndexIgnoreCase("inUpNext")
                val idxUpNextOrder = cursor.findColumnIndexIgnoreCase("upNextOrder")
                val idxChapUrl = cursor.findColumnIndexIgnoreCase("chaptersUrl")
                val idxChapJson = cursor.findColumnIndexIgnoreCase("chaptersJson")
                val idxTransUrl = cursor.findColumnIndexIgnoreCase("transcriptUrl")
                val idxTransMime = cursor.findColumnIndexIgnoreCase("transcriptMimeType")
                val idxTransText = cursor.findColumnIndexIgnoreCase("transcriptText")
                val idxGuid = cursor.findColumnIndexIgnoreCase("guid")
                val idxPlayedAt = cursor.findColumnIndexIgnoreCase("playedAt")

                fun str(i: Int): String = if (i < 0 || cursor.isNull(i)) "" else cursor.getString(i)?.trim().orEmpty()
                fun lng(i: Int): Long = if (i < 0 || cursor.isNull(i)) 0L else cursor.getLong(i)
                fun intVal(i: Int): Int = if (i < 0 || cursor.isNull(i)) 0 else cursor.getInt(i)

                while (cursor.moveToNext()) {
                    val id = str(idxId).ifBlank { continue }
                    episodes.add(
                        EpisodeEntity(
                            id = id,
                            podcastId = str(idxPodId),
                            podcastTitle = str(idxPodTitle),
                            title = str(idxTitle).ifBlank { "Migrated Episode" },
                            descriptionHtml = str(idxDesc),
                            audioUrl = str(idxAudio),
                            artworkUrl = str(idxArt),
                            publishedAt = lng(idxPub).coerceAtLeast(0L),
                            durationMs = lng(idxDur).coerceAtLeast(0L),
                            resumePositionMs = lng(idxResume).coerceAtLeast(0L),
                            isPlayed = intVal(idxPlayed) != 0,
                            isArchived = intVal(idxArchived) != 0,
                            // Local files inside another package's private directory cannot be accessed cross-app
                            isDownloaded = false,
                            localFilePath = "",
                            isStarred = intVal(idxStarred) != 0,
                            inUpNext = intVal(idxUpNext) != 0,
                            upNextOrder = intVal(idxUpNextOrder),
                            chaptersUrl = str(idxChapUrl),
                            chaptersJson = str(idxChapJson),
                            transcriptUrl = str(idxTransUrl),
                            transcriptMimeType = str(idxTransMime),
                            transcriptText = str(idxTransText),
                            guid = str(idxGuid),
                            playedAt = lng(idxPlayedAt).coerceAtLeast(0L)
                        )
                    )
                }
            }
        }

        val playlistsTable = tables.firstOrNull { it.equals("custom_playlists", ignoreCase = true) }
        if (playlistsTable != null) {
            db.rawQuery("SELECT * FROM $playlistsTable", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getStringOrBlank("id").ifBlank { continue }
                    val name = cursor.getStringOrBlank("name").ifBlank { "Migrated Playlist" }
                    playlists.add(
                        PlaylistEntity(
                            id = id,
                            name = name,
                            folderName = cursor.getStringOrBlank("folderName").ifBlank { "General" },
                            episodeIdsCsv = cursor.getStringOrBlank("episodeIdsCsv"),
                            assignedPodcastIdsCsv = cursor.getStringOrBlank("assignedPodcastIdsCsv"),
                            smartRuleType = cursor.getStringOrBlank("smartRuleType"),
                            isPinned = cursor.getIntOrDefault(0, "isPinned") != 0,
                            autoDownload = cursor.getIntOrDefault(0, "autoDownload") != 0,
                            createdAt = cursor.getLongOrDefault(System.currentTimeMillis(), "createdAt")
                        )
                    )
                }
            }
        }

        val bookmarksTable = tables.firstOrNull { it.equals("episode_bookmarks", ignoreCase = true) }
        if (bookmarksTable != null) {
            db.rawQuery("SELECT * FROM $bookmarksTable", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getStringOrBlank("id").ifBlank { continue }
                    val episodeId = cursor.getStringOrBlank("episodeId").ifBlank { continue }
                    bookmarks.add(
                        EpisodeBookmarkEntity(
                            id = id,
                            episodeId = episodeId,
                            positionMs = cursor.getLongOrDefault(0L, "positionMs").coerceAtLeast(0L),
                            label = cursor.getStringOrBlank("label").ifBlank { "Bookmark" },
                            createdAt = cursor.getLongOrDefault(System.currentTimeMillis(), "createdAt")
                        )
                    )
                }
            }
        }

        return ParsedImportPayload(
            format = ImportSourceFormat.LOHE_DB,
            podcasts = podcasts,
            episodes = episodes,
            playlists = playlists,
            bookmarks = bookmarks
        )
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
                    val durationMs = cursor.getDurationMsOrDefault("duration_ms", "duration")
                    val positionMs = cursor.getLongOrDefault(0L, "position", "position_ms", "resume_position")
                    val downloadUrl = cursor.getStringOrBlank("download_url", "enclosure_url", "audio_url", "file_url")
                    val completionDate = normalizeEpochMillis(cursor.getLongOrDefault(0L, "playback_completion_date", "played_at", "played_date"))
                    val lastPlayedTime = normalizeEpochMillis(cursor.getLongOrDefault(0L, "last_played_time"))
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
                    val pubDate = normalizeEpochMillis(cursor.getLongOrDefault(0L, "PubDate", "pubDate", "pub_date", "published_at"))
                    val readState = cursor.getIntOrDefault(0, "read", "played", "is_played")
                    val description = cursor.getStringOrBlank("description", "content_encoded")

                    val media = mediaByFeedItemId[itemId]
                    val audioUrl = (media?.downloadUrl ?: "").ifBlank {
                        cursor.getStringOrBlank("download_url", "enclosure_url", "audio_url", "media_url")
                    }
                    val durationMs = (media?.durationMs ?: 0L).takeIf { it > 0L }
                        ?: cursor.getDurationMsOrDefault("duration_ms", "duration")
                    val positionMs = (media?.positionMs ?: 0L).takeIf { it > 0L }
                        ?: cursor.getLongOrDefault(0L, "position", "position_ms", "resume_position")
                    val itemPlayedAt = normalizeEpochMillis(
                        cursor.getLongOrDefault(
                            0L,
                            "playback_completion_date",
                            "last_played_time",
                            "played_at",
                            "played_date"
                        )
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
                    val pubDate = normalizeEpochMillis(
                        cursor.getLongOrDefault(
                            0L,
                            "publication_date",
                            "pub_date",
                            "pubDate",
                            "published_at",
                            "date"
                        )
                    )
                    val durationMs = cursor.getDurationMsOrDefault("duration_ms", "duration")
                    val positionMs = cursor.getLongOrDefault(
                        0L,
                        "position_to_resume",
                        "position",
                        "resume_position",
                        "resume_position_ms",
                        "played_duration"
                    )
                    val seenStatus = cursor.getIntOrDefault(0, "seen_status", "played", "read", "is_played")
                    val playedAtMs = normalizeEpochMillis(
                        cursor.getLongOrDefault(
                            0L,
                            "playback_Date",
                            "playback_date",
                            "played_date",
                            "played_at",
                            "last_played_time"
                        )
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
        var playlistsImported = 0
        var bookmarksImported = 0

        val isNativeLoheMigration = payload.format == ImportSourceFormat.LOHE_DB ||
            payload.format == ImportSourceFormat.LOHE_JSON
        val totalWorkItems = (
            payload.podcasts.size +
                payload.episodes.size +
                payload.playlists.size +
                payload.bookmarks.size
            ).coerceAtLeast(1)
        var processedCount = 0

        database.withTransaction {
            // For native Lohe DB/JSON migrations containing episodes, replace the local Up Next queue
            // with the imported backup's queue instead of leaving stale local items behind.
            if (isNativeLoheMigration && payload.episodes.isNotEmpty()) {
                dao.clearUpNextQueue()
            }
            val existingPodcasts = dao.getAllPodcasts().first()
            val existingEpisodes = dao.getAllEpisodes().first()
            val existingBookmarks = dao.getAllBookmarks().first()

            // O(1) podcast lookup maps
            val podById = HashMap<String, PodcastEntity>(existingPodcasts.size * 2 + 16)
            val podByFeed = HashMap<String, PodcastEntity>(existingPodcasts.size * 2 + 16)
            val podByTitle = HashMap<String, PodcastEntity>(existingPodcasts.size * 2 + 16)
            fun indexPodcast(pod: PodcastEntity) {
                podById[pod.id] = pod
                val normFeed = normalizeUrl(pod.feedUrl)
                if (normFeed.isNotBlank()) podByFeed[normFeed] = pod
                val normTitle = normalizeTitle(pod.title)
                if (normTitle.isNotBlank()) podByTitle[normTitle] = pod
            }
            existingPodcasts.forEach(::indexPodcast)

            // Map imported IDs -> resolved local IDs
            val importedPodIdToLocalPod = HashMap<String, PodcastEntity>(payload.podcasts.size * 2 + 16)
            val importedEpIdToLocalEpId = HashMap<String, String>(payload.episodes.size * 2 + 16)
            val podcastsToUpsert = LinkedHashMap<String, PodcastEntity>()

            for (importedPod in payload.podcasts) {
                processedCount++
                val normalizedImportRss = normalizeUrl(importedPod.feedUrl)
                val normalizedImportTitle = normalizeTitle(importedPod.title)

                val matchedPodcast = (if (normalizedImportRss.isNotBlank()) podByFeed[normalizedImportRss] else null)
                    ?: podById[importedPod.id]
                    ?: (if (normalizedImportTitle.isNotBlank()) podByTitle[normalizedImportTitle] else null)

                if (matchedPodcast != null) {
                    if (!matchedPodcast.isSubscribed && importedPod.isSubscribed) {
                        val resubscribed = matchedPodcast.copy(
                            isSubscribed = true,
                            artworkUrl = matchedPodcast.artworkUrl.ifBlank { importedPod.artworkUrl },
                            autoQueueMode = if (isNativeLoheMigration && matchedPodcast.autoQueueMode == AutoQueueMode.OFF.name) importedPod.autoQueueMode else matchedPodcast.autoQueueMode,
                            useCustomAudio = if (isNativeLoheMigration) (matchedPodcast.useCustomAudio || importedPod.useCustomAudio) else matchedPodcast.useCustomAudio,
                            customSpeed = if (isNativeLoheMigration && importedPod.useCustomAudio && !matchedPodcast.useCustomAudio) importedPod.customSpeed else matchedPodcast.customSpeed,
                            customSkipSilence = if (isNativeLoheMigration && importedPod.useCustomAudio && !matchedPodcast.useCustomAudio) importedPod.customSkipSilence else matchedPodcast.customSkipSilence,
                            customVolumeBoostDb = if (isNativeLoheMigration && importedPod.useCustomAudio && !matchedPodcast.useCustomAudio) importedPod.customVolumeBoostDb else matchedPodcast.customVolumeBoostDb,
                            notifyNewEpisodes = if (isNativeLoheMigration) importedPod.notifyNewEpisodes else matchedPodcast.notifyNewEpisodes
                        )
                        podcastsToUpsert[resubscribed.id] = resubscribed
                        indexPodcast(resubscribed)
                        importedPodIdToLocalPod[importedPod.id] = resubscribed
                        showsAdded++
                    } else {
                        if (isNativeLoheMigration && importedPod.useCustomAudio && !matchedPodcast.useCustomAudio) {
                            val updatedAudio = matchedPodcast.copy(
                                useCustomAudio = true,
                                customSpeed = importedPod.customSpeed,
                                customSkipSilence = importedPod.customSkipSilence,
                                customVolumeBoostDb = importedPod.customVolumeBoostDb,
                                autoQueueMode = if (matchedPodcast.autoQueueMode == AutoQueueMode.OFF.name) importedPod.autoQueueMode else matchedPodcast.autoQueueMode
                            )
                            podcastsToUpsert[updatedAudio.id] = updatedAudio
                            indexPodcast(updatedAudio)
                            importedPodIdToLocalPod[importedPod.id] = updatedAudio
                        } else {
                            importedPodIdToLocalPod[importedPod.id] = matchedPodcast
                        }
                        itemsSkipped++
                    }
                } else if (importedPod.feedUrl.isNotBlank() || importedPod.title.isNotBlank()) {
                    val toInsert = if (isNativeLoheMigration) importedPod else importedPod.copy(isSubscribed = true)
                    podcastsToUpsert[toInsert.id] = toInsert
                    indexPodcast(toInsert)
                    importedPodIdToLocalPod[importedPod.id] = toInsert
                    showsAdded++
                } else {
                    itemsSkipped++
                }

                if (processedCount % 25 == 0 || processedCount == payload.podcasts.size) {
                    onProgress(
                        ImportProgressState(
                            isImporting = true,
                            stageMessage = "Importing subscriptions ($processedCount / $totalWorkItems)...",
                            processedItems = processedCount,
                            totalItems = totalWorkItems,
                            progressFraction = (0.3f + 0.2f * (processedCount.toFloat() / totalWorkItems)).coerceIn(0.3f, 0.5f)
                        )
                    )
                }
            }
            if (podcastsToUpsert.isNotEmpty()) {
                podcastsToUpsert.values.chunked(250).forEach { dao.insertPodcasts(it) }
            }

            // O(1) episode lookup index preserving strict matching priority:
            // 1. GUID first (same show, then any show)
            // 2. Enclosure URL second (same show, then any show)
            // 3. Title + publish date third (exact timestamp, then 24h tolerance)
            val epCap = (existingEpisodes.size + payload.episodes.size).coerceAtLeast(16)
            val epById = HashMap<String, EpisodeEntity>(epCap)
            val epByPodGuid = HashMap<String, EpisodeEntity>(epCap)
            val epByGuid = HashMap<String, EpisodeEntity>(epCap)
            val epByPodUrl = HashMap<String, EpisodeEntity>(epCap)
            val epByUrl = HashMap<String, EpisodeEntity>(epCap)
            val epByPodTitle = HashMap<String, MutableList<EpisodeEntity>>(epCap)
            val epByTitle = HashMap<String, MutableList<EpisodeEntity>>(epCap)

            fun indexEpisode(ep: EpisodeEntity) {
                epById[ep.id] = ep
                val g = ep.guid.trim()
                if (g.isNotBlank()) {
                    epByPodGuid["${ep.podcastId}|$g"] = ep
                    epByGuid[g] = ep
                }
                val idKey = ep.id.trim()
                if (idKey.isNotBlank()) {
                    epByPodGuid.putIfAbsent("${ep.podcastId}|$idKey", ep)
                    epByGuid.putIfAbsent(idKey, ep)
                }
                val u = normalizeUrl(ep.audioUrl)
                if (u.isNotBlank()) {
                    epByPodUrl["${ep.podcastId}|$u"] = ep
                    epByUrl[u] = ep
                }
                val t = normalizeTitle(ep.title)
                if (t.isNotBlank() && ep.publishedAt > 0L) {
                    val podList = epByPodTitle.getOrPut("${ep.podcastId}|$t") { ArrayList(2) }
                    val existingIdx = podList.indexOfFirst { it.id == ep.id }
                    if (existingIdx >= 0) podList[existingIdx] = ep else podList.add(ep)

                    val globalList = epByTitle.getOrPut(t) { ArrayList(2) }
                    val globalIdx = globalList.indexOfFirst { it.id == ep.id }
                    if (globalIdx >= 0) globalList[globalIdx] = ep else globalList.add(ep)
                }
            }
            existingEpisodes.forEach(::indexEpisode)

            fun lookupEpisodeFast(imported: EpisodeEntity, targetPodcastId: String): EpisodeEntity? {
                val candidateGuid = imported.guid.trim()
                if (candidateGuid.isNotBlank()) {
                    epByPodGuid["$targetPodcastId|$candidateGuid"]?.let { return epById[it.id] ?: it }
                    epByGuid[candidateGuid]?.let { return epById[it.id] ?: it }
                }
                val candidateUrl = normalizeUrl(imported.audioUrl)
                if (candidateUrl.isNotBlank()) {
                    epByPodUrl["$targetPodcastId|$candidateUrl"]?.let { return epById[it.id] ?: it }
                    epByUrl[candidateUrl]?.let { return epById[it.id] ?: it }
                }
                val candidateTitle = normalizeTitle(imported.title)
                val candidatePubDate = imported.publishedAt
                if (candidateTitle.isNotBlank() && candidatePubDate > 0L) {
                    val sameShowCandidates = epByPodTitle["$targetPodcastId|$candidateTitle"]
                    if (!sameShowCandidates.isNullOrEmpty()) {
                        sameShowCandidates.firstOrNull { datesMatch(it.publishedAt, candidatePubDate, exactOnly = true) }
                            ?.let { return epById[it.id] ?: it }
                    }
                    val anyShowCandidates = epByTitle[candidateTitle]
                    if (!anyShowCandidates.isNullOrEmpty()) {
                        anyShowCandidates.firstOrNull { datesMatch(it.publishedAt, candidatePubDate, exactOnly = true) }
                            ?.let { return epById[it.id] ?: it }
                    }
                    if (!sameShowCandidates.isNullOrEmpty()) {
                        sameShowCandidates.firstOrNull { datesMatch(it.publishedAt, candidatePubDate, exactOnly = false) }
                            ?.let { return epById[it.id] ?: it }
                    }
                    if (!anyShowCandidates.isNullOrEmpty()) {
                        anyShowCandidates.firstOrNull { datesMatch(it.publishedAt, candidatePubDate, exactOnly = false) }
                            ?.let { return epById[it.id] ?: it }
                    }
                }
                return null
            }

            val episodesToUpsert = LinkedHashMap<String, EpisodeEntity>(payload.episodes.size)
            val progressStride = (payload.episodes.size / 40).coerceAtLeast(100)

            for (importedEp in payload.episodes) {
                processedCount++
                val mappedParent = importedPodIdToLocalPod[importedEp.podcastId]
                val targetPodcastId = mappedParent?.id ?: importedEp.podcastId
                val targetPodcastTitle = mappedParent?.title ?: importedEp.podcastTitle

                val matchedExisting = lookupEpisodeFast(importedEp, targetPodcastId)

                val effectiveDuration = maxOf(
                    matchedExisting?.durationMs ?: 0L,
                    importedEp.durationMs.coerceAtLeast(0L)
                )
                val importedPosition = importedEp.resumePositionMs.coerceAtLeast(0L)
                val importedCountsAsPlayed = importedEp.isPlayed ||
                    isPositionConsideredPlayed(importedPosition, effectiveDuration)
                val importedPlayedAt = importedEp.playedAt.coerceAtLeast(0L)

                if (matchedExisting != null) {
                    importedEpIdToLocalEpId[importedEp.id] = matchedExisting.id

                    // Forward-only state merge (except actively queued Up Next items from a native Lohe DB
                    // migration, which must unarchive/unplay and adopt the exported queue order so they
                    // actually appear in Up Next).
                    val importedActivelyQueued = isNativeLoheMigration &&
                        importedEp.inUpNext &&
                        !importedCountsAsPlayed &&
                        !importedEp.isArchived
                    val mergedIsPlayed = if (importedActivelyQueued) false else (matchedExisting.isPlayed || importedCountsAsPlayed)
                    val mergedPositionMs = maxOf(matchedExisting.resumePositionMs, importedPosition)
                    val mergedPlayedAt = maxOf(matchedExisting.playedAt, importedPlayedAt)
                    val mergedStarred = matchedExisting.isStarred || importedEp.isStarred
                    val mergedInUpNext = matchedExisting.inUpNext || importedEp.inUpNext
                    val mergedUpNextOrder = if (importedActivelyQueued || (!matchedExisting.inUpNext && importedEp.inUpNext)) {
                        importedEp.upNextOrder
                    } else {
                        matchedExisting.upNextOrder
                    }
                    val mergedArchived = if (importedActivelyQueued) {
                        false
                    } else {
                        matchedExisting.isArchived || (isNativeLoheMigration && importedEp.isArchived)
                    }

                    val healedDuration = matchedExisting.durationMs <= 5_000L && effectiveDuration > matchedExisting.durationMs
                    val stateMovedForward = (mergedIsPlayed != matchedExisting.isPlayed) ||
                        (mergedPositionMs > matchedExisting.resumePositionMs) ||
                        (mergedPlayedAt > matchedExisting.playedAt) ||
                        healedDuration ||
                        (!matchedExisting.isStarred && mergedStarred) ||
                        (!matchedExisting.inUpNext && mergedInUpNext) ||
                        (mergedUpNextOrder != matchedExisting.upNextOrder) ||
                        (mergedArchived != matchedExisting.isArchived)

                    val updatedEntity = matchedExisting.copy(
                        guid = matchedExisting.guid.ifBlank { importedEp.guid.trim() },
                        audioUrl = matchedExisting.audioUrl.ifBlank { importedEp.audioUrl.trim() },
                        durationMs = if (matchedExisting.durationMs > 5_000L) matchedExisting.durationMs else effectiveDuration,
                        isPlayed = mergedIsPlayed,
                        resumePositionMs = mergedPositionMs,
                        playedAt = mergedPlayedAt,
                        isStarred = mergedStarred,
                        inUpNext = mergedInUpNext,
                        upNextOrder = mergedUpNextOrder,
                        isArchived = mergedArchived,
                        chaptersUrl = matchedExisting.chaptersUrl.ifBlank { importedEp.chaptersUrl },
                        chaptersJson = matchedExisting.chaptersJson.ifBlank { importedEp.chaptersJson },
                        transcriptUrl = matchedExisting.transcriptUrl.ifBlank { importedEp.transcriptUrl },
                        transcriptMimeType = matchedExisting.transcriptMimeType.ifBlank { importedEp.transcriptMimeType },
                        transcriptText = matchedExisting.transcriptText.ifBlank { importedEp.transcriptText }
                    )

                    if (stateMovedForward) {
                        episodesToUpsert[updatedEntity.id] = updatedEntity
                        indexEpisode(updatedEntity)
                        episodesUpdated++
                    } else {
                        if (updatedEntity != matchedExisting) {
                            episodesToUpsert[updatedEntity.id] = updatedEntity
                            indexEpisode(updatedEntity)
                        }
                        itemsSkipped++
                    }
                } else {
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
                            playedAt = importedPlayedAt,
                            isDownloaded = false,
                            localFilePath = ""
                        )
                        episodesToUpsert[newEntity.id] = newEntity
                        indexEpisode(newEntity)
                        importedEpIdToLocalEpId[importedEp.id] = newEntity.id

                        val hasForwardState = importedCountsAsPlayed ||
                            importedPosition > 0L ||
                            importedPlayedAt > 0L ||
                            importedEp.isStarred ||
                            importedEp.inUpNext ||
                            (isNativeLoheMigration && importedEp.isArchived)

                        if (hasForwardState) {
                            episodesUpdated++
                        } else {
                            itemsSkipped++
                        }
                    }
                }

                if (processedCount % progressStride == 0) {
                    onProgress(
                        ImportProgressState(
                            isImporting = true,
                            stageMessage = "Merging episodes ($processedCount / $totalWorkItems)...",
                            processedItems = processedCount,
                            totalItems = totalWorkItems,
                            progressFraction = (0.5f + 0.35f * (processedCount.toFloat() / totalWorkItems)).coerceIn(0.5f, 0.85f)
                        )
                    )
                }
            }

            if (episodesToUpsert.isNotEmpty()) {
                onProgress(
                    ImportProgressState(
                        isImporting = true,
                        stageMessage = "Writing ${episodesToUpsert.size} episodes to database...",
                        processedItems = processedCount,
                        totalItems = totalWorkItems,
                        progressFraction = 0.9f
                    )
                )
                episodesToUpsert.values.chunked(500).forEach { chunk ->
                    dao.insertEpisodes(chunk)
                }
            }

            if (payload.playlists.isNotEmpty()) {
                val remappedPlaylists = payload.playlists.map { pl ->
                    val remappedEps = pl.episodeIdsCsv
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { oldId -> importedEpIdToLocalEpId[oldId] ?: oldId }
                        .distinct()
                        .joinToString(",")
                    val remappedPods = pl.assignedPodcastIdsCsv
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .map { oldPodId -> importedPodIdToLocalPod[oldPodId]?.id ?: oldPodId }
                        .distinct()
                        .joinToString(",")
                    pl.copy(
                        episodeIdsCsv = remappedEps,
                        assignedPodcastIdsCsv = remappedPods
                    )
                }
                dao.insertPlaylists(remappedPlaylists)
                playlistsImported = remappedPlaylists.size
            }

            if (payload.bookmarks.isNotEmpty()) {
                val existingBookmarkIds = existingBookmarks.mapTo(HashSet()) { it.id }
                val existingBookmarkKeys = existingBookmarks.mapTo(HashSet()) { "${it.episodeId}|${it.positionMs}|${it.label}" }
                val bookmarksToInsert = mutableListOf<EpisodeBookmarkEntity>()
                for (bm in payload.bookmarks) {
                    val targetEpId = importedEpIdToLocalEpId[bm.episodeId] ?: bm.episodeId
                    val key = "$targetEpId|${bm.positionMs}|${bm.label}"
                    if (bm.id !in existingBookmarkIds && key !in existingBookmarkKeys) {
                        val remapped = bm.copy(episodeId = targetEpId)
                        bookmarksToInsert.add(remapped)
                        existingBookmarkIds.add(remapped.id)
                        existingBookmarkKeys.add(key)
                    }
                }
                if (bookmarksToInsert.isNotEmpty()) {
                    dao.insertBookmarks(bookmarksToInsert)
                    bookmarksImported = bookmarksToInsert.size
                }
            }
        }

        return ImportResultSummary(
            format = payload.format,
            showsAdded = showsAdded,
            episodesUpdated = episodesUpdated,
            itemsSkipped = itemsSkipped,
            playlistsImported = playlistsImported,
            bookmarksImported = bookmarksImported
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

    private fun Cursor.getDurationMsOrDefault(vararg names: String): Long {
        for (target in names) {
            val idx = findColumnIndexIgnoreCase(target)
            if (idx < 0 || isNull(idx)) continue
            val rawText = runCatching { getString(idx)?.trim().orEmpty() }.getOrDefault("")
            if (rawText.contains(":")) {
                val parsed = parseDurationTextToMs(rawText)
                if (parsed > 0L) return parsed
            } else {
                val numeric = runCatching { getLong(idx) }.getOrNull()
                    ?: rawText.toLongOrNull()
                    ?: 0L
                if (numeric > 0L) return numeric
            }
        }
        return 0L
    }

    private fun Cursor.getDoubleOrDefault(defaultValue: Double, vararg names: String): Double {
        val idx = findColumnIndexIgnoreCase(*names)
        if (idx < 0 || isNull(idx)) return defaultValue
        return runCatching { getDouble(idx) }.getOrDefault(defaultValue)
    }
}
