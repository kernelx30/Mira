package com.ai.assistance.operit.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReadOverrideTest {
    @Test
    fun storageValue_unknownOrMissing_fallsBackToInherit() {
        assertEquals(AutoReadOverride.INHERIT, AutoReadOverride.fromStorageValue(null))
        assertEquals(AutoReadOverride.INHERIT, AutoReadOverride.fromStorageValue("legacy"))
    }

    @Test
    fun effectiveState_respectsCharacterOverride() {
        assertFalse(resolveAutoReadEnabled(false, AutoReadOverride.INHERIT))
        assertTrue(resolveAutoReadEnabled(true, AutoReadOverride.INHERIT))
        assertTrue(resolveAutoReadEnabled(false, AutoReadOverride.ENABLED))
        assertFalse(resolveAutoReadEnabled(true, AutoReadOverride.DISABLED))
    }
}
