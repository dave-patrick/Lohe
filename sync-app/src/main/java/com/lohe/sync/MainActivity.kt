package com.lohe.sync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LoheSyncColors = darkColorScheme(
    primary = Color(0xFF008EFF),
    onPrimary = Color.White,
    secondary = Color(0xFF38BDF8),
    background = Color(0xFF0A0E17),
    surface = Color(0xFF131A29),
    surfaceVariant = Color(0xFF1B2438),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFF87171),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = LoheSyncColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    LoheSyncScreen()
                }
            }
        }
    }
}

private enum class PendingAction { PLAY_TO_FOSS, FOSS_TO_PLAY, SMART_MERGE }

@Composable
private fun LoheSyncScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var playStatus by remember { mutableStateOf(EditionStatus(Edition.PLAY)) }
    var fossStatus by remember { mutableStateOf(EditionStatus(Edition.FOSS)) }
    var busy by remember { mutableStateOf(false) }
    var mergePrimary by remember { mutableStateOf(Edition.PLAY) }
    var lastOutcome by remember { mutableStateOf<SyncOutcome?>(null) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }

    fun refresh() {
        scope.launch {
            busy = true
            val (p, f) = withContext(Dispatchers.IO) {
                SyncBridge.inspect(context, Edition.PLAY) to SyncBridge.inspect(context, Edition.FOSS)
            }
            playStatus = p
            fossStatus = f
            busy = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val bothReady = playStatus.accessible && fossStatus.accessible && !busy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Lohe Sync",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Owner-only Play ↔ FOSS library sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { if (!busy) refresh() }, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh status")
                }
            }
        }

        EditionCard(status = playStatus)
        EditionCard(status = fossStatus)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Sync Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Syncs podcasts, playlists, episode played/progress/archive/star status, and Up Next order. Local downloaded files stay untouched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = { pendingAction = PendingAction.SMART_MERGE },
                    enabled = bothReady,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Smart Two-Way Merge")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Up Next winner:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilterChip(
                        selected = mergePrimary == Edition.PLAY,
                        onClick = { mergePrimary = Edition.PLAY },
                        label = { Text("Play") },
                    )
                    FilterChip(
                        selected = mergePrimary == Edition.FOSS,
                        onClick = { mergePrimary = Edition.FOSS },
                        label = { Text("FOSS") },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { pendingAction = PendingAction.PLAY_TO_FOSS },
                        enabled = bothReady,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Play")
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.padding(horizontal = 4.dp).size(16.dp),
                        )
                        Text("FOSS")
                    }
                    OutlinedButton(
                        onClick = { pendingAction = PendingAction.FOSS_TO_PLAY },
                        enabled = bothReady,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("FOSS")
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.padding(horizontal = 4.dp).size(16.dp),
                        )
                        Text("Play")
                    }
                }
            }
        }

        lastOutcome?.let { outcome ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (outcome.ok) Color(0xFF0E2A22) else Color(0xFF2D1518),
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = if (outcome.ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (outcome.ok) Color(0xFF34D399) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = outcome.summary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (outcome.detail.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = outcome.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingAction?.let { action ->
        val (title, body) = when (action) {
            PendingAction.PLAY_TO_FOSS ->
                "Copy Play → FOSS?" to "Overwrites FOSS podcasts, playlists, episode status, and Up Next with Google Play's current state. FOSS downloaded files are preserved."
            PendingAction.FOSS_TO_PLAY ->
                "Copy FOSS → Play?" to "Overwrites Google Play podcasts, playlists, episode status, and Up Next with FOSS's current state. Play downloaded files are preserved."
            PendingAction.SMART_MERGE ->
                "Run Smart Two-Way Merge?" to "Merges podcasts, playlists, and newest per-episode playback/archive status across both apps, using ${mergePrimary.label}'s Up Next order, then writes the merged state to both."
        }
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingAction = null
                        scope.launch {
                            busy = true
                            val outcome = withContext(Dispatchers.IO) {
                                when (action) {
                                    PendingAction.PLAY_TO_FOSS -> SyncBridge.copyOneWay(context, Edition.PLAY, Edition.FOSS)
                                    PendingAction.FOSS_TO_PLAY -> SyncBridge.copyOneWay(context, Edition.FOSS, Edition.PLAY)
                                    PendingAction.SMART_MERGE -> SyncBridge.smartMerge(context, mergePrimary)
                                }
                            }
                            lastOutcome = outcome
                            val (p, f) = withContext(Dispatchers.IO) {
                                SyncBridge.inspect(context, Edition.PLAY) to SyncBridge.inspect(context, Edition.FOSS)
                            }
                            playStatus = p
                            fossStatus = f
                            busy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Sync now")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun EditionCard(status: EditionStatus) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = status.edition.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = status.edition.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val badgeText = when {
                    status.accessible -> "v${status.versionName}"
                    status.installed -> "Locked"
                    else -> "Missing"
                }
                val badgeColor = when {
                    status.accessible -> Color(0xFF34D399)
                    else -> MaterialTheme.colorScheme.error
                }
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeColor,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (status.accessible) {
                Text(
                    text = "${status.subscribedPodcasts} shows · ${status.episodes} episodes (${status.played} played, ${status.inProgress} in progress)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${status.queued} in Up Next · ${status.playlists} playlists",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Text(
                    text = status.error ?: "Unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
