package com.lohe.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCategoryTest {
    @Test
    fun `each section has a unique restorable route`() {
        assertEquals(SettingsCategory.entries.size, SettingsCategory.entries.map { it.route }.toSet().size)
        SettingsCategory.entries.forEach { category ->
            assertEquals(category, SettingsCategory.fromRoute(category.route))
            assertTrue(category.title.isNotBlank())
            assertTrue(category.summary.isNotBlank())
        }
    }

    @Test
    fun `unknown sections fall back to the index`() {
        assertNull(SettingsCategory.fromRoute(null))
        assertNull(SettingsCategory.fromRoute("missing"))
    }
}
