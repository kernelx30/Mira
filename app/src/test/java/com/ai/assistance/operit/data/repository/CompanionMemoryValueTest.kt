package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryValueTest {
    @Test
    fun labeledValueRoundTripsWithoutChangingTheRecallText() {
        val record = record(encodeCompanionMemoryValue("7 月 3 日", "生日"))

        assertEquals("7 月 3 日", record.decodedValue())
        assertEquals("生日", record.decodedLabel())
    }

    @Test
    fun legacyPrimitiveValueRemainsReadable() {
        val record = record(encodeCompanionMemoryValue("喜欢吃辣"))

        assertEquals("喜欢吃辣", record.decodedValue())
        assertNull(record.decodedLabel())
    }

    @Test
    fun manualPredicateIsStableForEquivalentLabels() {
        assertEquals(
            CompanionMemoryRepository.manualPredicate(" 饮食 偏好 "),
            CompanionMemoryRepository.manualPredicate("饮食偏好"),
        )
    }

    @Test
    fun recallSelectionPenalizesNearDuplicateParaphrases() {
        val first = record("\"用户喜欢喝咖啡\"").copy(id = "first", normalizedValue = "用户喜欢喝咖啡")
        val duplicate =
            record("\"用户喜欢喝咖啡\"").copy(
                id = "duplicate",
                predicate = "manual:coffee-paraphrase",
                normalizedValue = "用户喜欢喝咖啡",
            )
        val different = record("\"用户生日是七月三日\"").copy(id = "different", normalizedValue = "用户生日七月三日")

        val selected =
            CompanionMemoryRepository.selectDiverseCandidates(
                scoredCandidates = listOf(first to 1.0, duplicate to 0.99, different to 0.8),
                limit = 2,
            )

        assertEquals("first", selected.first().id)
        assertTrue(selected.any { it.id == "different" })
    }

    @Test
    fun priorityRecallReservesCriticalMemoryCategories() {
        val relevant = record("\"最近想喝咖啡\"").copy(id = "relevant", normalizedValue = "最近想喝咖啡")
        val identity = record("\"用户叫阿策\"").copy(id = "identity", type = "IDENTITY", predicate = "name")
        val health = record("\"用户对花生过敏\"").copy(id = "health", predicate = "health.allergy")
        val commitment =
            record("\"约好周日复盘\"").copy(
                id = "commitment",
                scope = "RELATIONSHIP",
                type = "COMMITMENT",
                subjectKey = "relationship",
                predicate = "commitment",
            )
        val event = record("\"用户刚换了工作\"").copy(id = "event", type = "EVENT", importance = 0.9)

        val selected =
            CompanionMemoryRepository.selectPriorityCandidates(
                scoredCandidates =
                    listOf(
                        relevant to 1.0,
                        identity to 0.05,
                        health to 0.04,
                        commitment to 0.03,
                        event to 0.02,
                    ),
                limit = 6,
            )

        assertTrue(selected.any { it.id == "identity" })
        assertTrue(selected.any { it.id == "health" })
        assertTrue(selected.any { it.id == "commitment" })
        assertTrue(selected.any { it.id == "event" })
        assertTrue(selected.any { it.id == "relevant" })
    }

    @Test
    fun oppositePreferenceWordingProducesTheSameConflictKey() {
        assertEquals(
            CompanionMemoryRepository.preferenceConflictKey("吃辣"),
            CompanionMemoryRepository.preferenceConflictKey("现在不吃辣了"),
        )
    }

    private fun record(valueJson: String) =
        CompanionMemoryRecordEntity(
            profileId = "default",
            scope = "USER",
            type = "FACT",
            subjectKey = "user",
            predicate = "manual:test",
            valueJson = valueJson,
            normalizedValue = "test",
            sourceKind = "USER_EXPLICIT",
        )
}
