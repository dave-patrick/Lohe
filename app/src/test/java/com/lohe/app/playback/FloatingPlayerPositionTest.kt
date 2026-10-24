package com.lohe.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingPlayerPositionTest {
    @Test
    fun `position respects each available screen edge`() {
        assertEquals(0, floatingPlayerPosition(-1f, 500))
        assertEquals(500, floatingPlayerPosition(2f, 500))
        assertEquals(250, floatingPlayerPosition(0.5f, 500))
    }

    @Test
    fun `expanded controls on a small display never produce negative positions`() {
        assertEquals(0, floatingPlayerPosition(0.9f, -100))
        assertEquals(0f, floatingPlayerFraction(60, 0))
    }

    @Test
    fun `saved fraction adapts after rotation or collapsing`() {
        val fraction = floatingPlayerFraction(300, 400)
        assertEquals(0.75f, fraction)
        assertEquals(600, floatingPlayerPosition(fraction, 800))
        assertEquals(150, floatingPlayerPosition(fraction, 200))
    }

    @Test
    fun `invalid persisted fractions have a safe fallback`() {
        assertEquals(250, floatingPlayerPosition(Float.NaN, 500))
        assertEquals(250, floatingPlayerPosition(Float.POSITIVE_INFINITY, 500))
        assertEquals(0f, floatingPlayerFraction(-20, 500))
        assertEquals(1f, floatingPlayerFraction(800, 500))
    }

    @Test
    fun `opacity cannot make controls invisible or accept invalid settings`() {
        assertEquals(0.25f, safeFloatingPlayerOpacity(0f))
        assertEquals(1f, safeFloatingPlayerOpacity(2f))
        assertEquals(0.5f, safeFloatingPlayerOpacity(0.5f))
        assertEquals(1f, safeFloatingPlayerOpacity(Float.NaN))
    }

    @Test
    fun `every control size retains a minimum 48 dp touch target`() {
        assertEquals(48, floatingPlayerButtonSize(48))
        assertEquals(48, floatingPlayerButtonSize(56))
        assertEquals(64, floatingPlayerButtonSize(72))
        assertEquals(80, floatingPlayerButtonSize(88))
    }
}
