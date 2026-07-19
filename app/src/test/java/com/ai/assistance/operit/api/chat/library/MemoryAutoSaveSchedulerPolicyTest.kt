package com.ai.assistance.operit.api.chat.library

import com.ai.assistance.operit.data.model.MemoryAutoSaveCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryAutoSaveSchedulerPolicyTest {
    @Test
    fun partitionsEveryScopedCandidateOnceByCompanionAndSourceKind() {
        val candidates =
            listOf(
                candidate(1, "character:a", MemoryAutoSaveCandidate.SOURCE_TYPE_EXPLICIT_USER),
                candidate(2, "character:a", MemoryAutoSaveCandidate.SOURCE_TYPE_SELECTED_USER_MESSAGE),
                candidate(3, "character:a", MemoryAutoSaveCandidate.SOURCE_TYPE_HIGH_VALUE_AUTO),
                candidate(4, "character:a", MemoryAutoSaveCandidate.SOURCE_TYPE_REPLY_FINALIZED_AUTO),
                candidate(5, "character:b", MemoryAutoSaveCandidate.SOURCE_TYPE_EXPLICIT_USER),
                candidate(6, "character:b", MemoryAutoSaveCandidate.SOURCE_TYPE_SELECTED_USER_MESSAGE),
                candidate(7, "character:b", MemoryAutoSaveCandidate.SOURCE_TYPE_HIGH_VALUE_AUTO),
                candidate(8, "character:b", MemoryAutoSaveCandidate.SOURCE_TYPE_REPLY_FINALIZED_AUTO),
            )

        val batches = buildScopedMemoryAutoSaveBatches(candidates, maxCandidatesPerCompanion = 20)
        val flattenedIds = batches.flatMap { it.candidates }.map { it.id }
        val kindByCandidateId =
            batches.flatMap { batch -> batch.candidates.map { it.id to batch.kind } }.toMap()

        assertEquals(candidates.map { it.id }.toSet(), flattenedIds.toSet())
        assertEquals(candidates.size, flattenedIds.size)
        assertEquals(flattenedIds.size, flattenedIds.distinct().size)
        assertEquals(MemoryAutoSaveBatchKind.EXPLICIT, kindByCandidateId.getValue(1))
        assertEquals(MemoryAutoSaveBatchKind.SELECTED, kindByCandidateId.getValue(2))
        assertEquals(MemoryAutoSaveBatchKind.HIGH_VALUE, kindByCandidateId.getValue(3))
        assertEquals(MemoryAutoSaveBatchKind.AUTO, kindByCandidateId.getValue(4))
        assertTrue(MemoryAutoSaveBatchKind.HIGH_VALUE.usesExactUserMessages)
        assertTrue(MemoryAutoSaveBatchKind.HIGH_VALUE.autoConfirmHighImportance)
        assertTrue(!MemoryAutoSaveBatchKind.AUTO.usesExactUserMessages)
        assertTrue(MemoryAutoSaveBatchKind.AUTO.autoConfirmHighImportance)
        assertTrue(
            batches.all { batch ->
                batch.candidates.all { it.companionId == batch.companionId }
            },
        )
    }

    @Test
    fun skipsLegacyCandidateWithoutCompanionSnapshot() {
        val batches =
            buildScopedMemoryAutoSaveBatches(
                orderedCandidates =
                    listOf(
                        candidate(1, "", MemoryAutoSaveCandidate.SOURCE_TYPE_EXPLICIT_USER),
                        candidate(2, "character:a", MemoryAutoSaveCandidate.SOURCE_TYPE_EXPLICIT_USER),
                    ),
                maxCandidatesPerCompanion = 20,
            )

        assertEquals(listOf(2L), batches.flatMap { it.candidates }.map { it.id })
        assertEquals("character:a", batches.single().companionId)
    }

    @Test
    fun appliesBatchLimitIndependentlyForEachCompanion() {
        val batches =
            buildScopedMemoryAutoSaveBatches(
                orderedCandidates =
                    listOf(
                        candidate(1, "character:a", MemoryAutoSaveCandidate.SOURCE_TYPE_EXPLICIT_USER),
                        candidate(2, "character:a", MemoryAutoSaveCandidate.SOURCE_TYPE_SELECTED_USER_MESSAGE),
                        candidate(3, "character:a", MemoryAutoSaveCandidate.SOURCE_TYPE_HIGH_VALUE_AUTO),
                        candidate(4, "character:b", MemoryAutoSaveCandidate.SOURCE_TYPE_HIGH_VALUE_AUTO),
                        candidate(5, "character:b", MemoryAutoSaveCandidate.SOURCE_TYPE_REPLY_FINALIZED_AUTO),
                    ),
                maxCandidatesPerCompanion = 2,
            )

        val idsByCompanion =
            batches
                .groupBy { it.companionId }
                .mapValues { (_, scopedBatches) ->
                    scopedBatches.flatMap { it.candidates }.map { it.id }
                }

        assertEquals(listOf(1L, 2L), idsByCompanion.getValue("character:a"))
        assertEquals(listOf(4L, 5L), idsByCompanion.getValue("character:b"))
    }

    private fun candidate(
        id: Long,
        companionId: String,
        sourceType: String,
    ): MemoryAutoSaveCandidate =
        MemoryAutoSaveCandidate(
            id = id,
            chatId = "chat-1",
            companionId = companionId,
            triggerMessageTimestamp = id,
            sourceType = sourceType,
        )
}
