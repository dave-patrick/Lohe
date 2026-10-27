package com.lohe.app.ui.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeActionsCoordinatorTest {
    private val bounds = Rect(0f, 0f, 100f, 50f)

    @Test fun outsideTapCancelsOnlyOnce() {
        val coordinator = SwipeActionsCoordinator()
        var cancelled = 0
        coordinator.open(Any(), { bounds }) { cancelled++ }
        assertTrue(coordinator.dismissOutside(Offset(150f, 25f)))
        assertFalse(coordinator.isOpen)
        assertFalse(coordinator.dismissOutside(Offset(150f, 25f)))
        assertEquals(1, cancelled)
    }

    @Test fun insideTapLeavesActionButtonsUsable() {
        val coordinator = SwipeActionsCoordinator()
        var cancelled = false
        coordinator.open(Any(), { bounds }) { cancelled = true }
        assertFalse(coordinator.dismissOutside(Offset(25f, 25f)))
        assertTrue(coordinator.isOpen)
        assertFalse(cancelled)
    }

    @Test fun openingAnotherTrayClosesOnlyThePreviousOne() {
        val coordinator = SwipeActionsCoordinator()
        var first = 0
        var second = 0
        coordinator.open(Any(), { bounds }) { first++ }
        coordinator.open(Any(), { bounds }) { second++ }
        assertEquals(1, first)
        assertEquals(0, second)
        coordinator.dismiss()
        assertEquals(1, second)
    }

    @Test fun disposingAnOldRowDoesNotClearTheNewTray() {
        val coordinator = SwipeActionsCoordinator()
        val old = Any()
        val current = Any()
        coordinator.open(old, { bounds }) {}
        coordinator.open(current, { bounds }) {}
        coordinator.forget(old)
        assertTrue(coordinator.isOpen)
        coordinator.forget(current)
        assertFalse(coordinator.isOpen)
    }

    @Test fun updatedBoundsAreUsedAfterScrolling() {
        val coordinator = SwipeActionsCoordinator()
        var movedBounds = bounds
        coordinator.open(Any(), { movedBounds }) {}
        movedBounds = Rect(0f, 100f, 100f, 150f)
        assertFalse(coordinator.dismissOutside(Offset(25f, 125f)))
        assertTrue(coordinator.dismissOutside(Offset(25f, 25f)))
    }
}
