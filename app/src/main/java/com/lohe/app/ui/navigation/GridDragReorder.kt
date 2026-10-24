package com.lohe.app.ui.navigation

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

internal class GridDragReorderState(
    private val gridState: LazyGridState,
    private val onMove: (Any, Any) -> Unit,
    private val onDrop: () -> Unit,
) {
    var draggedKey by mutableStateOf<Any?>(null)
        private set
    private var center by mutableStateOf(Offset.Zero)
    private var size = IntSize.Zero

    fun start(key: Any) {
        val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggedKey = key
        size = item.size
        center = Offset(item.offset.x + size.width / 2f, item.offset.y + size.height / 2f)
    }

    fun drag(amount: Offset) {
        if (draggedKey == null) return
        center += amount
        moveToTarget()
    }

    fun moveToTarget() {
        val key = draggedKey ?: return
        val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != key && center.x >= it.offset.x && center.x < it.offset.x + it.size.width &&
                center.y >= it.offset.y && center.y < it.offset.y + it.size.height
        } ?: return
        onMove(key, target.key)
    }

    fun offset(key: Any): Offset {
        if (key != draggedKey) return Offset.Zero
        val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return Offset.Zero
        return center - Offset(item.offset.x + size.width / 2f, item.offset.y + size.height / 2f)
    }

    fun scrollSpeed(edge: Float): Float {
        val layout = gridState.layoutInfo
        val top = layout.viewportStartOffset + layout.beforeContentPadding
        val bottom = layout.viewportEndOffset - layout.afterContentPadding
        return when {
            center.y - size.height / 2f < top + edge ->
                -((top + edge - center.y + size.height / 2f) / edge).coerceIn(0f, 1f)
            center.y + size.height / 2f > bottom - edge ->
                ((center.y + size.height / 2f - bottom + edge) / edge).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    fun end() {
        if (draggedKey == null) return
        draggedKey = null
        onDrop()
    }
}

@Composable
internal fun rememberGridDragReorderState(
    gridState: LazyGridState,
    onMove: (Any, Any) -> Unit,
    onDrop: () -> Unit,
): GridDragReorderState {
    val currentMove by rememberUpdatedState(onMove)
    val currentDrop by rememberUpdatedState(onDrop)
    val state = remember(gridState) {
        GridDragReorderState(gridState, { from, to -> currentMove(from, to) }, { currentDrop() })
    }
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val speed = with(LocalDensity.current) { 600.dp.toPx() }
    LaunchedEffect(state.draggedKey) {
        if (state.draggedKey == null) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (isActive) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.05f)
            previousFrame = frame
            val distance = state.scrollSpeed(edge) * speed * seconds
            if (distance != 0f) {
                gridState.scrollBy(distance)
                state.moveToTarget()
            }
        }
    }
    return state
}

@Composable
internal fun GridDragHandle(
    state: GridDragReorderState,
    playlistId: String,
    playlistName: String,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = Icons.Filled.DragHandle,
        contentDescription = "Drag to reorder $playlistName",
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.pointerInput(state, playlistId) {
            detectDragGestures(
                onDragStart = { state.start(playlistId) },
                onDrag = { change, amount ->
                    change.consume()
                    state.drag(amount)
                },
                onDragEnd = { state.end() },
                onDragCancel = { state.end() },
            )
        }.padding(12.dp),
    )
}
