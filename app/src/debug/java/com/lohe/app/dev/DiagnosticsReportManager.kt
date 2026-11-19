package com.lohe.app.dev

import android.content.Context
import android.os.Build
import com.lohe.app.playback.PlaybackUiState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class DiagnosticEvent(
    val timestampMs: Long,
    val category: String,
    val message: String
)

class DiagnosticsReportManager(private val context: Context) {

    companion object {
        const val MAX_REPORT_FOLDERS = 5
        const val MAX_RECENT_EVENTS = 200
        const val REDACTED_URL_TOKEN = "[REDACTED_URL]"

        private val URL_REGEX = Regex(
            """\b(?:https?|ftp|file|content|rss|feed|drawable)://[^\s"'<>)\]]+""",
            RegexOption.IGNORE_CASE
        )

        private val sequenceCounter = AtomicLong(1L)

        fun redactUrls(text: String): String {
            if (text.isEmpty()) return text
            return URL_REGEX.replace(text, REDACTED_URL_TOKEN)
        }
    }

    val reportsRootDir: File
        get() = File(context.noBackupFilesDir, "diagnostics_reports").apply {
            if (!exists()) mkdirs()
        }

    private val recentEvents = ArrayDeque<DiagnosticEvent>()
    private val lock = Any()

    fun recordEvent(category: String, message: String, timestampMs: Long = System.currentTimeMillis()) {
        val sanitizedCategory = redactUrls(category.trim().ifBlank { "general" })
        val sanitizedMessage = redactUrls(message.trim())
        synchronized(lock) {
            if (recentEvents.size >= MAX_RECENT_EVENTS) {
                recentEvents.removeFirst()
            }
            recentEvents.addLast(
                DiagnosticEvent(
                    timestampMs = timestampMs,
                    category = sanitizedCategory,
                    message = sanitizedMessage
                )
            )
        }
    }

    fun getRecentEventsSnapshot(): List<DiagnosticEvent> {
        synchronized(lock) {
            return recentEvents.toList()
        }
    }

    fun clearEvents() {
        synchronized(lock) {
            recentEvents.clear()
        }
    }

    fun listReportFolders(): List<File> {
        val root = reportsRootDir
        val dirs = root.listFiles { file -> file.isDirectory && file.name.startsWith("report_") }
            ?: emptyArray()
        return dirs.sortedBy { it.name }
    }

    fun recordBugReport(playbackState: PlaybackUiState?): File {
        val nowMs = System.currentTimeMillis()
        val seq = sequenceCounter.getAndIncrement()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(nowMs))
        val folderName = String.format(Locale.US, "report_%s_%04d", stamp, seq)
        val reportDir = File(reportsRootDir, folderName).apply { mkdirs() }

        val eventsSnapshot = getRecentEventsSnapshot()
        val deviceInfoJson = buildDeviceInfoJson()
        val playbackJson = buildPlaybackStateJson(playbackState)
        val eventsJsonArray = buildEventsJsonArray(eventsSnapshot)

        val combinedReport = JSONObject().apply {
            put("reportId", folderName)
            put("generatedAtMs", nowMs)
            put("deviceInfo", deviceInfoJson)
            put("playbackState", playbackJson)
            put("recentEvents", eventsJsonArray)
        }

        File(reportDir, "device_info.json").writeText(deviceInfoJson.toString(2))
        File(reportDir, "playback_state.json").writeText(playbackJson.toString(2))
        File(reportDir, "report.json").writeText(combinedReport.toString(2))

        val eventsLogText = buildString {
            for (ev in eventsSnapshot) {
                append(ev.timestampMs)
                append(" [")
                append(redactUrls(ev.category))
                append("] ")
                append(redactUrls(ev.message))
                append('\n')
            }
        }
        File(reportDir, "events.log").writeText(eventsLogText)

        enforceRetentionCap()
        return reportDir
    }

    private fun enforceRetentionCap() {
        val folders = listReportFolders()
        if (folders.size > MAX_REPORT_FOLDERS) {
            val toDelete = folders.take(folders.size - MAX_REPORT_FOLDERS)
            for (oldDir in toDelete) {
                oldDir.deleteRecursively()
            }
        }
    }

    private fun buildDeviceInfoJson(): JSONObject {
        val pkgInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()

        return JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER ?: "unknown")
            put("model", Build.MODEL ?: "unknown")
            put("brand", Build.BRAND ?: "unknown")
            put("device", Build.DEVICE ?: "unknown")
            put("sdkInt", Build.VERSION.SDK_INT)
            put("androidRelease", Build.VERSION.RELEASE ?: "unknown")
            put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS?.toList() ?: emptyList<String>()))
            put("packageName", context.packageName)
            put("versionName", pkgInfo?.versionName ?: "0.12.0")
            put("locale", Locale.getDefault().toLanguageTag())
        }
    }

    private fun buildPlaybackStateJson(playbackState: PlaybackUiState?): JSONObject {
        val state = playbackState ?: PlaybackUiState()
        return JSONObject().apply {
            put("isPlaying", state.isPlaying)
            put("positionMs", state.positionMs)
            put("durationMs", state.durationMs)
            put("playbackSpeed", state.playbackSpeed.toDouble())
            put("skipSilenceEnabled", state.skipSilenceEnabled)
            put("volumeBoostDb", state.volumeBoostDb)
            put("sleepTimerRemainingSeconds", state.sleepTimerRemainingSeconds)
            put("sleepAtEndOfEpisode", state.sleepAtEndOfEpisode)

            val ep = state.currentEpisode
            if (ep != null) {
                put(
                    "currentEpisode",
                    JSONObject().apply {
                        put("id", redactUrls(ep.id))
                        put("podcastId", redactUrls(ep.podcastId))
                        put("podcastTitle", redactUrls(ep.podcastTitle))
                        put("title", redactUrls(ep.title))
                        put("guid", redactUrls(ep.guid))
                        put("audioUrl", if (ep.audioUrl.isNotBlank()) REDACTED_URL_TOKEN else "")
                        put("artworkUrl", if (ep.artworkUrl.isNotBlank()) REDACTED_URL_TOKEN else "")
                        put("chaptersUrl", if (ep.chaptersUrl.isNotBlank()) REDACTED_URL_TOKEN else "")
                        put("transcriptUrl", if (ep.transcriptUrl.isNotBlank()) REDACTED_URL_TOKEN else "")
                        put("publishedAt", ep.publishedAt)
                        put("durationMs", ep.durationMs)
                        put("resumePositionMs", ep.resumePositionMs)
                        put("isPlayed", ep.isPlayed)
                    }
                )
            } else {
                put("currentEpisode", JSONObject.NULL)
            }
        }
    }

    private fun buildEventsJsonArray(events: List<DiagnosticEvent>): JSONArray {
        val arr = JSONArray()
        for (ev in events) {
            arr.put(
                JSONObject().apply {
                    put("timestampMs", ev.timestampMs)
                    put("category", redactUrls(ev.category))
                    put("message", redactUrls(ev.message))
                }
            )
        }
        return arr
    }
}
