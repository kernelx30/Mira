package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.CompanionMemoryProposal
import com.ai.assistance.operit.data.model.CompanionMemorySourceKind
import com.ai.assistance.operit.data.model.CompanionMemoryType
import com.ai.assistance.operit.data.model.CompanionRecordScope
import com.ai.assistance.operit.data.model.MemoryTriggerKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryReceiptBusTest {
    @Test
    fun automaticDirectFactDoesNotPublishSavedReceipt() {
        val proposal = proposal(MemoryTriggerKind.AUTO_EXTRACT)

        assertFalse(CompanionMemoryReceiptBus.shouldPublishSavedReceipt(proposal))
    }

    @Test
    fun explicitRequestPublishesSavedReceiptOnceConfirmed() {
        val proposal = proposal(MemoryTriggerKind.EXPLICIT_REQUEST)

        assertTrue(CompanionMemoryReceiptBus.shouldPublishSavedReceipt(proposal))
        assertFalse(
            CompanionMemoryReceiptBus.shouldPublishSavedReceipt(
                proposal.copy(reviewAt = 2_000L),
            ),
        )
    }

    @Test
    fun userSelectedMemoryDoesNotMasqueradeAsExplicitRequest() {
        val proposal = proposal(MemoryTriggerKind.USER_SELECTED)

        assertFalse(CompanionMemoryReceiptBus.shouldPublishSavedReceipt(proposal))
    }

    private fun proposal(triggerKind: MemoryTriggerKind) =
        CompanionMemoryProposal(
            scope = CompanionRecordScope.USER,
            type = CompanionMemoryType.PREFERENCE,
            subjectKey = "user",
            predicate = "hobby",
            value = "唱歌",
            normalizedValue = "唱歌",
            confidence = 0.9,
            importance = 0.8,
            sourceKind = CompanionMemorySourceKind.USER_EXPLICIT,
            conversationId = "chat-1",
            messageId = 42L,
            messageTimestamp = 1_000L,
            evidenceQuote = "我喜欢唱歌",
            reviewAt = null,
            triggerKind = triggerKind,
        )
}
