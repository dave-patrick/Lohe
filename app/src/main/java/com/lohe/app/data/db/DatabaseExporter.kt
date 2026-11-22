package com.lohe.app.data.db

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * Exports and restores self-contained copies of Lohe's Room SQLite database (`lohe.db`).
 */
object DatabaseExporter {

    private const val DATABASE_NAME = "lohe.db"
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    suspend fun exportToUri(context: Context, destination: Uri): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val appContext = context.applicationContext
                val sourceFile = checkpointAndGetDatabaseFile(appContext)
                appContext.contentResolver.openOutputStream(destination)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                } ?: error("Could not open destination output stream")
            }
        }

    suspend fun createSnapshotFile(context: Context): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val appContext = context.applicationContext
                val sourceFile = checkpointAndGetDatabaseFile(appContext)
                val snapshot = File.createTempFile("lohe_sync_snapshot_", ".db", appContext.cacheDir)
                sourceFile.inputStream().use { input ->
                    snapshot.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                snapshot
            }
        }

    suspend fun restoreFromFile(context: Context, backupFile: File): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val appContext = context.applicationContext
                if (!backupFile.exists() || backupFile.length() < SQLITE_MAGIC.size) {
                    throw IllegalArgumentException("Downloaded backup is empty or invalid")
                }
                val header = ByteArray(SQLITE_MAGIC.size)
                backupFile.inputStream().use { input ->
                    val read = input.read(header)
                    if (read != SQLITE_MAGIC.size || !header.contentEquals(SQLITE_MAGIC)) {
                        throw IllegalArgumentException("Backup file is not a valid SQLite database")
                    }
                }

                AppDatabase.closeForRestore()

                val targetFile = appContext.getDatabasePath(DATABASE_NAME)
                targetFile.parentFile?.mkdirs()
                File(targetFile.path + "-wal").delete()
                File(targetFile.path + "-shm").delete()

                val bytes = backupFile.inputStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                bytes
            }
        }

    private fun checkpointAndGetDatabaseFile(appContext: Context): File {
        val db = AppDatabase.get(appContext)
        // Flush pending WAL frames into the main database file before copying.
        db.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)")
            .use { cursor -> cursor.moveToFirst() }

        val sourceFile = appContext.getDatabasePath(DATABASE_NAME)
        if (!sourceFile.exists() || !sourceFile.isFile) {
            throw FileNotFoundException("Database file not found: $DATABASE_NAME")
        }
        return sourceFile
    }
}
