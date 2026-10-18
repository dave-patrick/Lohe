package com.lohe.app.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * Walks a Storage Access Framework tree and collects audio documents.
 *
 * The SAF tree is walked through [DocumentFile] rather than MediaStore because
 * a picked tree is exactly what the user granted us — MediaStore would hand
 * back the entire music library, ignoring the selection.
 */
object FileScanner {

    /** Extensions we treat as playable audio when a provider reports no MIME type. */
    val AUDIO_EXTENSIONS = listOf(
        "mp3", "m4a", "mp4", "aac", "ogg", "oga", "opus", "flac", "wav", "m4b",
    )

    /** Container formats ExoPlayer decodes reliably. */
    val SUPPORTED_EXTENSIONS = listOf("mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "wav")

    /** Recursively collect audio [Uri]s beneath [treeUri]. */
    fun scanFolderForAudio(context: Context, treeUri: Uri): List<Uri> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val out = mutableListOf<Uri>()
        collect(root, out)
        return out
    }

    private fun collect(dir: DocumentFile, out: MutableList<Uri>) {
        for (child in dir.listFiles()) {
            when {
                child.isDirectory -> collect(child, out)
                child.isFile && isAudio(child) -> out += child.uri
            }
        }
    }

    private fun isAudio(file: DocumentFile): Boolean {
        val type = file.type?.lowercase()
        if (type != null && type.startsWith("audio/")) return true
        if (type != null && type == "application/octet-stream") {
            // Some providers report octet-stream; fall back to the extension.
            return extensionOf(file.name) in AUDIO_EXTENSIONS
        }
        return extensionOf(file.name) in AUDIO_EXTENSIONS
    }

    private fun extensionOf(name: String?): String = lowerExtensionOf(name)

    /** Lower-cased extension without the dot, or "" when there is none. */
    fun lowerExtensionOf(name: String?): String =
        name?.substringAfterLast('.', "")?.lowercase().orEmpty()

    /** Human-readable label for a picked tree, used as the pseudo-podcast title. */
    fun displayNameOf(context: Context, treeUri: Uri): String? = runCatching {
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        val parts = id.split(':')
        when (parts.size) {
            2 -> parts[1].ifBlank { null }
            else -> null
        }
    }.getOrNull() ?: context.contentResolver.query(
        treeUri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null, null, null,
    )?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}
