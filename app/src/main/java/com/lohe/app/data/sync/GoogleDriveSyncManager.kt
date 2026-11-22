package com.lohe.app.data.sync

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

sealed interface DriveAuthOutcome {
    data class Authorized(val accessToken: String) : DriveAuthOutcome
    data class NeedsResolution(val pendingIntent: PendingIntent) : DriveAuthOutcome
    data class Unavailable(val reason: String) : DriveAuthOutcome
}

class GoogleDriveSyncManager(
    context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val BACKUP_FILE_NAME = "lohe_cloud_backup.db"
        private val SQLITE_MEDIA_TYPE = "application/vnd.sqlite3".toMediaType()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val appContext = context.applicationContext

    suspend fun requestDriveAuthorization(): DriveAuthOutcome = suspendCancellableCoroutine { cont ->
        runCatching {
            val request = AuthorizationRequest.Builder()
                .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
                .build()
            Identity.getAuthorizationClient(appContext)
                .authorize(request)
                .addOnSuccessListener { result ->
                    if (!cont.isActive) return@addOnSuccessListener
                    val token = result.accessToken
                    val pendingIntent = result.pendingIntent
                    when {
                        result.hasResolution() && pendingIntent != null -> {
                            cont.resume(DriveAuthOutcome.NeedsResolution(pendingIntent))
                        }
                        !token.isNullOrBlank() -> {
                            cont.resume(DriveAuthOutcome.Authorized(token))
                        }
                        else -> {
                            cont.resume(DriveAuthOutcome.Unavailable("No Google Drive access token returned"))
                        }
                    }
                }
                .addOnFailureListener { err ->
                    if (!cont.isActive) return@addOnFailureListener
                    cont.resume(
                        DriveAuthOutcome.Unavailable(
                            err.localizedMessage ?: "Google Play Services OAuth unavailable",
                        ),
                    )
                }
        }.onFailure { err ->
            if (cont.isActive) {
                cont.resume(
                    DriveAuthOutcome.Unavailable(
                        err.localizedMessage ?: "Google Play Services OAuth unavailable",
                    ),
                )
            }
        }
    }

    fun extractTokenFromResult(resultCode: Int, data: Intent?): String? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        return runCatching {
            Identity.getAuthorizationClient(appContext)
                .getAuthorizationResultFromIntent(data)
                .accessToken
        }.getOrNull()
    }

    suspend fun uploadDatabaseBackup(accessToken: String, dbFile: File): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val kb = (dbFile.length() + 1023L) / 1024L
                val existingFileId = findExistingBackupFileId(accessToken)
                if (existingFileId != null) {
                    val patchReq = Request.Builder()
                        .url("https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media")
                        .header("Authorization", "Bearer $accessToken")
                        .patch(dbFile.asRequestBody(SQLITE_MEDIA_TYPE))
                        .build()
                    httpClient.newCall(patchReq).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("Drive update HTTP ${resp.code}")
                        }
                    }
                    "Updated Google Drive backup ($kb KB)"
                } else {
                    val metadataJson = JSONObject().apply {
                        put("name", BACKUP_FILE_NAME)
                        put("parents", JSONArray(listOf("appDataFolder")))
                        put("mimeType", "application/vnd.sqlite3")
                    }.toString()
                    val multipartBody = MultipartBody.Builder()
                        .setType("multipart/related".toMediaType())
                        .addPart(metadataJson.toRequestBody(JSON_MEDIA_TYPE))
                        .addPart(dbFile.asRequestBody(SQLITE_MEDIA_TYPE))
                        .build()

                    val createReq = Request.Builder()
                        .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                        .header("Authorization", "Bearer $accessToken")
                        .post(multipartBody)
                        .build()
                    httpClient.newCall(createReq).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("Drive create HTTP ${resp.code}")
                        }
                    }
                    "Created Google Drive backup ($kb KB)"
                }
            }
        }

    suspend fun downloadDatabaseBackup(accessToken: String, destinationFile: File): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fileId = findExistingBackupFileId(accessToken) ?: return@runCatching false
                val getReq = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                httpClient.newCall(getReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Drive download HTTP ${resp.code}")
                    }
                    val body = resp.body ?: throw IllegalStateException("Empty Drive response")
                    body.byteStream().use { input ->
                        destinationFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                true
            }
        }

    private fun findExistingBackupFileId(accessToken: String): String? {
        val searchUrl =
            "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name%3D%27$BACKUP_FILE_NAME%27&fields=files(id,name,modifiedTime)"
        val req = Request.Builder()
            .url(searchUrl)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Drive query HTTP ${resp.code}")
            }
            val raw = resp.body?.string().orEmpty()
            val files = JSONObject(raw).optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            return files.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
        }
    }
}
