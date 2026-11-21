package com.example.data.sync

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import com.example.data.download.EpisodeDownloadManager
import com.example.data.local.LoheDatabase
import com.example.data.local.LohePreferencesRepository
import com.example.data.repository.LoheRepository
import com.example.edition.Edition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

/**
 * Owner-only, signature-locked ContentProvider enabling the private `com.lohe.sync`
 * companion app to sync podcasts, playlists, episode progress/played/archived/starred state,
 * bookmarks, and Up Next order between installed Play (`com.lohe.app`) and FOSS
 * (`com.lohe.app.foss`) builds on the same device.
 *
 * Protected both by a `signature`-level manifest permission and defense-in-depth runtime
 * verification of the caller's UID signature and package name.
 */
class OwnerSyncProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    private fun buildRepository(ctx: Context): LoheRepository {
        val db = LoheDatabase.getInstance(ctx)
        return LoheRepository(
            dao = db.loheDao(),
            downloadManager = EpisodeDownloadManager(ctx),
            preferencesRepository = LohePreferencesRepository(ctx)
        )
    }

    private fun resolveVersionName(ctx: Context): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            }
            info.versionName.orEmpty().ifBlank { "1219.0" }
        } catch (_: Exception) {
            "1219.0"
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context?.applicationContext ?: throw IllegalStateException("No context")
        verifyOwnerCaller(ctx)
        val dao = LoheDatabase.getInstance(ctx).loheDao()
        val repository = buildRepository(ctx)

        return when (method) {
            "summary" -> runBlocking {
                val allPodcasts = dao.getAllPodcasts().first()
                val subscribedPodcasts = allPodcasts.count { it.isSubscribed }
                val allEpisodes = dao.getAllEpisodes().first()
                val played = allEpisodes.count { it.isPlayed }
                val inProgress = allEpisodes.count { !it.isPlayed && it.resumePositionMs > 0L }
                val queued = allEpisodes.count { it.inUpNext && !it.isPlayed && !it.isArchived }
                val playlists = dao.getCustomPlaylists().first().count { it.smartRuleType.isEmpty() }
                val ver = resolveVersionName(ctx)

                Bundle().apply {
                    putBoolean("ok", true)
                    putString("versionName", ver)
                    putString("buildId", ver)
                    putString("distribution", if (Edition.HAS_PRO) "play" else "foss")
                    putInt("subscribedPodcasts", subscribedPodcasts)
                    putInt("totalPodcasts", allPodcasts.size)
                    putInt("episodes", allEpisodes.size)
                    putInt("played", played)
                    putInt("inProgress", inProgress)
                    putInt("queued", queued)
                    putInt("playlists", playlists)
                }
            }

            "apply_incoming" -> {
                val incomingFile = File(ctx.cacheDir, INCOMING_FILE_NAME)
                try {
                    if (!incomingFile.exists()) {
                        return Bundle().apply {
                            putBoolean("ok", false)
                            putString("error", "No incoming snapshot file found")
                        }
                    }
                    if (incomingFile.length() > MAX_SNAPSHOT_BYTES) {
                        return Bundle().apply {
                            putBoolean("ok", false)
                            putString("error", "Snapshot exceeds 64 MiB safety limit")
                        }
                    }
                    val rawJson = incomingFile.readText(Charsets.UTF_8)
                    val isReplaceMode = arg.isNullOrBlank() || arg.equals("replace", ignoreCase = true)

                    val applied = runBlocking {
                        val (remoteBackup, _) = DriveSyncEngine.parseRemoteSyncPayload(rawJson)
                        val localPodcasts = dao.getAllPodcasts().first()
                        val remoteHasRealShows = remoteBackup.podcasts.any { !it.id.startsWith("sample_pod_") }
                        val localOnlySampleShows = localPodcasts.isNotEmpty() &&
                            localPodcasts.all { it.id.startsWith("sample_pod_") }

                        // If replacing, or if local only has starter sample shows and remote has real shows,
                        // remove local shows that are not present in remoteBackup.
                        if (isReplaceMode || (localOnlySampleShows && remoteHasRealShows)) {
                            val remoteFeedKeys = remoteBackup.podcasts.map {
                                DriveSyncEngine.normalizeFeedKey(it.feedUrl, it.id, it.title)
                            }.toSet()
                            localPodcasts.forEach { localPod ->
                                val key = DriveSyncEngine.normalizeFeedKey(localPod.feedUrl, localPod.id, localPod.title)
                                val shouldRemove = if (isReplaceMode) {
                                    remoteBackup.podcasts.isNotEmpty() && key !in remoteFeedKeys
                                } else {
                                    localPod.id.startsWith("sample_pod_") && key !in remoteFeedKeys
                                }
                                if (shouldRemove) {
                                    dao.deleteEpisodesByPodcastId(localPod.id)
                                    dao.deletePodcastById(localPod.id)
                                }
                            }
                            if (isReplaceMode) {
                                val remotePlaylistIds = remoteBackup.playlists
                                    .filter { it.smartRuleType.isEmpty() }
                                    .map { it.id }
                                    .toSet()
                                dao.getCustomPlaylists().first()
                                    .filter { it.smartRuleType.isEmpty() && it.id !in remotePlaylistIds }
                                    .forEach { dao.deletePlaylist(it.id) }
                            }
                        }

                        repository.syncWithDriveRemotePayload(
                            remoteRawJson = rawJson,
                            pullOnly = isReplaceMode
                        )
                    }

                    val customPlaylistsCount = applied.mergedPlaylists.count { it.smartRuleType.isEmpty() }
                    Bundle().apply {
                        putBoolean("ok", true)
                        putInt("podcasts", applied.summary.podcastsSynced)
                        putInt("episodes", applied.mergedEpisodes.size)
                        putInt("queued", applied.summary.upNextCount)
                        putInt("playlists", customPlaylistsCount)
                    }
                } catch (t: Throwable) {
                    Bundle().apply {
                        putBoolean("ok", false)
                        putString("error", t.message ?: t.javaClass.simpleName)
                    }
                } finally {
                    incomingFile.delete()
                }
            }

            else -> Bundle().apply {
                putBoolean("ok", false)
                putString("error", "Unknown method: $method")
            }
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context?.applicationContext ?: throw FileNotFoundException("No context")
        verifyOwnerCaller(ctx)
        val repository = buildRepository(ctx)

        return when (uri.path) {
            "/snapshot" -> {
                if (mode != "r") throw SecurityException("Snapshot URI is read-only")
                val encoded = runBlocking {
                    repository.buildDriveSyncPayload()
                }
                val outFile = File(ctx.cacheDir, EXPORT_FILE_NAME)
                outFile.writeText(encoded, Charsets.UTF_8)
                ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            "/incoming" -> {
                if (!mode.startsWith("w")) throw SecurityException("Incoming URI is write-only")
                val incomingFile = File(ctx.cacheDir, INCOMING_FILE_NAME)
                ParcelFileDescriptor.open(
                    incomingFile,
                    ParcelFileDescriptor.MODE_CREATE or
                        ParcelFileDescriptor.MODE_TRUNCATE or
                        ParcelFileDescriptor.MODE_WRITE_ONLY
                )
            }

            else -> throw FileNotFoundException("Unknown path: ${uri.path}")
        }
    }

    private fun verifyOwnerCaller(ctx: Context) {
        val callingUid = Binder.getCallingUid()
        val myUid = Process.myUid()
        if (callingUid == myUid) return

        val pm = ctx.packageManager
        val packages = pm.getPackagesForUid(callingUid).orEmpty()
        if (packages.none { it in ALLOWED_CALLER_PACKAGES }) {
            throw SecurityException("Caller package is not authorized for Owner Sync")
        }
        if (pm.checkSignatures(myUid, callingUid) != PackageManager.SIGNATURE_MATCH) {
            throw SecurityException("Caller signature does not match owner signing key")
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = "application/json"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private companion object {
        val ALLOWED_CALLER_PACKAGES = setOf(
            "com.lohe.sync",
            "com.lohe.app",
            "com.lohe.app.foss",
            "com.lohe.app.preview"
        )
        const val EXPORT_FILE_NAME = "owner-sync-export.json"
        const val INCOMING_FILE_NAME = "owner-sync-incoming.json"
        const val MAX_SNAPSHOT_BYTES = 64L * 1024L * 1024L
    }
}
