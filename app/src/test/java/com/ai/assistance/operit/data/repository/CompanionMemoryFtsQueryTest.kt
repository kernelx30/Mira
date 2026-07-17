package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.db.companionMemorySearchIndexNeedsRepair
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryFtsQueryTest {
    @Test
    fun `search query uses the portable match contract`() {
        assertTrue(COMPANION_MEMORY_FTS_QUERY.contains("companion_memory_fts MATCH ?"))
        assertFalse(COMPANION_MEMORY_FTS_QUERY.contains("bm25("))
    }

    @Test
    fun `legacy plain search table is repaired before querying`() {
        assertTrue(
            companionMemorySearchIndexNeedsRepair(
                "CREATE TABLE `companion_memory_fts` (`recordId` TEXT, `searchableText` TEXT)",
            ),
        )
        assertFalse(
            companionMemorySearchIndexNeedsRepair(
                "CREATE VIRTUAL TABLE `companion_memory_fts` USING fts4(`recordId`, `searchableText`)",
            ),
        )
        assertFalse(
            companionMemorySearchIndexNeedsRepair(
                "CREATE VIRTUAL TABLE `companion_memory_fts` USING fts5(`recordId`, `searchableText`)",
            ),
        )
        assertFalse(companionMemorySearchIndexNeedsRepair(null))
    }
}
