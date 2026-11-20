package com.lohe.app.data.db

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/**
 * Exports a self-contained copy of Lohe's Room SQLite database (`lohe.db`) to a
 * user-selected Storage Access Framework document URI.
 */
object DatabaseExporter {

    private const val DATABASE_NAME = "lohe.db"

    suspend fun exportToUri(context: Context, destination: Uri): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val appContext = context.applicationContext
                val db = AppDatabase.get(appContext)
                // Flush pending WAL frames into the main database file before copying.
                db.openHelper.writableDatabase
                    .query("PRAGMA wal_checkpoint(TRUNCATE)")
                    .use { cursor -> cursor.moveToFirst() }

                val sourceFile = appContext.getDatabasePath(DATABASE_NAME)
                if (!sourceFile.exists() || !sourceFile.isFile) {
                    throw FileNotFoundException("Database file not found: $DATABASE_NAME")
                }

                appContext.contentResolver.openOutputStream(destination)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                } ?: error("Could not open destination output stream")
            }
        }
}
