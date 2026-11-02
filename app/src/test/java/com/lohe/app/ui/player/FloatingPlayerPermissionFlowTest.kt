package com.lohe.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingPlayerPermissionFlowTest {
    @Test fun overlayMustBeGrantedBeforeNotificationSetup() {
        assertEquals(FloatingPlayerPermissionStep.OVERLAY, nextFloatingPlayerPermissionStep(false, false, false))
        assertEquals(FloatingPlayerPermissionStep.OVERLAY, nextFloatingPlayerPermissionStep(false, true, true))
    }
    @Test fun newNotificationDenialOffersAnExplanation() {
        assertEquals(FloatingPlayerPermissionStep.NOTIFICATIONS, nextFloatingPlayerPermissionStep(true, false, false))
    }
    @Test fun declinedNotificationsDoNotBlockControlsOrPromptAgain() {
        assertEquals(FloatingPlayerPermissionStep.ENABLE, nextFloatingPlayerPermissionStep(true, false, true))
    }
    @Test fun grantedOrExemptNotificationsNeedNoPrompt() {
        assertEquals(FloatingPlayerPermissionStep.ENABLE, nextFloatingPlayerPermissionStep(true, true, false))
    }
}
