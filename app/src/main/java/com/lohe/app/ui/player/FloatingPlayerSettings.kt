package com.lohe.app.ui.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lohe.app.ui.theme.SettingsStore
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingPlayerSettings(enabled: Boolean, sizeDp: Int, opacity: Float, settingsStore: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val power = remember { context.getSystemService(PowerManager::class.java) }
    val preferences = remember { context.getSharedPreferences("floating-player-setup", android.content.Context.MODE_PRIVATE) }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var batteryUnrestricted by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    var batteryPrompt by remember { mutableStateOf(false) }
    var permissionPrompt by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var transparency by remember(opacity) { mutableFloatStateOf(1f - opacity) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayAllowed = Settings.canDrawOverlays(context)
                batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val offerBatterySetup = {
        if (!power.isIgnoringBatteryOptimizations(context.packageName) && !preferences.getBoolean("battery-prompt-shown", false)) {
            preferences.edit().putBoolean("battery-prompt-shown", true).apply()
            batteryPrompt = true
        }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { offerBatterySetup() }
    val enablePlayer = {
        message = null
        scope.launch { settingsStore.setFloatingPlayerEnabled(true) }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else offerBatterySetup()
    }
    val overlayPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        overlayAllowed = Settings.canDrawOverlays(context)
        if (overlayAllowed) enablePlayer() else message = "Permission was not granted. Floating controls remain off."
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Floating player over other apps", style = MaterialTheme.typography.bodyLarge)
            Text("Drag to move. Double-tap play/pause to expand; use the chevron to collapse. Hold to close.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled && overlayAllowed, onCheckedChange = { checked ->
            if (!checked) scope.launch { settingsStore.setFloatingPlayerEnabled(false) }
            else if (overlayAllowed) enablePlayer()
            else permissionPrompt = true
        })
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    Text("Uses Display over other apps permission, not an accessibility service. The notification can hide controls without stopping your audio.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Control size", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(48 to "Small", 56 to "Medium", 72 to "Large", 88 to "Extra large").forEach { (size, label) ->
            FilterChip(selected = sizeDp == size,
                onClick = { scope.launch { settingsStore.setFloatingPlayerSizeDp(size) } },
                label = { Text(label) })
        }
    }
    Text("Transparency: ${(transparency * 100).roundToInt()}%", style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 12.dp))
    Slider(value = transparency, valueRange = 0f..0.75f, steps = 14,
        modifier = Modifier.semantics {
            contentDescription = "Floating player transparency"
            stateDescription = "${(transparency * 100).roundToInt()}% transparent"
        },
        onValueChange = { transparency = it },
        onValueChangeFinished = { scope.launch { settingsStore.setFloatingPlayerOpacity(1f - transparency) } })
    Text("0% is solid; 75% is mostly transparent. Controls stay visible and tappable.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = { batteryPrompt = true }) {
        Text(if (batteryUnrestricted) "Battery optimization is off" else "Battery optimization setup")
    }

    if (permissionPrompt) AlertDialog(
        onDismissRequest = { permissionPrompt = false },
        title = { Text("Show controls over other apps?") },
        text = { Text("Allow Lohe under Display over other apps. This only displays your playback buttons; it does not read or control other apps.") },
        confirmButton = { Button(onClick = {
            permissionPrompt = false
            runCatching { overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                .onFailure { message = "Open Android Settings → Apps → Special access → Display over other apps → Lohe." }
        }) { Text("Open permission settings") } },
        dismissButton = { TextButton(onClick = { permissionPrompt = false }) { Text("Not now") } },
    )
    if (batteryPrompt) AlertDialog(
        onDismissRequest = { batteryPrompt = false },
        title = { Text("Keep floating controls available") },
        text = { Text("Android may stop floating controls to save power. In battery settings, select All apps, find Lohe, and choose Don't optimize or Unrestricted. This is optional and may use more battery.") },
        confirmButton = { Button(onClick = {
            batteryPrompt = false
            runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                .onFailure { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
        }) { Text("Open battery settings") } },
        dismissButton = { TextButton(onClick = { batteryPrompt = false }) { Text("Not now") } },
    )
}
