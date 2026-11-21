package com.lohe.sync

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream

data class EditionStatus(
    val edition: Edition,
    val installed: Boolean = false,
    val accessible: Boolean = false,
    val error: String? = null,
    val versionName: String = "",
    val buildId: String = "",
    val subscribedPodcasts: Int = 0,
    val totalPodcasts: Int = 0,
    val episodes: Int = 0,
    val played: Int = 0,
    val inProgress: Int = 0,
    val queued: Int = 0,
    val playlists: Int = 0,
)

data class SyncOutcome(
    val ok: Boolean,
    val summary: String,
    val detail: String = "",
)

/**
 * IPC bridge talking to `OwnerSyncProvider` in `com.lohe.app` (Play) and
 * `com.lohe.app.foss` (FOSS) via Android's signature-protected ContentProvider API.
 */
object SyncBridge {

    fun inspect(context: Context, edition: Edition): EditionStatus {
        val installed = isPackageInstalled(context, edition.packageName)
        if (!installed) {
            return EditionStatus(edition = edition, installed = false, error = "Not installed")
        }
        val providerUri = Uri.parse("content://${edition.authority}")
        return try {
            val bundle = context.contentResolver.call(providerUri, "summary", null, null)
                ?: return EditionStatus(
                    edition = edition,
                    installed = true,
                    error = "Installed build does not include Owner Sync yet",
                )
            EditionStatus(
                edition = edition,
                installed = true,
                accessible = true,
                versionName = bundle.getString("versionName").orEmpty(),
                buildId = bundle.getString("buildId").orEmpty(),
                subscribedPodcasts = bundle.getInt("subscribedPodcasts", 0),
                totalPodcasts = bundle.getInt("totalPodcasts", 0),
                episodes = bundle.getInt("episodes", 0),
                played = bundle.getInt("played", 0),
                inProgress = bundle.getInt("inProgress", 0),
                queued = bundle.getInt("queued", 0),
                playlists = bundle.getInt("playlists", 0),
            )
        } catch (_: SecurityException) {
            EditionStatus(
                edition = edition,
                installed = true,
                error = "Signature mismatch — sign Lohe Sync with the same keystore",
            )
        } catch (t: Throwable) {
            EditionStatus(
                edition = edition,
                installed = true,
                error = t.message ?: t.javaClass.simpleName,
            )
        }
    }

    fun copyOneWay(context: Context, source: Edition, target: Edition): SyncOutcome {
        return try {
            val snapshotBytes = readSnapshotBytes(context, source)
            val result = writeAndApply(context, target, snapshotBytes)
            if (result.ok) {
                SyncOutcome(
                    ok = true,
                    summary = "${source.label} → ${target.label} complete",
                    detail = result.detail,
                )
            } else {
                result
            }
        } catch (t: Throwable) {
            SyncOutcome(ok = false, summary = "Sync failed", detail = t.message ?: t.javaClass.simpleName)
        }
    }

    fun smartMerge(context: Context, primary: Edition): SyncOutcome {
        return try {
            val playJson = readSnapshotBytes(context, Edition.PLAY).toString(Charsets.UTF_8)
            val fossJson = readSnapshotBytes(context, Edition.FOSS).toString(Charsets.UTF_8)
            val mergedBytes = SnapshotMerger.merge(playJson, fossJson, primary).toByteArray(Charsets.UTF_8)

            val playResult = writeAndApply(context, Edition.PLAY, mergedBytes)
            if (!playResult.ok) {
                return SyncOutcome(ok = false, summary = "Failed writing merged state to Play", detail = playResult.detail)
            }
            val fossResult = writeAndApply(context, Edition.FOSS, mergedBytes)
            if (!fossResult.ok) {
                return SyncOutcome(ok = false, summary = "Play updated, but failed writing to FOSS", detail = fossResult.detail)
            }
            SyncOutcome(
                ok = true,
                summary = "Two-way merge complete (Up Next: ${primary.label})",
                detail = playResult.detail,
            )
        } catch (t: Throwable) {
            SyncOutcome(ok = false, summary = "Merge failed", detail = t.message ?: t.javaClass.simpleName)
        }
    }

    private fun readSnapshotBytes(context: Context, edition: Edition): ByteArray {
        val uri = Uri.parse("content://${edition.authority}/snapshot")
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not open snapshot stream from ${edition.label}")
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            stream.copyTo(out)
            out.toByteArray()
        }
    }

    private fun writeAndApply(context: Context, target: Edition, snapshotBytes: ByteArray): SyncOutcome {
        val incomingUri = Uri.parse("content://${target.authority}/incoming")
        val output = context.contentResolver.openOutputStream(incomingUri, "wt")
            ?: error("Could not open incoming stream on ${target.label}")
        output.use { stream ->
            stream.write(snapshotBytes)
            stream.flush()
        }
        val providerUri = Uri.parse("content://${target.authority}")
        val result = context.contentResolver.call(providerUri, "apply_incoming", "replace", null)
            ?: error("${target.label} did not respond to apply_incoming")
        val ok = result.getBoolean("ok", false)
        if (!ok) {
            return SyncOutcome(
                ok = false,
                summary = "${target.label} rejected snapshot",
                detail = result.getString("error") ?: "Unknown error",
            )
        }
        val podcasts = result.getInt("podcasts", 0)
        val episodes = result.getInt("episodes", 0)
        val queued = result.getInt("queued", 0)
        val playlists = result.getInt("playlists", 0)
        return SyncOutcome(
            ok = true,
            summary = "Applied to ${target.label}",
            detail = "$podcasts podcasts · $episodes episodes · $queued Up Next · $playlists playlists",
        )
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}
