package com.ai.assistance.operit.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ChatTurnOptionsTest {
    @Test
    fun defaultsInheritToolPreferenceWithoutForcingAnInvocation() {
        val options = ChatTurnOptions()

        assertNull(options.toolsEnabledOverride)
        assertFalse(options.requireToolExecution)
    }
}
