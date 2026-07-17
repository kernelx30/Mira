package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AnrMonitorPolicyTest {
    @Test
    fun `main thread delay thresholds match Android ANR timing`() {
        assertEquals(MainThreadHealth.HEALTHY, classifyMainThreadDelay(999L))
        assertEquals(MainThreadHealth.WARNING, classifyMainThreadDelay(1_000L))
        assertEquals(MainThreadHealth.WARNING, classifyMainThreadDelay(4_999L))
        assertEquals(MainThreadHealth.BLOCKED, classifyMainThreadDelay(5_000L))
    }
}
