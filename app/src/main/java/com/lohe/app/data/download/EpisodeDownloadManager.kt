package com.lohe.app.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.lohe.app.data.local.EpisodeEntity
import com.lohe.app.data.local.PodcastEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class PodcastStorageUsage(
    val podcastId: String,
    val podcastTitle: String,
    val episodeCount: Int,
    val totalBytes: Long
)

class EpisodeDownloadManager(
    context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val appContext = context.applicationContext
    private val cacheDir: File = File(appContext.filesDir, "lohe_audio_cache").apply {
        if (!exists()) mkdirs()
    }

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    fun fileForEpisode(episodeId: String): File {
        val safeName = episodeId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(cacheDir, "$safeName.audio")
    }

    fun resolvePlayableUri(episode: EpisodeEntity): String {
        val local = episode.localFilePath.takeIf { it.isNotBlank() }?.let(::File)
            ?: fileForEpisode(episode.id)
        return if (local.exists() && local.length() > 0L) {
            Uri.fromFile(local).toString()
        } else {
            episode.audioUrl
        }
    }

    fun isUnmeteredWifiConnected(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    suspend fun downloadEpisodeAudio(episode: EpisodeEntity): Result<File> = withContext(Dispatchers.IO) {
        if (episode.audioUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Missing audio URL"))
        }
        val existing = fileForEpisode(episode.id)
        if (existing.exists() && existing.length() > 0L) {
            return@withContext Result.success(existing)
        }

        _downloadingIds.value = _downloadingIds.value + episode.id
        val tempFile = File(cacheDir, "${existing.name}.tmp")
        try {
            val request = Request.Builder()
                .url(episode.audioUrl)
                .header("User-Agent", "LohePodcastAndroid/0.12.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.failure(IllegalStateException("Empty audio response"))
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output, bufferSize = 16 * 1024)
                    }
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                return@withContext Result.failure(IllegalStateException("Downloaded audio file was empty"))
            }
            if (existing.exists()) existing.delete()
            if (!tempFile.renameTo(existing)) {
                tempFile.copyTo(existing, overwrite = true)
                tempFile.delete()
            }
            Result.success(existing)
        } catch (t: Throwable) {
            tempFile.delete()
            Result.failure(t)
        } finally {
            _downloadingIds.value = _downloadingIds.value - episode.id
        }
    }

    suspend fun deleteEpisodeFile(episode: EpisodeEntity) = withContext(Dispatchers.IO) {
        if (episode.localFilePath.isNotBlank()) {
            runCatching { File(episode.localFilePath).delete() }
        }
        runCatching { fileForEpisode(episode.id).delete() }
    }

    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    fun getEpisodeFileSizeBytes(episode: EpisodeEntity): Long {
        val local = episode.localFilePath.takeIf { it.isNotBlank() }?.let(::File)
            ?: fileForEpisode(episode.id)
        return if (local.exists()) local.length() else 0L
    }

    fun computeStorageBreakdown(
        podcasts: List<PodcastEntity>,
        episodes: List<EpisodeEntity>
    ): Pair<Long, List<PodcastStorageUsage>> {
        val titleMap = podcasts.associateBy({ it.id }, { it.title })
        val downloaded = episodes.filter { it.isDownloaded }
        val grouped = downloaded.groupBy { it.podcastId }
        val list = grouped.map { (podId, eps) ->
            val bytes = eps.sumOf { ep ->
                getEpisodeFileSizeBytes(ep).takeIf { it > 0L } ?: (ep.durationMs / 1000L * 16_000L)
            }
            PodcastStorageUsage(
                podcastId = podId,
                podcastTitle = titleMap[podId] ?: eps.firstOrNull()?.podcastTitle ?: "Show",
                episodeCount = eps.size,
                totalBytes = bytes
            )
        }.sortedByDescending { it.totalBytes }
        val total = list.sumOf { it.totalBytes }
        return total to list
    }
}
