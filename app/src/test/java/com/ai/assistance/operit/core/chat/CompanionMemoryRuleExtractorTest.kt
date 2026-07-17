package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.CompanionMemoryType
import com.ai.assistance.operit.data.model.CompanionRecordScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryRuleExtractorTest {
    @Test
    fun extractsExplicitIdentityPreferenceBoundaryAndRelationshipFacts() {
        val messages =
            listOf(
                "我叫阿策" to "name",
                "我的生日是7月3日" to "birthday",
                "我喜欢吃辣" to "likes",
                "我讨厌香菜" to "dislikes",
                "以后叫我老板" to "preferred_address",
                "不要在晚上十一点后主动找我" to "boundary",
            )

        messages.forEachIndexed { index, (content, predicate) ->
            val proposals =
                CompanionMemoryRuleExtractor.extract(
                    content = content,
                    conversationId = "chat-1",
                    messageTimestamp = index + 1L,
                )
            assertTrue("Expected $predicate from $content", proposals.any { it.predicate == predicate })
        }
    }

    @Test
    fun bindsRelationshipAddressToRelationshipScopeAndKeepsEvidence() {
        val proposal =
            CompanionMemoryRuleExtractor.extract(
                content = "以后叫我阿策",
                conversationId = "chat-9",
                messageTimestamp = 99L,
                messageId = 7L,
            ).single()

        assertEquals(CompanionRecordScope.RELATIONSHIP, proposal.scope)
        assertEquals(CompanionMemoryType.RELATIONSHIP, proposal.type)
        assertEquals("阿策", proposal.value)
        assertEquals("以后叫我阿策", proposal.evidenceQuote)
        assertEquals(7L, proposal.messageId)
    }

    @Test
    fun ignoresOrdinaryConversationWithoutExplicitFactPattern() {
        assertTrue(
            CompanionMemoryRuleExtractor.extract(
                content = "今天天气看起来还行",
                conversationId = "chat-1",
                messageTimestamp = 1L,
            ).isEmpty(),
        )
    }

    @Test
    fun extractsHealthWorkGoalRoutineAndFamilyFacts() {
        val cases =
            listOf(
                "我对花生过敏" to "health.allergy",
                "我的职业是产品经理" to "occupation",
                "我现在在星河科技工作" to "workplace",
                "我的目标是明年考上研究生" to "long_term_goal",
                "我每天跑步半小时" to "routine",
                "我女朋友叫小雨" to "family.girlfriend.name",
            )

        cases.forEachIndexed { index, (content, predicate) ->
            val proposals =
                CompanionMemoryRuleExtractor.extract(
                    content = content,
                    conversationId = "chat-important",
                    messageTimestamp = index + 1L,
                    messageId = index + 10L,
                )
            assertTrue("Expected $predicate from $content", proposals.any { it.predicate == predicate })
        }
    }

    @Test
    fun splitsRepeatedFactsIntoAtomicMemories() {
        val proposals =
            CompanionMemoryRuleExtractor.extract(
                content = "我喜欢咖啡，我喜欢爵士乐，我对花生过敏，我对芒果过敏",
                conversationId = "chat-many",
                messageTimestamp = 10L,
            )

        assertEquals(2, proposals.count { it.predicate == "likes" })
        assertEquals(2, proposals.count { it.predicate == "health.allergy" })
    }

    @Test
    fun blocksExplicitNotesContainingCredentials() {
        val proposals =
            CompanionMemoryRuleExtractor.extract(
                content = "请记住：我的 API key 是 sk-example-secret-123456",
                conversationId = "chat-secret",
                messageTimestamp = 10L,
            )

        assertTrue(proposals.isEmpty())
    }

    @Test
    fun extractsChangedFoodPreferenceAsDislike() {
        val proposal =
            CompanionMemoryRuleExtractor.extract(
                content = "我现在不吃辣了",
                conversationId = "chat-correction",
                messageTimestamp = 10L,
            ).single()

        assertEquals("dislikes", proposal.predicate)
        assertEquals("辣", proposal.value)
    }
}
