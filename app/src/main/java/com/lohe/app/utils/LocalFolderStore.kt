package com.lohe.app.utils

import android.content.Context
import android.net.Uri

/**
 * Remembers the folder the listener drops local podcast files into.
 *
 * The folder is a SAF tree, so the permission is taken persistably and the
 * address is kept here. The folder is rescanned on every launch, which is
 * what lets a file dropped into it show up in the library without anyone
 * having to ask for it.
 */
class LocalFolderStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The tree address, or null when no folder has been set. */
    var treeUri: String?
        get() = prefs.getString(KEY_URI, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_URI) else putString(KEY_URI, value)
        }.apply()

    /** The folder's name, for the pseudo-podcast the files are filed under. */
    var folderName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_NAME) else putString(KEY_NAME, value)
        }.apply()

    /** The stored folder as a tree address, or null when there is none. */
    fun tree(): Uri? = treeUri?.let { runCatching { Uri.parse(it) }.getOrNull() }

    /** Whether a folder has been set. */
    val isSet: Boolean get() = treeUri != null

    /** Forgets the folder. The files themselves are left alone. */
    fun clear() {
        prefs.edit().remove(KEY_URI).remove(KEY_NAME).apply()
    }

    private companion object {
        const val PREFS = "local_folder"
        const val KEY_URI = "tree_uri"
        const val KEY_NAME = "name"
    }
}
