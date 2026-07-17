package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowUsageTest {
    @Test
    fun assignsProviderFramingDifferenceToOther() {
        val usage =
            ContextWindowUsage.fromRawEstimates(
                totalTokens = 1_000L,
                messageTokens = 500L,
                systemToolTokens = 200L,
                skillTokens = 50L,
                systemPromptTokens = 150L,
            )

        assertEquals(100L, usage.otherTokens)
        assertEquals(1_000L, usage.classifiedTokens)
        assertTrue(usage.hasBreakdown)
    }

    @Test
    fun proportionallyFitsOverestimatedComponentsToProviderTotal() {
        val usage =
            ContextWindowUsage.fromRawEstimates(
                totalTokens = 100L,
                messageTokens = 90L,
                systemToolTokens = 60L,
                skillTokens = 30L,
                systemPromptTokens = 20L,
            )

        assertEquals(100L, usage.classifiedTokens)
        assertEquals(0L, usage.otherTokens)
        assertTrue(usage.messageTokens > usage.skillTokens)
        assertTrue(usage.systemToolTokens > usage.systemPromptTokens)
    }
}
