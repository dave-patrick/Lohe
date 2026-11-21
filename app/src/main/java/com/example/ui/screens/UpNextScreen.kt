package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.EpisodeEntity
import com.example.data.local.LoheUserSettings
import com.example.playback.PlaybackUiState
import com.example.ui.components.EpisodeBatchAction
import com.example.ui.components.EpisodeMultiSelectTopBar
import com.example.ui.components.PodcastArtworkThumbnail
import com.example.ui.components.PrimaryTabTopBar
import com.example.ui.components.QueueIndicatorBadge
import com.example.ui.components.SwipeableUpNextRow
import com.example.ui.components.UpNextMultiSelectActionBar
import com.example.ui.components.formatDurationCompactHuman
import com.example.ui.theme.LoheBackgroundDark
import com.example.ui.theme.LoheBorderDark
import com.example.ui.theme.LoheBrightBlueText
import com.example.ui.theme.LoheCyanAccent
import com.example.ui.theme.LoheElectricBlue
import com.example.ui.theme.LoheSurfaceCardDark

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UpNextTabScreen(
    settings: LoheUserSettings,
    upNextEpisodes: List<EpisodeEntity>,
    playbackState: PlaybackUiState,
    onOpenSettings: () -> Unit,
    onBrowsePodcasts: () -> Unit,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onMoveEpisode: (EpisodeEntity, Boolean) -> Unit,
    onMoveEpisodeEdge: (EpisodeEntity, Boolean) -> Unit,
    onMarkPlayed: (EpisodeEntity, Boolean) -> Unit,
    onRemoveFromQueue: (EpisodeEntity) -> Unit,
    onClearQueue: () -> Unit,
    onOpenNotes: (EpisodeEntity) -> Unit = {},
    onBatchUpNextAction: (List<EpisodeEntity>, EpisodeBatchAction) -> Unit = { _, _ -> }
) {
    val visibleUpNext = remember(upNextEpisodes) {
        upNextEpisodes.filter { !it.isPlayed }
    }
    val remainingMs = remember(visibleUpNext) {
        visibleUpNext.sumOf { (it.durationMs - it.resumePositionMs).coerceAtLeast(0L) }
    }
    var actionTargetId by remember { mutableStateOf<String?>(null) }
    var showQueueMenu by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedEpisodeIds by remember { mutableStateOf(setOf<String>()) }
    val haptic = LocalHapticFeedback.current

    val activeSelectedIds = remember(selectedEpisodeIds, visibleUpNext) {
        val valid = visibleUpNext.map { it.id }.toSet()
        selectedEpisodeIds.filterTo(LinkedHashSet()) { it in valid }
    }
    val selectedEpisodes = remember(activeSelectedIds, visibleUpNext) {
        visibleUpNext.filter { it.id in activeSelectedIds }
    }

    BackHandler(enabled = isMultiSelectMode) {
        isMultiSelectMode = false
        selectedEpisodeIds = emptySet()
    }

    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val cardBg = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
    val borderColor = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("up_next_tab_screen")
    ) {
        if (isMultiSelectMode) {
            EpisodeMultiSelectTopBar(
                selectedCount = activeSelectedIds.size,
                totalCount = visibleUpNext.size,
                onClose = {
                    isMultiSelectMode = false
                    selectedEpisodeIds = emptySet()
                },
                onToggleSelectAll = {
                    selectedEpisodeIds = if (activeSelectedIds.size >= visibleUpNext.size) {
                        emptySet()
                    } else {
                        visibleUpNext.map { it.id }.toSet()
                    }
                }
            )
        } else {
            PrimaryTabTopBar(
                title = stringResource(id = R.string.tab_up_next),
                subtitle = "${visibleUpNext.size} episodes next • ${formatDurationCompactHuman(remainingMs)} remaining",
                trailingActions = {
                    if (visibleUpNext.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                actionTargetId = null
                                isMultiSelectMode = true
                                selectedEpisodeIds = emptySet()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("up_next_multiselect_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Checklist,
                                contentDescription = "Select Multiple Episodes",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showQueueMenu = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("up_next_more_menu")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Queue Options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showQueueMenu,
                                onDismissRequest = { showQueueMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Play First Episode") },
                                    onClick = {
                                        showQueueMenu = false
                                        visibleUpNext.firstOrNull()?.let(onPlayEpisode)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Select Episodes") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Checklist,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showQueueMenu = false
                                        actionTargetId = null
                                        isMultiSelectMode = true
                                        selectedEpisodeIds = emptySet()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear Queue", color = Color(0xFFEF4444)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteSweep,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showQueueMenu = false
                                        showClearConfirmDialog = true
                                    },
                                    modifier = Modifier.testTag("clear_queue_menu_item")
                                )
                            }
                        }
                    }
                },
                onOpenSettings = onOpenSettings
            )
        }

        if (visibleUpNext.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 140.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = cardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderColor, RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Podcasts, contentDescription = null, tint = LoheElectricBlue, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Your Up Next queue is empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Queue episodes from any podcast or playlist to line up your listening session.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = onBrowsePodcasts,
                            colors = ButtonDefaults.buttonColors(containerColor = LoheElectricBlue)
                        ) {
                            Text("Browse Podcasts", color = Color.White)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = if (isMultiSelectMode) 12.dp else 24.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                itemsIndexed(visibleUpNext, key = { _, ep -> ep.id }) { idx, ep ->
                    val isPlayingEp = playbackState.currentEpisode?.id == ep.id && playbackState.isPlaying
                    val isExpanded = !isMultiSelectMode && actionTargetId == ep.id
                    val isSelected = ep.id in activeSelectedIds
                    SwipeableUpNextRow(
                        episode = ep,
                        onMoveToTop = {
                            actionTargetId = null
                            onMoveEpisodeEdge(ep, true)
                        },
                        onMoveToBottom = {
                            actionTargetId = null
                            onMoveEpisodeEdge(ep, false)
                        },
                        onMarkPlayed = {
                            actionTargetId = null
                            onMarkPlayed(ep, true)
                        },
                        onRemoveFromQueue = {
                            actionTargetId = null
                            onRemoveFromQueue(ep)
                        },
                        cornerRadius = 16.dp,
                        enabled = !isMultiSelectMode
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) LoheElectricBlue.copy(alpha = if (isDark) 0.16f else 0.10f) else cardBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected || isPlayingEp) LoheElectricBlue else borderColor,
                                    RoundedCornerShape(16.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            selectedEpisodeIds = if (isSelected) {
                                                selectedEpisodeIds - ep.id
                                            } else {
                                                selectedEpisodeIds + ep.id
                                            }
                                        } else {
                                            onOpenNotes(ep)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        actionTargetId = null
                                        isMultiSelectMode = true
                                        selectedEpisodeIds = selectedEpisodeIds + ep.id
                                    }
                                )
                                .testTag("up_next_item_${ep.id}")
                        ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isMultiSelectMode) {
                                    Box(
                                        modifier = Modifier.size(36.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                            contentDescription = if (isSelected) "Selected" else "Not selected",
                                            tint = if (isSelected) LoheElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(22.dp)
                                                .testTag("up_next_select_icon_${ep.id}")
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = { actionTargetId = if (isExpanded) null else ep.id },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("up_next_handle_${ep.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DragHandle,
                                            contentDescription = "Reorder or Actions",
                                            tint = if (isExpanded) LoheCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                PodcastArtworkThumbnail(
                                    artworkUrl = ep.artworkUrl,
                                    title = ep.podcastTitle,
                                    cornerRadius = 10.dp,
                                    modifier = Modifier.size(56.dp)
                                )

                                Spacer(Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        QueueIndicatorBadge(settings = settings, queuePosition = idx + 1)
                                        Text(
                                            text = ep.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = buildAnnotatedString {
                                            append("${formatEpisodeDate(ep.publishedAt)} • ")
                                            withStyle(SpanStyle(color = LoheBrightBlueText, fontWeight = FontWeight.SemiBold)) {
                                                append(formatDurationCompactHuman(ep.durationMs))
                                            }
                                            append(" • ${ep.podcastTitle}")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (ep.durationMs > 0L && ep.resumePositionMs > 0L) {
                                        Spacer(Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { (ep.resumePositionMs.toFloat() / ep.durationMs.toFloat()).coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(2.5.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = LoheElectricBlue
                                        )
                                    }
                                }

                                if (!isMultiSelectMode) {
                                    IconButton(
                                        onClick = { onMarkPlayed(ep, true) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Mark Played",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onPlayEpisode(ep) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingEp) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = "Play",
                                            tint = LoheElectricBlue
                                        )
                                    }
                                }
                            }

                            // Non-overlapping clean inline action strip below card row
                            AnimatedVisibility(visible = isExpanded) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(LoheElectricBlue.copy(alpha = 0.16f))
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    QueueStripAction("Cancel", Icons.Filled.Close, MaterialTheme.colorScheme.onSurface) {
                                        actionTargetId = null
                                    }
                                    QueueStripAction("Notes", Icons.Filled.Description, LoheBrightBlueText) {
                                        actionTargetId = null
                                        onOpenNotes(ep)
                                    }
                                    QueueStripAction("Top", Icons.Filled.KeyboardDoubleArrowUp, LoheBrightBlueText) {
                                        onMoveEpisodeEdge(ep, true)
                                        actionTargetId = null
                                    }
                                    QueueStripAction("End", Icons.Filled.KeyboardDoubleArrowDown, LoheBrightBlueText) {
                                        onMoveEpisodeEdge(ep, false)
                                        actionTargetId = null
                                    }
                                    QueueStripAction("Up", Icons.Filled.ArrowUpward, LoheCyanAccent, enabled = idx > 0) {
                                        onMoveEpisode(ep, true)
                                    }
                                    QueueStripAction("Down", Icons.Filled.ArrowDownward, LoheCyanAccent, enabled = idx < visibleUpNext.lastIndex) {
                                        onMoveEpisode(ep, false)
                                    }
                                    QueueStripAction("Played", Icons.Filled.Check, LoheBrightBlueText) {
                                        onMarkPlayed(ep, true)
                                        actionTargetId = null
                                    }
                                    QueueStripAction("Remove", Icons.Filled.Delete, Color(0xFFEF4444)) {
                                        onRemoveFromQueue(ep)
                                        actionTargetId = null
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }

            if (isMultiSelectMode) {
                UpNextMultiSelectActionBar(
                    selectedEpisodes = selectedEpisodes,
                    onBatchAction = { action ->
                        if (selectedEpisodes.isNotEmpty()) {
                            onBatchUpNextAction(selectedEpisodes, action)
                            isMultiSelectMode = false
                            selectedEpisodeIds = emptySet()
                        }
                    }
                )
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Up Next Queue?") },
            text = {
                Text(
                    "Remove all ${visibleUpNext.size} episodes from your Up Next queue? Your podcasts and progress will not be deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearQueue()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    modifier = Modifier.testTag("clear_queue_confirm_button")
                ) {
                    Text("Clear Queue", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("clear_queue_confirm_dialog")
        )
    }
}

@Composable
private fun QueueStripAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (enabled) tint else tint.copy(alpha = 0.35f),
            fontWeight = FontWeight.Medium
        )
    }
}
