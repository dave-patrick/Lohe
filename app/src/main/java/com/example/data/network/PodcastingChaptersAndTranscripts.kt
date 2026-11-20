package com.example.data.network

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class EpisodeChapter(
    val startTimeMs: Long,
    val title: String,
    val url: String = "",
    val imageUrl: String = ""
)

data class TranscriptCue(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val speaker: String = "",
    val text: String
)

object PodcastingChaptersAndTranscripts {

    fun parsePodcastChaptersJson(jsonText: String): List<EpisodeChapter> {
        val clean = jsonText.trim()
        if (clean.isBlank()) return emptyList()
        return runCatching {
            val arr = if (clean.startsWith("[")) {
                JSONArray(clean)
            } else {
                JSONObject(clean).optJSONArray("chapters") ?: JSONArray()
            }
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val startSec = obj.optDouble("startTime", Double.NaN)
                    if (startSec.isNaN() || startSec < 0.0) continue
                    val title = obj.optString("title", "").trim()
                    if (title.isBlank()) continue
                    add(
                        EpisodeChapter(
                            startTimeMs = (startSec * 1000.0).toLong(),
                            title = title,
                            url = obj.optString("url", "").trim(),
                            imageUrl = obj.optString("img", obj.optString("image", "")).trim()
                        )
                    )
                }
            }.sortedBy { it.startTimeMs }
        }.getOrDefault(emptyList())
    }

    fun parseTimestampChaptersFromHtml(html: String): List<EpisodeChapter> {
        if (html.isBlank()) return emptyList()
        val plain = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString()
        val regex = Regex("""(?m)(?:^|\s|\()((?:\d{1,2}:)?\d{1,2}:\d{2})\)?\s*(?:[-–—|•:]\s*)?([^\n\r]{3,90})""")
        val found = linkedMapOf<Long, EpisodeChapter>()
        regex.findAll(plain).forEach { match ->
            val tsMs = parseTimestampTokenToMs(match.groupValues[1]) ?: return@forEach
            val label = match.groupValues[2].trim().trimEnd('.', ';', ',')
            if (label.isNotBlank() && !found.containsKey(tsMs)) {
                found[tsMs] = EpisodeChapter(startTimeMs = tsMs, title = label)
            }
        }
        return found.values.sortedBy { it.startTimeMs }
    }

    fun parseTranscriptContent(raw: String, mimeTypeHint: String = ""): List<TranscriptCue> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()

        if (trimmed.startsWith("{") || trimmed.startsWith("[") || mimeTypeHint.contains("json", ignoreCase = true)) {
            val jsonCues = parseJsonTranscript(trimmed)
            if (jsonCues.isNotEmpty()) return jsonCues
        }

        if (trimmed.startsWith("WEBVTT", ignoreCase = true) || trimmed.contains("-->")) {
            val vttOrSrt = parseVttOrSrtTranscript(trimmed)
            if (vttOrSrt.isNotEmpty()) return vttOrSrt
        }

        // Plain-text / paragraph fallback
        return trimmed.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapIndexed { index, line ->
                TranscriptCue(
                    startTimeMs = index * 10_000L,
                    endTimeMs = (index + 1) * 10_000L,
                    text = line
                )
            }
    }

    private fun parseJsonTranscript(jsonText: String): List<TranscriptCue> = runCatching {
        val arr = if (jsonText.startsWith("[")) {
            JSONArray(jsonText)
        } else {
            val root = JSONObject(jsonText)
            root.optJSONArray("segments")
                ?: root.optJSONArray("cues")
                ?: root.optJSONArray("transcript")
                ?: JSONArray()
        }
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val text = obj.optString("body", obj.optString("text", "")).trim()
                if (text.isBlank()) continue
                val startSec = obj.optDouble("startTime", obj.optDouble("start", 0.0))
                val endSec = obj.optDouble("endTime", obj.optDouble("end", startSec + 5.0))
                val speaker = obj.optString("speaker", "").trim()
                add(
                    TranscriptCue(
                        startTimeMs = (startSec.coerceAtLeast(0.0) * 1000.0).toLong(),
                        endTimeMs = (endSec.coerceAtLeast(startSec) * 1000.0).toLong(),
                        speaker = speaker,
                        text = text
                    )
                )
            }
        }.sortedBy { it.startTimeMs }
    }.getOrDefault(emptyList())

    private fun parseVttOrSrtTranscript(raw: String): List<TranscriptCue> {
        val blocks = raw.replace("\r\n", "\n").split(Regex("""\n{2,}"""))
        val tsRegex = Regex("""((?:\d{1,2}:)?\d{1,2}:\d{2}(?:[.,]\d{1,3})?)\s*-->\s*((?:\d{1,2}:)?\d{1,2}:\d{2}(?:[.,]\d{1,3})?)""")
        val cues = mutableListOf<TranscriptCue>()
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty() || lines.first().startsWith("WEBVTT", ignoreCase = true)) continue
            val tsIdx = lines.indexOfFirst { tsRegex.containsMatchIn(it) }
            if (tsIdx == -1) continue
            val tsMatch = tsRegex.find(lines[tsIdx]) ?: continue
            val startMs = parseTimestampTokenToMs(tsMatch.groupValues[1]) ?: continue
            val endMs = parseTimestampTokenToMs(tsMatch.groupValues[2]) ?: (startMs + 4000L)
            val bodyLines = lines.drop(tsIdx + 1).joinToString(" ")
                .replace(Regex("""<[^>]+>"""), "")
                .trim()
            if (bodyLines.isBlank()) continue

            val voiceMatch = Regex("""^<v\s+([^>]+)>(.+)$""").find(lines.drop(tsIdx + 1).joinToString(" "))
            val speaker = voiceMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
            val cleanText = voiceMatch?.groupValues?.getOrNull(2)?.replace(Regex("""<[^>]+>"""), "")?.trim() ?: bodyLines
            cues.add(TranscriptCue(startTimeMs = startMs, endTimeMs = endMs, speaker = speaker, text = cleanText))
        }
        return cues.sortedBy { it.startTimeMs }
    }

    fun parseTimestampTokenToMs(token: String): Long? {
        val clean = token.trim().replace(',', '.')
        val parts = clean.split(":")
        return runCatching {
            when (parts.size) {
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val s = (parts[2].toDouble() * 1000.0).toLong()
                    ((h * 3600L) + (m * 60L)) * 1000L + s
                }
                2 -> {
                    val m = parts[0].toLong()
                    val s = (parts[1].toDouble() * 1000.0).toLong()
                    (m * 60_000L) + s
                }
                else -> null
            }
        }.getOrNull()
    }

    fun formatTimestamp(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    suspend fun fetchTextUrl(client: OkHttpClient, url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        runCatching {
            val request = Request.Builder()
                .url(url.trim())
                .header("User-Agent", "Lohe/0.12.0 (Android; +https://lohe.app)")
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }
}
