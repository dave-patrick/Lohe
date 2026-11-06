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
    private val firstDraggableIndex: Int = 0,
    private val canMove: (from: Int, to: Int) -> Boolean = { _, _ -> true },
) {
    /** The row currently being carried, or null when nothing is. */
    var draggedIndex by mutableStateOf<Int?>(null)
        private set

    /** How far the carried row has been moved, in pixels. */
    var draggedOffset by mutableFloatStateOf(0f)
        private set

    val isDragging: Boolean get() = draggedIndex != null

    private var draggedKey: Any? = null
    private var draggedTop = 0f
    private var draggedSize = 0
    private var awaitingMoveLayout = false

    fun onDragStart(index: Int) {
        if (isDragging || index < firstDraggableIndex) return
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        draggedKey = item.key
        draggedTop = item.offset.toFloat()
        draggedSize = item.size
        awaitingMoveLayout = false
        draggedIndex = index
        draggedOffset = 0f
    }

    fun onDrag(delta: Float) {
        if (!isDragging) return
        draggedTop += delta
        updateDragPosition()
    }

    private fun updateDragPosition() {
        val current = draggedIndex ?: return
        val info = listState.layoutInfo
        val carried = info.visibleItemsInfo.firstOrNull { it.key == draggedKey } ?: return
        // A move updates composition before LazyColumn measures its new order.
        // Never move the different item now occupying the old slot.
        if (awaitingMoveLayout && carried.index != current) return
        awaitingMoveLayout = false
        draggedOffset = draggedTop - carried.offset
        draggedIndex = carried.index
        draggedSize = carried.size
        val center = draggedTop + draggedSize / 2f
        val target = info.visibleItemsInfo.filter { candidate ->
            candidate.index >= firstDraggableIndex && candidate.key != draggedKey &&
                canMove(carried.index, candidate.index) &&
                if (candidate.index < carried.index) {
                    center < candidate.offset + candidate.size / 2f
                } else {
                    center > candidate.offset + candidate.size / 2f
                }
        }.minByOrNull { kotlin.math.abs(center - (it.offset + it.size / 2f)) } ?: return
        awaitingMoveLayout = true
        draggedIndex = target.index
        draggedOffset = draggedTop - target.offset
        val firstVisible = listState.firstVisibleItemIndex
        if (carried.index <= firstVisible || target.index <= firstVisible) {
            // LazyColumn normally follows its first visible item's key. A
            // reorder at that slot must retain the numeric viewport instead,
            // or the carried key can be measured offscreen and lose its gesture.
            listState.requestScrollToItem(minOf(firstVisible, target.index), listState.firstVisibleItemScrollOffset)
        }
        onMove(carried.index, target.index)
    }

    fun onDragEnd() {
        if (!isDragging) return
        draggedIndex = null
        draggedKey = null
        awaitingMoveLayout = false
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
            updateDragPosition()
            if (!isDragging) break
            // Pause until the carried row is measured in its new slot.
            if (awaitingMoveLayout) continue
            val center = draggedTop + draggedSize / 2f
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
                val visible = listState.layoutInfo.visibleItemsInfo
                val carried = visible.firstOrNull { it.key == draggedKey } ?: continue
                val edge = if (distance < 0f) {
                    visible.firstOrNull { it.index >= firstDraggableIndex }
                } else visible.lastOrNull()
                // A pinned-group boundary can reject a swap. Keep the gesture
                // alive there rather than scrolling its keyed row out of composition.
                if (edge != null && !canMove(carried.index, edge.index)) continue
                listState.scrollBy(distance)
                // Stationary holds produce no pointer deltas; newly exposed
                // rows must still participate in reordering every frame.
                if (isDragging) updateDragPosition()
            }
        }
    }
}

@Composable
internal fun rememberDragReorderState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit = {},
    firstDraggableIndex: Int = 0,
    canMove: (from: Int, to: Int) -> Boolean = { _, _ -> true },
): DragReorderState {
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentCanMove by rememberUpdatedState(canMove)
    val state = remember(listState, firstDraggableIndex) {
        DragReorderState(listState, { from, to -> currentOnMove(from, to) }, { currentOnDrop() }, firstDraggableIndex,
            { from, to -> currentCanMove(from, to) })
    }
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
