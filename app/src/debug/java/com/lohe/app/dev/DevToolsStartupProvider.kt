package com.lohe.app.dev

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.lohe.app.edition.DevTools

class LoheDebugApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ensureDebugDevToolsRegistered(this)
    }
}

class DevToolsStartupProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        ensureDebugDevToolsRegistered(ctx)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}

@Synchronized
internal fun ensureDebugDevToolsRegistered(context: Context) {
    if (DevTools.current !is DebugDevTools) {
        DevTools.register(DebugDevTools(context.applicationContext ?: context))
    }
}
