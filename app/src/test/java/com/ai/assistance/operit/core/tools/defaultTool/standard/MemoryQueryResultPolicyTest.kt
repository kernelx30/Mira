package com.ai.assistance.operit.core.tools.defaultTool.standard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryQueryResultPolicyTest {
    @Test
    fun structuredAndLegacyResultsShareOneLimit() {
        val selection =
            selectMemoryQueryResults(
                structuredCandidates = listOf("structured-1", "structured-2", "structured-3"),
                legacyCandidates = listOf("legacy-1", "legacy-2", "legacy-3"),
                limit = 4,
            )

        assertEquals(listOf("structured-1", "structured-2", "structured-3"), selection.structured)
        assertEquals(listOf("legacy-1"), selection.legacy)
        assertEquals(4, selection.structured.size + selection.legacy.size)
    }

    @Test
    fun structuredResultsCannotExceedLimitByThemselves() {
        val selection =
            selectMemoryQueryResults(
                structuredCandidates = listOf(1, 2, 3),
                legacyCandidates = listOf(4, 5),
                limit = 2,
            )

        assertEquals(listOf(1, 2), selection.structured)
        assertTrue(selection.legacy.isEmpty())
    }

    @Test
    fun folderScopedQueriesDoNotMixStructuredCompanionMemory() {
        assertFalse(shouldQueryStructuredCompanionMemory("projects/mira"))
        assertTrue(shouldQueryStructuredCompanionMemory(null))
        assertTrue(shouldQueryStructuredCompanionMemory(""))
    }
}
