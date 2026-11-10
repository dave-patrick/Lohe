package com.lohe.app.ui.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureInPictureAlt
import com.lohe.app.ui.theme.LoheDialog
import com.lohe.app.ui.theme.SettingsStore
import kotlinx.coroutines.launch

internal enum class FloatingPlayerPermissionStep { OVERLAY, NOTIFICATIONS, ENABLE }

internal fun nextFloatingPlayerPermissionStep(
    overlayAllowed: Boolean,
    notificationsAllowed: Boolean,
    notificationPromptHandled: Boolean,
): FloatingPlayerPermissionStep = when {
    !overlayAllowed -> FloatingPlayerPermissionStep.OVERLAY
    !notificationsAllowed && !notificationPromptHandled -> FloatingPlayerPermissionStep.NOTIFICATIONS
    else -> FloatingPlayerPermissionStep.ENABLE
}

/** Both entry points offer the same optional setup, only after an explicit enable action. */
@Composable
internal fun rememberFloatingPlayerPermissionFlow(
    settingsStore: SettingsStore,
    onEnabled: () -> Unit = {},
    onMessage: ((String) -> Unit)? = null,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnEnabled by rememberUpdatedState(onEnabled)
    val currentOnMessage by rememberUpdatedState(onMessage)
    val preferences = remember { context.getSharedPreferences("floating-player-setup", Context.MODE_PRIVATE) }
    var overlayPrompt by rememberSaveable { mutableStateOf(false) }
    var notificationPrompt by rememberSaveable { mutableStateOf(false) }
    val message: (String) -> Unit = { text ->
        currentOnMessage?.invoke(text) ?: Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
    val finishEnable: () -> Unit = {
        scope.launch {
            // Permission can be revoked while the notification prompt is open.
            if (Settings.canDrawOverlays(context)) {
                settingsStore.setFloatingPlayerEnabled(true)
                currentOnEnabled()
            } else message("Display over other apps was not granted. Floating controls remain off.")
        }
    }
    val notificationsAllowed = {
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val continueAfterOverlay: () -> Unit = {
        if (!notificationsAllowed() && !preferences.getBoolean("notification-prompt-handled", false)) {
            notificationPrompt = true
        } else {
            if (!notificationsAllowed()) message("Notifications are off. You can enable them in Android notification settings; floating controls still work.")
            finishEnable()
        }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) message("Notifications are off. You can hide floating controls in Lohe's Floating player settings. Audio playback is still available.")
        finishEnable()
    }
    val overlay = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) continueAfterOverlay()
        else message("Display over other apps was not granted. Floating controls remain off.")
    }

    if (overlayPrompt) LoheDialog(
        onDismiss = { overlayPrompt = false },
        title = "Show controls over other apps?",
        icon = Icons.Filled.PictureInPictureAlt,
        confirmLabel = "Open permission settings",
        onConfirm = {
            overlayPrompt = false
            runCatching { overlay.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))) }
                .onFailure { message("Open Android Settings → Apps → Special access → Display over other apps → Lohe.") }
        },
        dismissLabel = "Not now",
        onDismissAction = { overlayPrompt = false },
        body = { Text("Allow Lohe under Display over other apps to show draggable playback buttons. Lohe does not read or control other apps. This is optional; you can keep listening without floating controls.") },
    )
    if (notificationPrompt) LoheDialog(
        onDismiss = {
            notificationPrompt = false
            preferences.edit().putBoolean("notification-prompt-handled", true).apply()
            message("Notifications remain off. You can hide floating controls in Lohe's Floating player settings.")
            finishEnable()
        },
        title = "Allow floating-player notifications?",
        icon = Icons.Filled.Notifications,
        confirmLabel = "Allow notifications",
        onConfirm = {
            notificationPrompt = false
            preferences.edit().putBoolean("notification-prompt-handled", true).apply()
            runCatching { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
                .onFailure {
                    message("Notifications remain off. You can enable them in Android notification settings.")
                    finishEnable()
                }
        },
        dismissLabel = "Not now",
        onDismissAction = {
            notificationPrompt = false
            preferences.edit().putBoolean("notification-prompt-handled", true).apply()
            message("Notifications remain off. You can hide floating controls in Lohe's Floating player settings.")
            finishEnable()
        },
        body = { Text("The optional ongoing notification lets you hide floating controls without stopping your audio. Android may hide it if notifications are off. You can still listen and use floating controls without allowing notifications.") },
    )
    if (false) AlertDialog(
        onDismissRequest = { overlayPrompt = false },
        title = { Text("Show controls over other apps?") },
        text = { Text("Allow Lohe under Display over other apps to show draggable playback buttons. Lohe does not read or control other apps. This is optional; you can keep listening without floating controls.") },
        confirmButton = { TextButton(onClick = {
            overlayPrompt = false
            runCatching { overlay.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))) }
                .onFailure { message("Open Android Settings → Apps → Special access → Display over other apps → Lohe.") }
        }) { Text("Open permission settings") } },
        dismissButton = { TextButton(onClick = { overlayPrompt = false }) { Text("Not now") } },
    )
    if (notificationPrompt) AlertDialog(
        onDismissRequest = {
            notificationPrompt = false
            preferences.edit().putBoolean("notification-prompt-handled", true).apply()
            message("Notifications remain off. You can hide floating controls in Lohe's Floating player settings.")
            finishEnable()
        },
        title = { Text("Allow floating-player notifications?") },
        text = { Text("The optional ongoing notification lets you hide floating controls without stopping your audio. Android may hide it if notifications are off. You can still listen and use floating controls without allowing notifications.") },
        confirmButton = { TextButton(onClick = {
            notificationPrompt = false
            preferences.edit().putBoolean("notification-prompt-handled", true).apply()
            runCatching { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
                .onFailure {
                    message("Notifications remain off. You can enable them in Android notification settings.")
                    finishEnable()
                }
        }) { Text("Allow notifications") } },
        dismissButton = { TextButton(onClick = {
            notificationPrompt = false
            preferences.edit().putBoolean("notification-prompt-handled", true).apply()
            message("Notifications remain off. You can hide floating controls in Lohe's Floating player settings.")
            finishEnable()
        }) { Text("Not now") } },
    )

    return {
        when (nextFloatingPlayerPermissionStep(Settings.canDrawOverlays(context), notificationsAllowed(),
            preferences.getBoolean("notification-prompt-handled", false))) {
            FloatingPlayerPermissionStep.OVERLAY -> overlayPrompt = true
            FloatingPlayerPermissionStep.NOTIFICATIONS -> notificationPrompt = true
            FloatingPlayerPermissionStep.ENABLE -> continueAfterOverlay()
        }
    }
}
