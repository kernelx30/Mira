package com.ai.assistance.operit.core.companion

import com.ai.assistance.operit.data.preferences.AutoReadOverride
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiraCompanionAutoReadPolicyTest {
    @Test
    fun conversationSettingOverridesRoleAndGlobalSettings() {
        assertFalse(
            resolveProactiveAutoReadEnabled(
                globalEnabled = true,
                roleOverride = AutoReadOverride.ENABLED,
                conversationOverride = false,
            ),
        )
        assertTrue(
            resolveProactiveAutoReadEnabled(
                globalEnabled = false,
                roleOverride = AutoReadOverride.DISABLED,
                conversationOverride = true,
            ),
        )
    }

    @Test
    fun missingConversationSettingUsesRoleThenGlobalPolicy() {
        assertTrue(
            resolveProactiveAutoReadEnabled(
                globalEnabled = false,
                roleOverride = AutoReadOverride.ENABLED,
                conversationOverride = null,
            ),
        )
        assertFalse(
            resolveProactiveAutoReadEnabled(
                globalEnabled = true,
                roleOverride = AutoReadOverride.DISABLED,
                conversationOverride = null,
            ),
        )
        assertTrue(
            resolveProactiveAutoReadEnabled(
                globalEnabled = true,
                roleOverride = AutoReadOverride.INHERIT,
                conversationOverride = null,
            ),
        )
    }
}
