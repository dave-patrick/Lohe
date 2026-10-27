package com.lohe.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

internal class SwipeActionsCoordinator {
    private data class OpenAction(val key: Any, val bounds: () -> Rect, val dismiss: () -> Unit)
    private var active by mutableStateOf<OpenAction?>(null)
    val isOpen: Boolean get() = active != null

    fun open(key: Any, bounds: () -> Rect, dismiss: () -> Unit) {
        if (active?.key !== key) this.dismiss()
        active = OpenAction(key, bounds, dismiss)
    }

    fun forget(key: Any) {
        if (active?.key === key) active = null
    }

    fun dismiss() {
        val previous = active ?: return
        active = null
        previous.dismiss()
    }

    fun dismissOutside(position: Offset): Boolean {
        val current = active ?: return false
        if (current.bounds().contains(position)) return false
        dismiss()
        return true
    }
}

private val LocalSwipeActions = staticCompositionLocalOf<SwipeActionsCoordinator?> { null }

@Composable
internal fun SwipeActionsHost(content: @Composable () -> Unit) {
    val coordinator = remember { SwipeActionsCoordinator() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    CompositionLocalProvider(LocalSwipeActions provides coordinator) {
        Box(
            Modifier.fillMaxSize()
                .onGloballyPositioned { origin = it.positionInRoot() }
                .pointerInput(coordinator) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (coordinator.dismissOutside(down.position + origin)) {
                            // A tap intended to cancel must not also play or
                            // delete a different row underneath it.
                            down.consume()
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                },
        ) { content() }
        // Register when the tray opens so it takes precedence over handlers
        // installed by a newly navigated screen.
        if (coordinator.isOpen) BackHandler { coordinator.dismiss() }
    }
}

/** Registers a revealed tray with the host, using the unshifted row's bounds. */
@Composable
internal fun swipeActionDismissal(open: Boolean, onDismiss: () -> Unit): Modifier {
    val coordinator = LocalSwipeActions.current
    val key = remember { Any() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(open, coordinator) {
        if (open) coordinator?.open(key, { bounds }, { dismiss() })
        else coordinator?.forget(key)
    }
    DisposableEffect(coordinator) {
        onDispose { coordinator?.forget(key) }
    }
    return Modifier.onGloballyPositioned { bounds = it.boundsInRoot() }
}
