package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Direct HTTPS WebDAV cloud sync client supporting Nextcloud, ownCloud, Koofr,
 * pCloud WebDAV, Box WebDAV, Synology NAS, and any standard WebDAV server.
 */
class WebDavCloudSyncClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val BACKUP_FILENAME = "lohe_cloud_backup.json"
    }

    private fun resolveBackupUrl(serverUrl: String): String {
        val trimmed = serverUrl.trim()
        require(trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            "WebDAV server URL must start with https://"
        }
        return if (trimmed.endsWith(".json", ignoreCase = true)) {
            trimmed
        } else {
            "${trimmed.trimEnd('/')}/$BACKUP_FILENAME"
        }
    }

    suspend fun uploadBackup(
        serverUrl: String,
        username: String,
        password: String,
        jsonPayload: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val targetUrl = resolveBackupUrl(serverUrl)
            val parentFolderUrl = targetUrl.substringBeforeLast('/', "")
            if (parentFolderUrl.startsWith("https://") || parentFolderUrl.startsWith("http://")) {
                val mkcolBuilder = Request.Builder()
                    .url(parentFolderUrl)
                    .method("MKCOL", null)
                if (username.isNotBlank()) {
                    mkcolBuilder.header("Authorization", Credentials.basic(username.trim(), password))
                }
                runCatching { httpClient.newCall(mkcolBuilder.build()).execute().close() }
            }
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .put(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            if (username.isNotBlank()) {
                requestBuilder.header("Authorization", Credentials.basic(username.trim(), password))
            }
            httpClient.newCall(requestBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 201 && resp.code != 204) {
                    throw IllegalStateException("WebDAV upload failed (HTTP ${resp.code})")
                }
            }
            "Synced library to WebDAV ($BACKUP_FILENAME)"
        }
    }

    suspend fun downloadBackup(
        serverUrl: String,
        username: String,
        password: String
    ): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val targetUrl = resolveBackupUrl(serverUrl)
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .get()
            if (username.isNotBlank()) {
                requestBuilder.header("Authorization", Credentials.basic(username.trim(), password))
            }
            httpClient.newCall(requestBuilder.build()).execute().use { resp ->
                if (resp.code == 404) {
                    return@runCatching null
                }
                if (!resp.isSuccessful) {
                    throw IllegalStateException("WebDAV restore failed (HTTP ${resp.code})")
                }
                resp.body?.string()
            }
        }
    }
}
