package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AutoQueueMode
import com.example.data.local.EpisodeEntity
import com.example.data.local.EpisodeSwipeAction
import com.example.data.local.LoheUserSettings
import com.example.data.local.PlaylistEntity
import com.example.data.local.PodcastEntity
import com.example.ui.theme.LoheBackgroundDark
import com.example.ui.theme.LoheBorderDark
import com.example.ui.theme.LoheCyanAccent
import com.example.ui.theme.LoheElectricBlue
import com.example.ui.theme.LoheSurfaceCardDark
import com.example.ui.theme.LoheSurfaceElevatedDark
import com.example.ui.theme.LoheTextPrimaryDark
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Queue button with a dropdown allowing the listener to add an episode
 * to the Beginning (Play Next) or End (Play Last) of the Up Next queue,
 * or remove it when already queued.
 */
@Composable
fun UpNextQueueActionButton(
    inUpNext: Boolean,
    onAddToBeginning: () -> Unit,
    onAddToEnd: () -> Unit,
    onRemoveFromUpNext: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    tint: Color? = null,
    testTagPrefix: String = "up_next_action"
) {
    var expanded by remember { mutableStateOf(false) }
    val resolvedTint = tint ?: if (inUpNext) LoheCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .size(buttonSize)
                .testTag(testTagPrefix)
        ) {
            Icon(
                imageVector = if (inUpNext) Icons.AutoMirrored.Filled.QueueMusic else Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = if (inUpNext) "Manage Up Next Position" else "Add to Up Next",
                tint = resolvedTint,
                modifier = Modifier.size(iconSize)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = LoheSurfaceElevatedDark
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (inUpNext) "Move to Beginning (Play Next)" else "Play Next (Beginning)",
                        color = LoheTextPrimaryDark
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.VerticalAlignTop,
                        contentDescription = null,
                        tint = LoheCyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onAddToBeginning()
                },
                modifier = Modifier.testTag("${testTagPrefix}_beginning")
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (inUpNext) "Move to End (Play Last)" else "Play Last (End of Queue)",
                        color = LoheTextPrimaryDark
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.VerticalAlignBottom,
                        contentDescription = null,
                        tint = LoheElectricBlue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onAddToEnd()
                },
                modifier = Modifier.testTag("${testTagPrefix}_end")
            )
            if (inUpNext && onRemoveFromUpNext != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Remove from Up Next",
                            color = Color(0xFFEF4444)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        expanded = false
                        onRemoveFromUpNext()
                    },
                    modifier = Modifier.testTag("${testTagPrefix}_remove")
                )
            }
        }
    }
}

private data class SlideTrayAction(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val testTag: String,
    val onClick: () -> Unit
)

private fun buildTrayActionsFor(
    action: EpisodeSwipeAction,
    episode: EpisodeEntity,
    sidePrefix: String,
    onSwipeAction: (EpisodeEntity, EpisodeSwipeAction) -> Unit
): List<SlideTrayAction> = when (action) {
    EpisodeSwipeAction.UP_NEXT_CHOOSE,
    EpisodeSwipeAction.PLAY_NEXT,
    EpisodeSwipeAction.PLAY_LAST -> listOf(
        SlideTrayAction(
            label = "Top",
            icon = Icons.Filled.VerticalAlignTop,
            containerColor = LoheElectricBlue,
            testTag = "${sidePrefix}_up_next_top_${episode.id}",
            onClick = { onSwipeAction(episode, EpisodeSwipeAction.PLAY_NEXT) }
        ),
        SlideTrayAction(
            label = "Bottom",
            icon = Icons.Filled.VerticalAlignBottom,
            containerColor = Color(0xFF0284C7),
            testTag = "${sidePrefix}_up_next_bottom_${episode.id}",
            onClick = { onSwipeAction(episode, EpisodeSwipeAction.PLAY_LAST) }
        )
    )
    EpisodeSwipeAction.MARK_PLAYED -> listOf(
        SlideTrayAction(
            label = if (episode.isPlayed) "Unplayed" else "Played",
            icon = Icons.Filled.CheckCircle,
            containerColor = Color(0xFF16A34A),
            testTag = "${sidePrefix}_mark_played_${episode.id}",
            onClick = { onSwipeAction(episode, EpisodeSwipeAction.MARK_PLAYED) }
        )
    )
    EpisodeSwipeAction.DOWNLOAD -> listOf(
        SlideTrayAction(
            label = if (episode.isDownloaded) "Remove" else "Download",
            icon = if (episode.isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.DownloadForOffline,
            containerColor = Color(0xFF7C3AED),
            testTag = "${sidePrefix}_download_${episode.id}",
            onClick = { onSwipeAction(episode, EpisodeSwipeAction.DOWNLOAD) }
        )
    )
    EpisodeSwipeAction.BOOKMARK -> listOf(
        SlideTrayAction(
            label = if (episode.isStarred) "Unstar" else "Bookmark",
            icon = if (episode.isStarred) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            containerColor = Color(0xFFD97706),
            testTag = "${sidePrefix}_bookmark_${episode.id}",
            onClick = { onSwipeAction(episode, EpisodeSwipeAction.BOOKMARK) }
        )
    )
    EpisodeSwipeAction.NONE -> emptyList()
}

/**
 * Reusable smooth horizontal slide-to-reveal action container built on Compose's
 * horizontal `draggable` gesture so it cooperates reliably with vertical `LazyColumn`
 * scrolling without swallowing taps or fighting thresholds.
 */
@Composable
private fun HorizontalSlideActionContainer(
    itemKey: String,
    rightSlideActions: List<SlideTrayAction>,
    leftSlideActions: List<SlideTrayAction>,
    rightQuickFire: (() -> Unit)? = null,
    leftQuickFire: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    content: @Composable () -> Unit
) {
    val hasRight = rightSlideActions.isNotEmpty()
    val hasLeft = leftSlideActions.isNotEmpty()
    if (!hasRight && !hasLeft) {
        Box(modifier = modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    // Reset open tray if LazyColumn reuses slot for a different episode
    LaunchedEffect(itemKey) {
        if (offsetX.value != 0f) offsetX.snapTo(0f)
    }

    val buttonWidthDp = 76.dp
    val singleButtonWidthDp = 96.dp
    val rightTrayWidthDp = if (rightSlideActions.size > 1) buttonWidthDp * rightSlideActions.size else singleButtonWidthDp
    val leftTrayWidthDp = if (leftSlideActions.size > 1) buttonWidthDp * leftSlideActions.size else singleButtonWidthDp

    val rightTrayPx = with(density) { if (hasRight) rightTrayWidthDp.toPx() else 0f }
    val leftTrayPx = with(density) { if (hasLeft) leftTrayWidthDp.toPx() else 0f }
    val overdragPx = with(density) { 108.dp.toPx() }
    val openThresholdPx = with(density) { 30.dp.toPx() }
    val rightQuickFireThresholdPx = rightTrayPx + with(density) { 88.dp.toPx() }
    val leftQuickFireThresholdPx = leftTrayPx + with(density) { 88.dp.toPx() }

    val latestRightQuickFire by rememberUpdatedState(rightQuickFire)
    val latestLeftQuickFire by rememberUpdatedState(leftQuickFire)

    val shape = RoundedCornerShape(cornerRadius)
    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val draggableState = rememberDraggableState { delta ->
        val maxRight = if (hasRight) rightTrayPx + overdragPx else 0f
        val maxLeft = if (hasLeft) -(leftTrayPx + overdragPx) else 0f
        val target = (offsetX.value + delta).coerceIn(maxLeft, maxRight)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { offsetX.snapTo(target) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
    ) {
        // Revealed background tray — strictly matches foreground height
        if (abs(offsetX.value) > 1f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        when {
                            offsetX.value > 0f && rightSlideActions.isNotEmpty() -> rightSlideActions.last().containerColor
                            offsetX.value < 0f && leftSlideActions.isNotEmpty() -> leftSlideActions.first().containerColor
                            else -> Color.Transparent
                        }
                    )
            ) {
                // Revealed on Slide Right (left side of card)
                if (offsetX.value > 2f && hasRight) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(rightTrayWidthDp)
                            .fillMaxHeight()
                    ) {
                        rightSlideActions.forEach { action ->
                            SlideActionTrayButton(
                                action = action,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onInvoke = {
                                    action.onClick()
                                    scope.launch { offsetX.animateTo(0f, springSpec) }
                                }
                            )
                        }
                    }
                }

                // Revealed on Slide Left (right side of card)
                if (offsetX.value < -2f && hasLeft) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(leftTrayWidthDp)
                            .fillMaxHeight()
                    ) {
                        leftSlideActions.forEach { action ->
                            SlideActionTrayButton(
                                action = action,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onInvoke = {
                                    action.onClick()
                                    scope.launch { offsetX.animateTo(0f, springSpec) }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Foreground sliding episode card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = draggableState,
                    onDragStopped = { velocity ->
                        scope.launch {
                            val current = offsetX.value
                            when {
                                current > 0f && hasRight -> {
                                    val qf = latestRightQuickFire
                                    if (qf != null && (current >= rightQuickFireThresholdPx || velocity >= 2200f)) {
                                        qf()
                                        offsetX.animateTo(0f, springSpec)
                                    } else if (current >= openThresholdPx || velocity >= 280f) {
                                        offsetX.animateTo(rightTrayPx, springSpec)
                                    } else {
                                        offsetX.animateTo(0f, springSpec)
                                    }
                                }
                                current < 0f && hasLeft -> {
                                    val qf = latestLeftQuickFire
                                    if (qf != null && (current <= -leftQuickFireThresholdPx || velocity <= -2200f)) {
                                        qf()
                                        offsetX.animateTo(0f, springSpec)
                                    } else if (current <= -openThresholdPx || velocity <= -280f) {
                                        offsetX.animateTo(-leftTrayPx, springSpec)
                                    } else {
                                        offsetX.animateTo(0f, springSpec)
                                    }
                                }
                                else -> offsetX.animateTo(0f, springSpec)
                            }
                        }
                    }
                )
        ) {
            content()

            // Tap displaced card while slid open to close cleanly instead of firing row click
            if (abs(offsetX.value) > 8f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            scope.launch { offsetX.animateTo(0f, springSpec) }
                        }
                )
            }
        }
    }
}

@Composable
private fun SlideActionTrayButton(
    action: SlideTrayAction,
    onInvoke: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(action.containerColor)
            .clickable(onClick = onInvoke)
            .testTag(action.testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = action.label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Reusable configurable Slide Left / Slide Right wrapper for episode cards.
 * - Sliding right reveals Top & Bottom of Up Next buttons (or the user's configured action).
 * - Sliding left reveals the user's configured swipe-left action (default Mark Played / Unplayed)
 *   and also supports full-swipe quick trigger.
 */
@Composable
fun SwipeableEpisodeRow(
    episode: EpisodeEntity,
    settings: LoheUserSettings,
    onSwipeAction: (EpisodeEntity, EpisodeSwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(modifier = modifier.testTag("swipeable_episode_row_${episode.id}")) {
            content()
        }
        return
    }
    val latestOnSwipeAction by rememberUpdatedState(onSwipeAction)
    val latestEpisode by rememberUpdatedState(episode)

    val rightAction = settings.swipeRightAction
    val leftAction = settings.swipeLeftAction

    val rightActions = buildTrayActionsFor(
        action = rightAction,
        episode = episode,
        sidePrefix = "swipe_right",
        onSwipeAction = { ep, act -> latestOnSwipeAction(ep, act) }
    )
    val leftActions = buildTrayActionsFor(
        action = leftAction,
        episode = episode,
        sidePrefix = "swipe_left",
        onSwipeAction = { ep, act -> latestOnSwipeAction(ep, act) }
    )

    // Single-action sides allow quick full-swipe trigger; 2-button Up Next chooser snaps open so user picks Top or Bottom
    val rightQuickFire: (() -> Unit)? = if (rightActions.size == 1 && rightAction != EpisodeSwipeAction.NONE) {
        { latestOnSwipeAction(latestEpisode, rightAction) }
    } else null
    val leftQuickFire: (() -> Unit)? = if (leftActions.size == 1 && leftAction != EpisodeSwipeAction.NONE) {
        { latestOnSwipeAction(latestEpisode, leftAction) }
    } else null

    HorizontalSlideActionContainer(
        itemKey = episode.id,
        rightSlideActions = rightActions,
        leftSlideActions = leftActions,
        rightQuickFire = rightQuickFire,
        leftQuickFire = leftQuickFire,
        modifier = modifier.testTag("swipeable_episode_row_${episode.id}"),
        cornerRadius = cornerRadius,
        content = content
    )
}

/**
 * Dedicated Slide Left / Slide Right wrapper for Up Next queue items:
 * - Slide right: Move to Top or Move to Bottom of Up Next
 * - Slide left: Mark as Played or Remove from Up Next
 */
@Composable
fun SwipeableUpNextRow(
    episode: EpisodeEntity,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onMarkPlayed: () -> Unit,
    onRemoveFromQueue: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(modifier = modifier.testTag("swipeable_up_next_row_${episode.id}")) {
            content()
        }
        return
    }
    val latestMoveTop by rememberUpdatedState(onMoveToTop)
    val latestMoveBottom by rememberUpdatedState(onMoveToBottom)
    val latestMarkPlayed by rememberUpdatedState(onMarkPlayed)
    val latestRemove by rememberUpdatedState(onRemoveFromQueue)

    val rightActions = listOf(
        SlideTrayAction(
            label = "Top",
            icon = Icons.Filled.VerticalAlignTop,
            containerColor = LoheElectricBlue,
            testTag = "up_next_swipe_top_${episode.id}",
            onClick = { latestMoveTop() }
        ),
        SlideTrayAction(
            label = "Bottom",
            icon = Icons.Filled.VerticalAlignBottom,
            containerColor = Color(0xFF0284C7),
            testTag = "up_next_swipe_bottom_${episode.id}",
            onClick = { latestMoveBottom() }
        )
    )
    val leftActions = listOf(
        SlideTrayAction(
            label = "Played",
            icon = Icons.Filled.CheckCircle,
            containerColor = Color(0xFF16A34A),
            testTag = "up_next_swipe_played_${episode.id}",
            onClick = { latestMarkPlayed() }
        ),
        SlideTrayAction(
            label = "Remove",
            icon = Icons.Filled.Delete,
            containerColor = Color(0xFFDC2626),
            testTag = "up_next_swipe_remove_${episode.id}",
            onClick = { latestRemove() }
        )
    )

    HorizontalSlideActionContainer(
        itemKey = episode.id,
        rightSlideActions = rightActions,
        leftSlideActions = leftActions,
        rightQuickFire = null,
        leftQuickFire = null,
        modifier = modifier.testTag("swipeable_up_next_row_${episode.id}"),
        cornerRadius = cornerRadius,
        content = content
    )
}

enum class EpisodeBatchAction {
    ADD_UP_NEXT_TOP,
    ADD_UP_NEXT_BOTTOM,
    MOVE_UP_NEXT_TOP,
    MOVE_UP_NEXT_BOTTOM,
    REMOVE_FROM_UP_NEXT,
    MARK_PLAYED,
    MARK_UNPLAYED,
    DOWNLOAD,
    REMOVE_DOWNLOAD,
    BOOKMARK,
    UNBOOKMARK,
    ARCHIVE
}

enum class PodcastBatchAction {
    MARK_ALL_PLAYED,
    AUTO_QUEUE_ON,
    AUTO_QUEUE_OFF,
    REFRESH,
    UNSUBSCRIBE
}

enum class PlaylistBatchAction {
    QUEUE_EPISODES,
    PIN,
    UNPIN,
    AUTO_DOWNLOAD_ON,
    AUTO_DOWNLOAD_OFF,
    DELETE
}

@Composable
fun EpisodeMultiSelectTopBar(
    selectedCount: Int,
    totalCount: Int,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allSelected = totalCount > 0 && selectedCount >= totalCount
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val containerBg = if (isDark) LoheSurfaceCardDark else MaterialTheme.colorScheme.surface
    val borderCol = if (isDark) LoheElectricBlue.copy(alpha = 0.55f) else LoheElectricBlue.copy(alpha = 0.45f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerBg,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp)
            .border(1.dp, borderCol, RoundedCornerShape(16.dp))
            .testTag("episode_multiselect_top_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("multiselect_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Exit Selection",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LoheElectricBlue,
                    modifier = Modifier.testTag("multiselect_count_text")
                )
            }

            TextButton(
                onClick = onToggleSelectAll,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("multiselect_select_all_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.SelectAll,
                    contentDescription = null,
                    tint = LoheElectricBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (allSelected) "Clear All" else "Select All",
                    color = LoheElectricBlue,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun EpisodeMultiSelectActionBar(
    selectedEpisodes: List<EpisodeEntity>,
    onBatchAction: (EpisodeBatchAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val barBg = if (isDark) LoheSurfaceElevatedDark else MaterialTheme.colorScheme.surface
    val borderCol = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline
    val enabled = selectedEpisodes.isNotEmpty()
    val allPlayed = enabled && selectedEpisodes.all { it.isPlayed }
    val allDownloaded = enabled && selectedEpisodes.all { it.isDownloaded }
    val allStarred = enabled && selectedEpisodes.all { it.isStarred }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = barBg,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp)
            .border(1.dp, borderCol, RoundedCornerShape(18.dp))
            .testTag("episode_multiselect_action_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MultiSelectBatchActionChip(
                label = "Play Next",
                icon = Icons.Filled.VerticalAlignTop,
                accent = LoheElectricBlue,
                enabled = enabled,
                testTag = "multiselect_action_up_next_top",
                onClick = { onBatchAction(EpisodeBatchAction.ADD_UP_NEXT_TOP) }
            )
            MultiSelectBatchActionChip(
                label = "Play Last",
                icon = Icons.Filled.VerticalAlignBottom,
                accent = Color(0xFF0284C7),
                enabled = enabled,
                testTag = "multiselect_action_up_next_bottom",
                onClick = { onBatchAction(EpisodeBatchAction.ADD_UP_NEXT_BOTTOM) }
            )
            MultiSelectBatchActionChip(
                label = if (allPlayed) "Unplayed" else "Played",
                icon = if (allPlayed) Icons.Filled.CheckCircleOutline else Icons.Filled.CheckCircle,
                accent = Color(0xFF16A34A),
                enabled = enabled,
                testTag = "multiselect_action_mark_played",
                onClick = {
                    onBatchAction(
                        if (allPlayed) EpisodeBatchAction.MARK_UNPLAYED else EpisodeBatchAction.MARK_PLAYED
                    )
                }
            )
            MultiSelectBatchActionChip(
                label = if (allDownloaded) "Remove DL" else "Download",
                icon = if (allDownloaded) Icons.Filled.DownloadDone else Icons.Filled.DownloadForOffline,
                accent = Color(0xFF0EA5E9),
                enabled = enabled,
                testTag = "multiselect_action_download",
                onClick = {
                    onBatchAction(
                        if (allDownloaded) EpisodeBatchAction.REMOVE_DOWNLOAD else EpisodeBatchAction.DOWNLOAD
                    )
                }
            )
            MultiSelectBatchActionChip(
                label = if (allStarred) "Unstar" else "Star",
                icon = if (allStarred) Icons.Filled.BookmarkBorder else Icons.Filled.Bookmark,
                accent = Color(0xFFF59E0B),
                enabled = enabled,
                testTag = "multiselect_action_bookmark",
                onClick = {
                    onBatchAction(
                        if (allStarred) EpisodeBatchAction.UNBOOKMARK else EpisodeBatchAction.BOOKMARK
                    )
                }
            )
            MultiSelectBatchActionChip(
                label = "Archive",
                icon = Icons.Filled.Archive,
                accent = Color(0xFF64748B),
                enabled = enabled,
                testTag = "multiselect_action_archive",
                onClick = { onBatchAction(EpisodeBatchAction.ARCHIVE) }
            )
        }
    }
}

@Composable
fun UpNextMultiSelectActionBar(
    selectedEpisodes: List<EpisodeEntity>,
    onBatchAction: (EpisodeBatchAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val barBg = if (isDark) LoheSurfaceElevatedDark else MaterialTheme.colorScheme.surface
    val borderCol = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline
    val enabled = selectedEpisodes.isNotEmpty()
    val allDownloaded = enabled && selectedEpisodes.all { it.isDownloaded }
    val allStarred = enabled && selectedEpisodes.all { it.isStarred }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = barBg,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp)
            .border(1.dp, borderCol, RoundedCornerShape(18.dp))
            .testTag("up_next_multiselect_action_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MultiSelectBatchActionChip(
                label = "Move Top",
                icon = Icons.Filled.VerticalAlignTop,
                accent = LoheElectricBlue,
                enabled = enabled,
                testTag = "up_next_multiselect_move_top",
                onClick = { onBatchAction(EpisodeBatchAction.MOVE_UP_NEXT_TOP) }
            )
            MultiSelectBatchActionChip(
                label = "Move End",
                icon = Icons.Filled.VerticalAlignBottom,
                accent = Color(0xFF0284C7),
                enabled = enabled,
                testTag = "up_next_multiselect_move_bottom",
                onClick = { onBatchAction(EpisodeBatchAction.MOVE_UP_NEXT_BOTTOM) }
            )
            MultiSelectBatchActionChip(
                label = "Played",
                icon = Icons.Filled.CheckCircle,
                accent = Color(0xFF16A34A),
                enabled = enabled,
                testTag = "up_next_multiselect_mark_played",
                onClick = { onBatchAction(EpisodeBatchAction.MARK_PLAYED) }
            )
            MultiSelectBatchActionChip(
                label = if (allDownloaded) "Remove DL" else "Download",
                icon = if (allDownloaded) Icons.Filled.DownloadDone else Icons.Filled.DownloadForOffline,
                accent = Color(0xFF0EA5E9),
                enabled = enabled,
                testTag = "up_next_multiselect_download",
                onClick = {
                    onBatchAction(
                        if (allDownloaded) EpisodeBatchAction.REMOVE_DOWNLOAD else EpisodeBatchAction.DOWNLOAD
                    )
                }
            )
            MultiSelectBatchActionChip(
                label = if (allStarred) "Unstar" else "Star",
                icon = if (allStarred) Icons.Filled.BookmarkBorder else Icons.Filled.Bookmark,
                accent = Color(0xFFF59E0B),
                enabled = enabled,
                testTag = "up_next_multiselect_bookmark",
                onClick = {
                    onBatchAction(
                        if (allStarred) EpisodeBatchAction.UNBOOKMARK else EpisodeBatchAction.BOOKMARK
                    )
                }
            )
            MultiSelectBatchActionChip(
                label = "Remove",
                icon = Icons.Filled.Delete,
                accent = Color(0xFFDC2626),
                enabled = enabled,
                testTag = "up_next_multiselect_remove",
                onClick = { onBatchAction(EpisodeBatchAction.REMOVE_FROM_UP_NEXT) }
            )
        }
    }
}

@Composable
fun PodcastMultiSelectActionBar(
    selectedPodcasts: List<PodcastEntity>,
    onBatchAction: (PodcastBatchAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val barBg = if (isDark) LoheSurfaceElevatedDark else MaterialTheme.colorScheme.surface
    val borderCol = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline
    val enabled = selectedPodcasts.isNotEmpty()
    val allAutoQueue = enabled && selectedPodcasts.all { it.autoQueueMode != AutoQueueMode.OFF.name }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = barBg,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp)
            .border(1.dp, borderCol, RoundedCornerShape(18.dp))
            .testTag("podcast_multiselect_action_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MultiSelectBatchActionChip(
                label = "All Played",
                icon = Icons.Filled.CheckCircle,
                accent = Color(0xFF16A34A),
                enabled = enabled,
                testTag = "podcast_multiselect_mark_played",
                onClick = { onBatchAction(PodcastBatchAction.MARK_ALL_PLAYED) }
            )
            MultiSelectBatchActionChip(
                label = if (allAutoQueue) "Auto-Q Off" else "Auto-Q On",
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                accent = LoheElectricBlue,
                enabled = enabled,
                testTag = "podcast_multiselect_auto_queue",
                onClick = {
                    onBatchAction(
                        if (allAutoQueue) PodcastBatchAction.AUTO_QUEUE_OFF else PodcastBatchAction.AUTO_QUEUE_ON
                    )
                }
            )
            MultiSelectBatchActionChip(
                label = "Refresh",
                icon = Icons.Filled.Refresh,
                accent = Color(0xFF0EA5E9),
                enabled = enabled,
                testTag = "podcast_multiselect_refresh",
                onClick = { onBatchAction(PodcastBatchAction.REFRESH) }
            )
            MultiSelectBatchActionChip(
                label = "Unsubscribe",
                icon = Icons.Filled.RemoveCircleOutline,
                accent = Color(0xFFDC2626),
                enabled = enabled,
                testTag = "podcast_multiselect_unsubscribe",
                onClick = { onBatchAction(PodcastBatchAction.UNSUBSCRIBE) }
            )
        }
    }
}

@Composable
fun PlaylistMultiSelectActionBar(
    selectedPlaylists: List<PlaylistEntity>,
    onBatchAction: (PlaylistBatchAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == LoheBackgroundDark
    val barBg = if (isDark) LoheSurfaceElevatedDark else MaterialTheme.colorScheme.surface
    val borderCol = if (isDark) LoheBorderDark else MaterialTheme.colorScheme.outline
    val enabled = selectedPlaylists.isNotEmpty()
    val allPinned = enabled && selectedPlaylists.all { it.isPinned }
    val allAutoDl = enabled && selectedPlaylists.all { it.autoDownload }
    val hasDeletableCustom = enabled && selectedPlaylists.any { it.smartRuleType.isEmpty() }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = barBg,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp)
            .border(1.dp, borderCol, RoundedCornerShape(18.dp))
            .testTag("playlist_multiselect_action_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MultiSelectBatchActionChip(
                label = "Queue All",
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                accent = LoheElectricBlue,
                enabled = enabled,
                testTag = "playlist_multiselect_queue",
                onClick = { onBatchAction(PlaylistBatchAction.QUEUE_EPISODES) }
            )
            MultiSelectBatchActionChip(
                label = if (allPinned) "Unpin" else "Pin",
                icon = if (allPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                accent = Color(0xFF0284C7),
                enabled = enabled,
                testTag = "playlist_multiselect_pin",
                onClick = {
                    onBatchAction(
                        if (allPinned) PlaylistBatchAction.UNPIN else PlaylistBatchAction.PIN
                    )
                }
            )
            MultiSelectBatchActionChip(
                label = if (allAutoDl) "Auto-DL Off" else "Auto-DL On",
                icon = if (allAutoDl) Icons.Filled.DownloadDone else Icons.Filled.DownloadForOffline,
                accent = Color(0xFF0EA5E9),
                enabled = enabled,
                testTag = "playlist_multiselect_auto_dl",
                onClick = {
                    onBatchAction(
                        if (allAutoDl) PlaylistBatchAction.AUTO_DOWNLOAD_OFF else PlaylistBatchAction.AUTO_DOWNLOAD_ON
                    )
                }
            )
            MultiSelectBatchActionChip(
                label = "Delete",
                icon = Icons.Filled.Delete,
                accent = Color(0xFFDC2626),
                enabled = hasDeletableCustom,
                testTag = "playlist_multiselect_delete",
                onClick = { onBatchAction(PlaylistBatchAction.DELETE) }
            )
        }
    }
}

@Composable
private fun MultiSelectBatchActionChip(
    label: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.42f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = if (enabled) 0.14f else 0.06f))
            .border(1.dp, accent.copy(alpha = if (enabled) 0.35f else 0.15f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accent.copy(alpha = contentAlpha),
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Uniform pill badge for unplayed / playlist episode counts.
 * Removes Android font padding and matches lineHeight to fontSize with centered
 * LineHeightStyle + TextAlign.Center so numbers are strictly centered both vertically and horizontally.
 */
@Composable
fun UniformCountPillBadge(
    count: Int,
    borderColor: Color,
    modifier: Modifier = Modifier,
    borderWidth: Dp = 1.dp,
    pillWidth: Dp = 46.dp,
    pillHeight: Dp = 24.dp
) {
    val label = if (count > 999) "999+" else "$count"
    Box(
        modifier = modifier
            .width(pillWidth)
            .height(pillHeight)
            .clip(RoundedCornerShape(50))
            .background(LoheElectricBlue)
            .border(borderWidth, borderColor, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center
    ) {
        @Suppress("DEPRECATION")
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontFeatureSettings = "tnum",
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                )
            ),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
