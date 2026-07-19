package com.ai.assistance.operit.data.preferences

import org.junit.Assert.assertTrue
import org.junit.Test

class ApiPreferencesDefaultsTest {
    @Test
    fun autoRead_isEnabledWhenUserHasNotStoredAPreference() {
        assertTrue(ApiPreferences.DEFAULT_ENABLE_AUTO_READ)
    }
}
