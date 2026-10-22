package com.lohe.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lohe.app.BuildConfig

/**
 * The version of the running build, as a staging mark.
 *
 * It is only ever a mark on a build that is still being worked on: a release
 * build has nothing to announce, and a version number sitting next to the
 * wordmark in the shipped app would be noise pretending to be information.
 * So the mark is null in a release build and the screens that carry it simply
 * leave the space empty.
 *
 * The version is read from the installed package rather than from
 * [BuildConfig.VERSION_NAME], so the mark cannot drift from the APK it is
 * printed on.
 */
@Composable
fun rememberStagingMark(): String? {
    if (!BuildConfig.DEBUG) return null
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.let { "v$it" }
    }
}
