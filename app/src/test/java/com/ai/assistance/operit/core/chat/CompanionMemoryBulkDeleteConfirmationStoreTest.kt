package com.ai.assistance.operit.core.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanionMemoryBulkDeleteConfirmationStoreTest {
    @Test
    fun consumesOnlyTheOriginalBulkDeleteSnapshot() {
        val key = "profile|companion|chat"
        CompanionMemoryBulkDeleteConfirmationStore.clear(key)
        CompanionMemoryBulkDeleteConfirmationStore.request(key, listOf("memory-a", "memory-b"), nowMs = 1_000L)

        assertEquals(
            listOf("memory-a", "memory-b"),
            CompanionMemoryBulkDeleteConfirmationStore.consume(key, nowMs = 2_000L),
        )
        assertNull(CompanionMemoryBulkDeleteConfirmationStore.consume(key, nowMs = 2_001L))
    }

    @Test
    fun expiresUnconfirmedBulkDelete() {
        val key = "profile|companion|expired"
        CompanionMemoryBulkDeleteConfirmationStore.clear(key)
        CompanionMemoryBulkDeleteConfirmationStore.request(key, listOf("memory-a"), nowMs = 1_000L)

        assertNull(CompanionMemoryBulkDeleteConfirmationStore.consume(key, nowMs = 601_000L))
    }
}
