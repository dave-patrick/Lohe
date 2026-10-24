package com.lohe.app.ui.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import com.lohe.app.R

/**
 * Drag-to-reorder for a lazy list.
 *
 * The gesture lives on a handle rather than on the row itself: a row that
 * can be swiped, long-pressed *and* dragged is three ways of saying the same
 * thing, and the listener cannot tell which one they are about to trigger. A
 * visible grip is unambiguous.
 */
class DragReorderState internal constructor(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDrop: () -> Unit,
) {
    /** The row currently being carried, or null when nothing is. */
    var draggedIndex by mutableStateOf<Int?>(null)
        private set

    /** How far the carried row has been moved, in pixels. */
    var draggedOffset by mutableFloatStateOf(0f)
        private set

    val isDragging: Boolean get() = draggedIndex != null

    fun onDragStart(index: Int) {
        draggedIndex = index
        draggedOffset = 0f
    }

    fun onDrag(delta: Float) {
        val current = draggedIndex ?: return
        val info = listState.layoutInfo
        val carried = info.visibleItemsInfo.firstOrNull { it.index == current } ?: return

        // Where the carried row's centre would be if the finger moved by
        // another `delta`.
        val proposedTop = carried.offset + draggedOffset + delta
        val proposedCentre = proposedTop + carried.size / 2

        val target = info.visibleItemsInfo.firstOrNull { candidate ->
            candidate.index != current &&
                proposedCentre >= candidate.offset &&
                proposedCentre < candidate.offset + candidate.size
        }
        if (target == null) {
            draggedOffset += delta
            return
        }

        onMove(current, target.index)
        draggedIndex = target.index
        // The list has just moved the row, so the offset has to be corrected
        // by however far the new slot is from the old one. Without this the
        // row jumps out from under the finger on every swap.
        draggedOffset = proposedTop - target.offset
    }

    fun onDragEnd() {
        draggedIndex = null
        draggedOffset = 0f
        onDrop()
    }

    suspend fun autoScrollAtEdges(edgeSize: Float, maxSpeed: Float) {
        var previousFrame = withFrameNanos { it }
        while (isDragging) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.05f)
            previousFrame = frame
            val info = listState.layoutInfo
            val index = draggedIndex ?: break
            val dragged = info.visibleItemsInfo.firstOrNull { it.index == index } ?: continue
            val center = dragged.offset + draggedOffset + dragged.size / 2f
            val topProximity =
                ((info.viewportStartOffset + edgeSize - center) / edgeSize).coerceIn(0f, 1f)
            val bottomProximity =
                ((center - (info.viewportEndOffset - edgeSize)) / edgeSize).coerceIn(0f, 1f)
            val distance = when {
                topProximity > 0f -> -maxSpeed * topProximity * seconds
                bottomProximity > 0f -> maxSpeed * bottomProximity * seconds
                else -> 0f
            }
            if (distance != 0f) {
                // LazyListState can consume less than requested at the ends
                // of the list. Keep the dragged row aligned only with the
                // distance the list actually scrolled, or it will drift off
                // screen and drop at index zero or the last index.
                val consumed = listState.scrollBy(distance)
                draggedOffset += consumed
            }
        }
    }
}

@Composable
internal fun rememberDragReorderState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit = {},
): DragReorderState {
    val state = remember(listState) { DragReorderState(listState, onMove, onDrop) }
    val edgeSize = with(LocalDensity.current) { 64.dp.toPx() }
    val maxSpeed = with(LocalDensity.current) { 600.dp.toPx() }
    LaunchedEffect(state.isDragging) {
        if (state.isDragging) state.autoScrollAtEdges(edgeSize, maxSpeed)
    }
    return state
}

/**
 * The grip a row is dragged by.
 *
 * Sized and coloured to read as an affordance rather than as an icon: it is
 * faint until the row is touched.
 *
 * @param key a stable identity for the row — its episode id, not its index.
 *   Keying the gesture on the index would restart it every time the row
 *   swapped places, which is exactly what happens while it is being dragged.
 */
@Composable
internal fun DragHandle(
    state: DragReorderState,
    index: Int,
    key: Any,
    modifier: Modifier = Modifier,
) {
    val dragging = state.draggedIndex == index
    val currentIndex by rememberUpdatedState(index)
    Icon(
        imageVector = Icons.Filled.DragHandle,
        contentDescription = stringResource(R.string.queue_drag_handle),
        tint = if (dragging) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        },
        modifier = modifier
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Claim the grip's press so the parent row's combined
                    // click handler cannot turn this same long press into its
                    // action menu.
                    down.consume()

                    val longPress = awaitLongPressOrCancellation(down.id)
                        ?: return@awaitEachGesture
                    state.onDragStart(currentIndex)
                    try {
                        var finished = false
                        while (!finished) {
                            val event = awaitPointerEvent()
                            val change: PointerInputChange =
                                event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                finished = true
                            } else {
                                val delta = change.positionChange()
                                if (delta.y != 0f) state.onDrag(delta.y)
                                if (delta.x != 0f || delta.y != 0f) change.consume()
                            }
                        }
                    } finally {
                        state.onDragEnd()
                    }
                }
            },
    )
}
