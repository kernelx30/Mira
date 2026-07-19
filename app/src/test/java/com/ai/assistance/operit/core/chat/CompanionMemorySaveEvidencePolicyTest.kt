package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemorySaveEvidencePolicyTest {
    @Test
    fun concreteSaveRequestCanOnlyUseItsOwnEvidence() {
        val latest = message(3L, "记住我不吃香菜")
        val previous = message(2L, "我的生日是七月三日")

        assertEquals(
            latest,
            CompanionMemorySaveEvidencePolicy.selectEvidenceMessage(
                listOf(latest, previous),
                "不吃香菜",
            ),
        )
        assertNull(
            CompanionMemorySaveEvidencePolicy.selectEvidenceMessage(
                listOf(latest, previous),
                "生日是七月三日",
            ),
        )
    }

    @Test
    fun exactReferentialRequestCanUseOnlyTheImmediatelyPreviousUserMessage() {
        val latest = message(3L, "记住上一条")
        val previous = message(2L, "我的生日是七月三日")
        val older = message(1L, "我住在杭州")

        assertTrue(CompanionMemorySaveEvidencePolicy.isReferentialSaveRequest(latest.content))
        assertEquals(
            previous,
            CompanionMemorySaveEvidencePolicy.selectEvidenceMessage(
                listOf(latest, previous, older),
                "生日是七月三日",
            ),
        )
        assertNull(
            CompanionMemorySaveEvidencePolicy.selectEvidenceMessage(
                listOf(latest, previous, older),
                "住在杭州",
            ),
        )
    }

    @Test
    fun bareRememberCommandResolvesTheImmediatelyPreviousUserMessage() {
        val latest = message(3L, "记住")
        val previous = message(2L, "我是武陟的")

        assertTrue(CompanionMemorySaveEvidencePolicy.isReferentialSaveRequest(latest.content))
        val selection =
            CompanionMemorySaveEvidencePolicy.resolveEvidence(
                recentUserMessagesDesc = listOf(latest, previous),
                evidenceQuote = "记住",
            )

        assertEquals(previous, selection?.message)
        assertEquals(previous.content, selection?.quote)
    }

    @Test
    fun requestContainingItsOwnFactIsNotTreatedAsAReference() {
        assertTrue(!CompanionMemorySaveEvidencePolicy.isReferentialSaveRequest("记住这个：我不吃香菜"))
    }

    private fun message(id: Long, content: String) =
        MessageEntity(
            messageId = id,
            chatId = "chat-1",
            sender = "user",
            content = content,
            timestamp = id,
            orderIndex = id.toInt(),
        )
}
