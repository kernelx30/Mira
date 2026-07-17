package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.CompanionMemorySourceKind
import com.ai.assistance.operit.data.model.CompanionMemoryType
import com.ai.assistance.operit.data.model.CompanionRecordScope
import org.junit.Assert.assertEquals
import org.junit.Test

class RelationshipStateRepositoryTest {
    @Test
    fun buildsCurrentRelationshipStateFromNewestStructuredRecords() {
        val records =
            listOf(
                record("old-address", CompanionMemoryType.RELATIONSHIP, "preferred_address", "老哥", 10L),
                record("new-address", CompanionMemoryType.RELATIONSHIP, "preferred_address", "老板", 20L),
                record("promise", CompanionMemoryType.COMMITMENT, "commitment", "周五提醒缴费", 15L),
                record("boundary", CompanionMemoryType.BOUNDARY, "quiet_hours", "23:00 后不主动打扰", 12L),
            )

        val state = buildRelationshipState("profile", "zero", records)

        assertEquals("老板", state.preferredAddress)
        assertEquals(listOf("promise"), state.commitments.map { it.id })
        assertEquals(listOf("boundary"), state.boundaries.map { it.id })
    }

    private fun record(
        id: String,
        type: CompanionMemoryType,
        predicate: String,
        value: String,
        updatedAt: Long,
    ) =
        CompanionMemoryRecordEntity(
            id = id,
            profileId = "profile",
            companionId = "zero",
            scope = CompanionRecordScope.RELATIONSHIP.name,
            type = type.name,
            subjectKey = "relationship",
            predicate = predicate,
            valueJson = value,
            normalizedValue = value,
            updatedAt = updatedAt,
            sourceKind = CompanionMemorySourceKind.USER_EXPLICIT.name,
        )
}
