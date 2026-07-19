package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.CompanionMemorySourceKind
import com.ai.assistance.operit.data.model.CompanionMemoryType
import com.ai.assistance.operit.data.model.CompanionRecordScope
import com.ai.assistance.operit.data.repository.encodeCompanionMemoryValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryDeletionTest {
    @Test
    fun recognizesConcreteDeletionRequestsButNotFeatureQuestions() {
        assertTrue(
            CompanionMemoryDeletionIntentDetector.isExplicitDeletionRequest(
                "把我不吃香菜这条记忆删掉",
            ),
        )
        assertTrue(
            CompanionMemoryDeletionIntentDetector.isExplicitDeletionRequest(
                "Please forget what I said about cilantro",
            ),
        )
        assertTrue(
            CompanionMemoryDeletionIntentDetector.isExplicitDeletionRequest(
                "删除我的生日",
            ),
        )
        assertFalse(
            CompanionMemoryDeletionIntentDetector.isExplicitDeletionRequest(
                "有没有删除记忆的工具？",
            ),
        )
        assertFalse(
            CompanionMemoryDeletionIntentDetector.isExplicitDeletionRequest(
                "怎么删除一条记忆？",
            ),
        )
        assertFalse(
            CompanionMemoryDeletionIntentDetector.isExplicitDeletionRequest(
                "咱们这个记忆系统，用户说删除，有对应的包吗？",
            ),
        )
        assertFalse(CompanionMemoryDeletionIntentDetector.isExplicitDeletionRequest("不要删除我的生日记忆"))
        assertFalse(CompanionMemoryDeletionIntentDetector.isExplicitDeletionRequest("保留这条记忆，别删掉"))
        assertFalse(CompanionMemoryDeletionIntentDetector.isExplicitDeletionRequest("Don't delete my birthday memory"))
        assertTrue(CompanionMemoryDeletionIntentDetector.isExplicitDeletionRequest("不要忘记删除旧的生日记忆"))
        assertTrue(CompanionMemoryDeletionIntentDetector.isReferentialDeletionRequest("把这个记忆删掉"))
        assertFalse(CompanionMemoryDeletionIntentDetector.isReferentialDeletionRequest("怎么删除这个记忆？"))
    }

    @Test
    fun recognizesBulkDeleteAndItsSecondConfirmation() {
        assertTrue(CompanionMemoryDeletionIntentDetector.isBulkDeletionRequest("清空全部记忆"))
        assertTrue(CompanionMemoryDeletionIntentDetector.isBulkDeletionRequest("删除所有记忆"))
        assertTrue(CompanionMemoryDeletionIntentDetector.isBulkDeletionRequest("全部删除"))
        assertTrue(CompanionMemoryDeletionIntentDetector.isBulkDeletionConfirmation("确认清空全部记忆"))
        assertTrue(CompanionMemoryDeletionIntentDetector.isBulkDeletionConfirmation("确认全部删除"))
        assertFalse(CompanionMemoryDeletionIntentDetector.isBulkDeletionConfirmation("确认"))
        assertFalse(CompanionMemoryDeletionIntentDetector.isBulkDeletionRequest("不要清空全部记忆"))
        assertFalse(CompanionMemoryDeletionIntentDetector.isBulkDeletionRequest("别清除所有记录"))
        assertFalse(CompanionMemoryDeletionIntentDetector.isBulkDeletionRequest("不要忘掉全部记忆"))
    }

    @Test
    fun ranksTheRequestedFactInsteadOfAnImportantUnrelatedMemory() {
        val cilantro = record("food.dislikes", "饮食偏好", "我不吃香菜", importance = 0.7)
        val boundary = record("boundary", "重要边界", "不要替我做重大决定", importance = 1.0)

        val matches =
            CompanionMemoryManagementMatcher.rank(
                records = listOf(boundary, cilantro),
                query = "把关于不吃香菜的记忆删掉",
            )

        assertEquals(cilantro.id, matches.single().record.id)
        assertTrue(matches.single().score >= 0.95)
    }

    @Test
    fun aRecentFactSentenceCanResolveAReferentialDeleteRequest() {
        val hometown = record("location", "家乡", "河南武陟")

        val matches =
            CompanionMemoryManagementMatcher.rank(
                records = listOf(hometown),
                query = "你是河南武陟的，这个我记得很清楚",
            )

        assertEquals(hometown.id, matches.single().record.id)
    }

    @Test
    fun broadTopicKeepsMultipleCandidatesForUserSelection() {
        val dislike = record("food.dislikes", "饮食偏好", "我不吃香菜")
        val allergy = record("health.allergy", "过敏信息", "我对香菜过敏")

        val matches =
            CompanionMemoryManagementMatcher.rank(
                records = listOf(dislike, allergy),
                query = "删除香菜相关记忆",
            )

        assertEquals(2, matches.size)
        assertTrue(matches.all { it.score >= 0.95 })
    }

    private fun record(
        predicate: String,
        label: String,
        value: String,
        importance: Double = 0.8,
    ): CompanionMemoryRecordEntity =
        CompanionMemoryRecordEntity(
            profileId = "profile-1",
            scope = CompanionRecordScope.USER.name,
            type = CompanionMemoryType.FACT.name,
            subjectKey = "user",
            predicate = predicate,
            valueJson = encodeCompanionMemoryValue(value, label),
            normalizedValue = value,
            confidence = 1.0,
            importance = importance,
            sourceKind = CompanionMemorySourceKind.USER_EXPLICIT.name,
        )
}
