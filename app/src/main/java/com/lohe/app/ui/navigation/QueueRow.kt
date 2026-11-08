package com.lohe.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import com.lohe.app.ui.theme.QueueIndicatorSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lohe.app.R
import com.lohe.app.data.QueueEntry
import com.lohe.app.ui.image.RemoteImage
import com.lohe.app.ui.theme.ElectricBlue
import com.lohe.app.ui.theme.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueRow(
    positionNumber: Int,
    showPositionNumber: Boolean = true,
    entry: QueueEntry,
    queueIndicator: QueueIndicatorSettings,
    isHead: Boolean,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    now: Long,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onTogglePlayed: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    onArchive: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    dragHandle: @Composable (() -> Unit)? = null,
    dragHandleAtStart: Boolean = false,
    playlistSelection: Boolean = false,
    onOpen: (() -> Unit)? = null,
    onAddToUpNext: (() -> Unit)? = null,
    onMoveToTop: (() -> Unit)? = null,
    onMoveToEnd: (() -> Unit)? = null,
) {
    val ep = entry.episode

    // One line, not four. The runtime, the date and where the episode is up
    // to come first and the show name last: a long show title would
    // otherwise push out the number the listener is actually reading.
    val metaLine = remember(entry.showTitle, ep.playedUpToSeconds, ep.durationSeconds, ep.playingStatus, ep.published, ep.isArchived, now) {
        val state = EpisodeMeta.line(ep, now)
        val show = entry.showTitle.trim()
        val base = when {
            state.isNotEmpty() && show.isNotEmpty() -> "$state · $show"
            state.isNotEmpty() -> state
            else -> show
        }
        buildAnnotatedString {
            if (ep.isArchived && base.isNotEmpty()) append("Archived · ")
            val stateStart = length
            append(base)
            val runtime = EpisodeMeta.duration(ep.durationSeconds)
            val durationStyle = SpanStyle(color = ElectricBlue, fontWeight = FontWeight.Bold)
            if (runtime.isNotEmpty() && state.endsWith(runtime)) {
                addStyle(durationStyle, stateStart + state.length - runtime.length, stateStart + state.length)
            }
            val remainingEnd = state.indexOf(" left")
            if (ep.isInProgress && remainingEnd > 0) {
                addStyle(durationStyle, stateStart, stateStart + remainingEnd)
            }
        }
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> if (onAddToUpNext != null) onArchive != null else onRemove != null
                // Swiping the other way rests the row open on its actions, so
                // they can actually be tapped; the old behaviour settled
                // straight back, which made them unreachable.
                SwipeToDismissBoxValue.StartToEnd -> true
                SwipeToDismissBoxValue.Settled -> true
            }
        },
    )

    val scope = rememberCoroutineScope()
    LaunchedEffect(playlistSelection) { if (playlistSelection) dismissState.reset() }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !playlistSelection,
        enableDismissFromEndToStart = !playlistSelection && (onAddToUpNext == null || onArchive != null),
        modifier = modifier.then(swipeActionDismissal(
            open = dismissState.currentValue != SwipeToDismissBoxValue.Settled ||
                dismissState.targetValue != SwipeToDismissBoxValue.Settled,
            onDismiss = { scope.launch { dismissState.reset() } },
        )),
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val background = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(background)
                    .padding(horizontal = 14.dp),
            ) {
                if (onAddToUpNext != null && direction != SwipeToDismissBoxValue.Settled) {
                    Row(
                        modifier = Modifier.align(
                            if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart
                            else Alignment.CenterEnd,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SwipeActionButton(
                            icon = Icons.Filled.Close,
                            label = "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { scope.launch { dismissState.reset() } },
                        )
                        if (direction == SwipeToDismissBoxValue.StartToEnd) {
                            SwipeActionButton(
                                icon = Icons.AutoMirrored.Filled.QueueMusic,
                                label = "Up Next",
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = {
                                    onAddToUpNext()
                                    scope.launch { dismissState.reset() }
                                },
                            )
                        } else if (onArchive != null) {
                            SwipeActionButton(
                                icon = Icons.Filled.Archive,
                                label = "Archive",
                                tint = MaterialTheme.colorScheme.error,
                                onClick = {
                                    onArchive()
                                    scope.launch { dismissState.reset() }
                                },
                            )
                        }
                    }
                } else if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SwipeActionButton(
                            icon = Icons.Filled.Close,
                            label = "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { scope.launch { dismissState.reset() } },
                        )
                        Row(
                            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (onMoveToTop != null) {
                                SwipeActionButton(Icons.Filled.VerticalAlignTop, "Top", MaterialTheme.colorScheme.primary, onClick = {
                                    onMoveToTop()
                                    scope.launch { dismissState.reset() }
                                })
                            }
                            if (onMoveToEnd != null) {
                                SwipeActionButton(Icons.Filled.VerticalAlignBottom, "End", MaterialTheme.colorScheme.primary, onClick = {
                                    onMoveToEnd()
                                    scope.launch { dismissState.reset() }
                                })
                            }
                            if (onMoveUp != null) {
                                SwipeActionButton(
                                    icon = Icons.Filled.KeyboardArrowUp,
                                    label = "Up",
                                    tint = MaterialTheme.colorScheme.primary,
                                    onClick = {
                                        onMoveUp()
                                        scope.launch { dismissState.reset() }
                                    },
                                )
                            }
                            if (onMoveDown != null) {
                                SwipeActionButton(
                                    icon = Icons.Filled.KeyboardArrowDown,
                                    label = "Down",
                                    tint = MaterialTheme.colorScheme.primary,
                                    onClick = {
                                        onMoveDown()
                                        scope.launch { dismissState.reset() }
                                    },
                                )
                            }
                            if (onTogglePlayed != null) {
                                SwipeActionButton(
                                    icon = Icons.Filled.CheckCircle,
                                    label = if (ep.isPlayed) "Unplayed" else "Played",
                                    tint = MaterialTheme.colorScheme.primary,
                                    onClick = {
                                        onTogglePlayed()
                                        scope.launch { dismissState.reset() }
                                    },
                                )
                            }
                            if (onArchive != null && !ep.isArchived) {
                                SwipeActionButton(
                                    icon = Icons.Filled.Archive,
                                    label = "Archive",
                                    tint = MaterialTheme.colorScheme.primary,
                                    onClick = {
                                        onArchive()
                                        scope.launch { dismissState.reset() }
                                    },
                                )
                            }
                        }
                    }
                } else if (direction == SwipeToDismissBoxValue.EndToStart && onRemove != null) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SwipeActionButton(
                            icon = Icons.Filled.Close,
                            label = "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { scope.launch { dismissState.reset() } },
                        )
                        SwipeActionButton(
                            icon = Icons.Filled.Close,
                            label = stringResource(R.string.queue_remove),
                            tint = MaterialTheme.colorScheme.error,
                            onClick = {
                                onRemove()
                                scope.launch { dismissState.reset() }
                            },
                        )
                    }
                }
            }
        },
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth().then(if (playlistSelection) Modifier.semantics { selected = isSelected } else Modifier),
            contentPadding = 5.dp,
            accent = when {
                isSelected -> ElectricBlue
                else -> null
            },
            onClick = onOpen ?: if (dragHandleAtStart && !inSelectionMode) null else onPlay,
            onLongClick = onLongPress,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (dragHandleAtStart && dragHandle != null) {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            dragHandle()
                        }
                    }

                    if (inSelectionMode) {
                        Icon(
                            imageVector = if (isSelected) {
                                Icons.Filled.CheckCircle
                            } else {
                                Icons.Outlined.RadioButtonUnchecked
                            },
                            contentDescription = null,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    } else if (showPositionNumber) {
                        Text(
                            text = "$positionNumber",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isHead) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }

                    RemoteImage(
                        url = ep.artworkUrl,
                        contentDescription = null,
                        size = 30.dp,
                        cornerRadius = 9.dp,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(9.dp)),
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        QueueMembershipIndicator(ep.isQueued, queueIndicator)
                        Text(
                            text = ep.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        }
                        if (metaLine.isNotBlank()) {
                            Text(
                                text = metaLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val durationSeconds = ep.durationSeconds ?: 0
                        if (durationSeconds > 0) {
                            val playedFraction = if (ep.isPlayed) {
                                1f
                            } else {
                                (ep.playedUpToSeconds.toFloat() / durationSeconds.toFloat())
                                    .coerceIn(0f, 1f)
                            }
                            // Inside the text column, so it reads as a
                            // played-progress bar for the episode rather than
                            // a rule drawn across the card.
                            LinearProgressIndicator(
                                progress = { playedFraction },
                                color = ElectricBlue,
                                trackColor = ElectricBlue.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .fillMaxWidth()
                                    .height(2.dp),
                            )
                        }
                    }

                    if (ep.isPlayed) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Listened",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp),
                        )
                    } else if (ep.isArchived) {
                        Icon(
                            imageVector = Icons.Filled.Archive,
                            contentDescription = "Archived",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    if (!playlistSelection) IconButton(onClick = onPlay, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.player_now_playing),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    if (dragHandle != null && !dragHandleAtStart) {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            dragHandle()
                        }
                    }
                }

            }
        }
    }
}

/**
 * One of the actions revealed by swiping a row.
 *
 * The icon alone asked the listener to guess, so each one carries its name.
 */
@Composable
internal fun SwipeActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}
