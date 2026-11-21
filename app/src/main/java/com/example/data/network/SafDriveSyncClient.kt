package com.example.data.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads and writes Lohe's Drive sync JSON directly via a persisted Android Storage Access Framework
 * document URI provided by OneDrive, Dropbox, Box, pCloud, Nextcloud Files, Syncthing, etc.
 */
class SafDriveSyncClient(context: Context) {

    private val appContext = context.applicationContext

    suspend fun readSyncFile(uri: Uri): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            appContext.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                reader.readText().takeIf { it.isNotBlank() }
            }
        }
    }

    suspend fun writeSyncFile(uri: Uri, jsonPayload: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = runCatching {
                appContext.contentResolver.openOutputStream(uri, "wt")
            }.getOrNull() ?: appContext.contentResolver.openOutputStream(uri, "w")
            ?: throw IllegalStateException("Cloud drive file is not writable. Re-link the Drive sync file.")

            stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }
            queryDisplayName(uri)
        }
    }

    fun queryDisplayName(uri: Uri): String {
        runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "lohe_cloud_sync.json" }
            ?: "lohe_cloud_sync.json"
    }
}
