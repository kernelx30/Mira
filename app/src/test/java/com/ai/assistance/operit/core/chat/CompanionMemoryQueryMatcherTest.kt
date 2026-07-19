package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.CompanionMemorySourceKind
import com.ai.assistance.operit.data.model.CompanionMemoryType
import com.ai.assistance.operit.data.model.CompanionRecordScope
import com.ai.assistance.operit.data.repository.encodeCompanionMemoryValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryQueryMatcherTest {
    @Test
    fun `hometown questions match location memories`() {
        val records =
            listOf(
                record("location", "hometown", "武陟", "所在地"),
                record("food", "favorite_food", "火锅", "喜欢的食物"),
            )

        val result = CompanionMemoryQueryMatcher.rank(records, "我老家是哪里", 5)

        assertEquals(listOf("location"), result.map { it.record.id })
    }

    @Test
    fun `unrelated natural question returns no structured memory`() {
        val records = listOf(record("location", "hometown", "武陟", "所在地"))

        assertTrue(CompanionMemoryQueryMatcher.rank(records, "今天会下雨吗", 5).isEmpty())
    }

    @Test
    fun `exact values remain searchable`() {
        val records = listOf(record("location", "hometown", "武陟", "所在地"))

        assertEquals("location", CompanionMemoryQueryMatcher.rank(records, "武陟", 5).single().record.id)
    }

    private fun record(id: String, predicate: String, value: String, label: String) =
        CompanionMemoryRecordEntity(
            id = id,
            profileId = "default",
            companionId = "character:zero",
            scope = CompanionRecordScope.USER.name,
            type = CompanionMemoryType.FACT.name,
            subjectKey = "user",
            predicate = predicate,
            valueJson = encodeCompanionMemoryValue(value, label),
            normalizedValue = value,
            sourceKind = CompanionMemorySourceKind.USER_EXPLICIT.name,
        )
}
