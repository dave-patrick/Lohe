package com.lohe.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueIndicatorSettingsTest {
    @Test fun missingAndUnknownChoicesUseSmallBlueDot() {
        assertEquals(QueueIndicatorSettings(), QueueIndicatorSettings.fromStored(null, null, null, null))
        assertEquals(QueueIndicatorSettings(enabled = false),
            QueueIndicatorSettings.fromStored(false, "old", "unknown", "enormous"))
    }

    @Test fun savedDesignSurvivesBeingDisabled() {
        assertEquals(QueueIndicatorSettings(false, QueueIndicatorStyle.RING, QueueIndicatorColor.TEAL, QueueIndicatorSize.LARGE),
            QueueIndicatorSettings.fromStored(false, "RING", "TEAL", "LARGE"))
    }
}
