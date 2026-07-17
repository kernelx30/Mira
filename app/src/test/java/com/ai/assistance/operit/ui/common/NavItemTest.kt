package com.ai.assistance.operit.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavItemTest {
    @Test
    fun restoresKnownNavigationItemFromRoute() {
        assertEquals(NavItem.UserPreferencesSettings, NavItem.fromRoute("user_preferences_settings"))
        assertEquals(NavItem.MemoryBase, NavItem.fromRoute("memory_base"))
    }

    @Test
    fun ignoresUnknownRestoredRoute() {
        assertNull(NavItem.fromRoute("missing"))
        assertNull(NavItem.fromRoute(null))
    }
}
