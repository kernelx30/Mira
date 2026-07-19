package com.ai.assistance.operit.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingChatServiceFailurePolicyTest {
    @Test
    fun failuresWithinWindowAccumulateAndEventuallyDisableService() {
        var count = 0
        var lastCrashAt = 1_000L

        repeat(4) { index ->
            val now = 1_100L + index * 100L
            count = nextFloatingServiceCrashCount(count, lastCrashAt, now)
            lastCrashAt = now
        }

        assertEquals(4, count)
        assertTrue(shouldDisableFloatingServiceAfterFailures(count))
    }

    @Test
    fun failureOutsideWindowStartsANewSeries() {
        val count =
            nextFloatingServiceCrashCount(
                previousCount = 3,
                lastCrashAtMs = 1_000L,
                nowMs = 70_000L,
            )

        assertEquals(1, count)
        assertFalse(shouldDisableFloatingServiceAfterFailures(count))
    }
}
