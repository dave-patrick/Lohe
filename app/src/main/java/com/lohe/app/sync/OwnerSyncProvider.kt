package com.lohe.app.sync

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import com.lohe.app.BuildConfig
import com.lohe.app.data.ServiceLocator
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

/**
 * Owner-only, signature-locked ContentProvider enabling the private `com.lohe.sync`
 * companion app to sync podcasts, playlists, episode progress/played/archived state,
 * and Up Next order between installed Play (`com.lohe.app`) and FOSS (`com.lohe.app.foss`)
 * builds on the same device.
 *
 * Protected both by a `signature`-level manifest permission and defense-in-depth runtime
 * verification of the caller's UID signature and package name.
 */
class OwnerSyncProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context?.applicationContext ?: throw IllegalStateException("No context")
        verifyOwnerCaller(ctx)
        ServiceLocator.init(ctx)

        return when (method) {
            "summary" -> runBlocking {
                val summary = OwnerSyncStore.summary(
                    db = ServiceLocator.database(),
                    playlistRepository = ServiceLocator.playlistRepository(),
                )
                Bundle().apply {
                    putBoolean("ok", true)
                    putString("versionName", BuildConfig.VERSION_NAME)
                    putString("buildId", BuildConfig.BUILD_ID)
                    putString("distribution", BuildConfig.DISTRIBUTION)
                    putInt("subscribedPodcasts", summary.subscribedPodcasts)
                    putInt("totalPodcasts", summary.totalPodcasts)
                    putInt("episodes", summary.episodes)
                    putInt("played", summary.played)
                    putInt("inProgress", summary.inProgress)
                    putInt("queued", summary.queued)
                    putInt("playlists", summary.playlists)
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
                    val snapshot = OwnerSyncCodec.decodeSnapshot(rawJson)
                    val applied = runBlocking {
                        OwnerSyncStore.applySnapshot(
                            db = ServiceLocator.database(),
                            playlistRepository = ServiceLocator.playlistRepository(),
                            snapshot = snapshot,
                        )
                    }
                    Bundle().apply {
                        putBoolean("ok", true)
                        putInt("podcasts", applied.podcasts)
                        putInt("episodes", applied.episodes)
                        putInt("queued", applied.queued)
                        putInt("playlists", applied.playlists)
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
        ServiceLocator.init(ctx)

        return when (uri.path) {
            "/snapshot" -> {
                if (mode != "r") throw SecurityException("Snapshot URI is read-only")
                val snapshot = runBlocking {
                    OwnerSyncStore.exportSnapshot(
                        db = ServiceLocator.database(),
                        playlistRepository = ServiceLocator.playlistRepository(),
                        sourcePackage = ctx.packageName,
                    )
                }
                val encoded = OwnerSyncCodec.encodeSnapshot(snapshot)
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
                        ParcelFileDescriptor.MODE_WRITE_ONLY,
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
        if (ALLOWED_CALLER_PACKAGE !in packages) {
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
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = "application/json"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private companion object {
        const val ALLOWED_CALLER_PACKAGE = "com.lohe.sync"
        const val EXPORT_FILE_NAME = "owner-sync-export.json"
        const val INCOMING_FILE_NAME = "owner-sync-incoming.json"
        const val MAX_SNAPSHOT_BYTES = 64L * 1024L * 1024L
    }
}
