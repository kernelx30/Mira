package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.CompanionMemorySourceKind
import com.ai.assistance.operit.data.model.CompanionMemoryEdgeType
import com.ai.assistance.operit.data.model.CompanionMemoryProposalAction
import com.ai.assistance.operit.data.model.CompanionRecordScope
import com.ai.assistance.operit.data.model.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryProposalExtractorTest {
    private val userMessage =
        MessageEntity(
            messageId = 42L,
            chatId = "chat-1",
            sender = "user",
            content = "我平时不吃香菜，记一下。",
            timestamp = 1_000L,
            orderIndex = 0,
        )

    @Test
    fun acceptsOnlyProposalBoundToExactUserEvidence() {
        val response =
            """[{"action":"CREATE","scope":"USER","type":"PREFERENCE","predicate":"food.dislikes","label":"饮食偏好","value":"不吃香菜","confidence":0.8,"importance":0.8,"evidence_message_id":42,"evidence_quote":"不吃香菜"}]"""

        val proposal =
            CompanionMemoryProposalExtractor.parseValidatedProposals(
                response = response,
                conversationId = "chat-1",
                messages = listOf(userMessage),
                nowMs = 2_000L,
            ).single()

        assertEquals(42L, proposal.messageId)
        assertEquals("不吃香菜", proposal.evidenceQuote)
        assertEquals(CompanionMemorySourceKind.USER_IMPLIED, proposal.sourceKind)
        assertEquals(2_000L, proposal.reviewAt)
    }

    @Test
    fun rejectsInventedMessageIdAndNonExactQuote() {
        val response =
            """[
                {"action":"CREATE","scope":"USER","type":"PREFERENCE","predicate":"food.dislikes","value":"不吃香菜","evidence_message_id":99,"evidence_quote":"不吃香菜"},
                {"action":"CREATE","scope":"USER","type":"PREFERENCE","predicate":"food.dislikes","value":"不吃葱","evidence_message_id":42,"evidence_quote":"不吃葱"}
            ]""".trimIndent()

        val proposals =
            CompanionMemoryProposalExtractor.parseValidatedProposals(
                response = response,
                conversationId = "chat-1",
                messages = listOf(userMessage),
                nowMs = 2_000L,
            )

        assertTrue(proposals.isEmpty())
    }

    @Test
    fun selectedUserMessageBecomesConfirmedMemory() {
        val response =
            """[{"action":"CREATE","scope":"USER","type":"PREFERENCE","predicate":"food.dislikes","value":"不吃香菜","evidence_message_id":42,"evidence_quote":"不吃香菜"}]"""

        val proposal =
            CompanionMemoryProposalExtractor.parseValidatedProposals(
                response = response,
                conversationId = "chat-1",
                messages = listOf(userMessage),
                requireReview = false,
                nowMs = 2_000L,
            ).single()

        assertEquals(CompanionMemorySourceKind.USER_EXPLICIT, proposal.sourceKind)
        assertNull(proposal.reviewAt)
    }

    @Test
    fun acceptsRelationshipMemoryWithoutTreatingItAsCompanionProfile() {
        val response =
            """[{"action":"CREATE","scope":"RELATIONSHIP","type":"RELATIONSHIP","predicate":"preferred_address","value":"老板","evidence_message_id":42,"evidence_quote":"记一下"}]"""

        val proposal =
            CompanionMemoryProposalExtractor.parseValidatedProposals(
                response = response,
                conversationId = "chat-1",
                messages = listOf(userMessage),
                nowMs = 2_000L,
            ).single()

        assertEquals(CompanionRecordScope.RELATIONSHIP, proposal.scope)
        assertEquals("relationship", proposal.subjectKey)
    }

    @Test
    fun linkNeedsOnlyExistingIdsEdgeTypeAndExactEvidence() {
        val response =
            """[{"action":"LINK","memory_id":"memory-a","related_memory_id":"memory-b","edge_type":"AVOIDS","edge_strength":0.9,"evidence_message_id":42,"evidence_quote":"不吃香菜"}]"""

        val proposal =
            CompanionMemoryProposalExtractor.parseValidatedProposals(
                response = response,
                conversationId = "chat-1",
                messages = listOf(userMessage),
                nowMs = 2_000L,
            ).single()

        assertEquals(CompanionMemoryProposalAction.LINK, proposal.action)
        assertEquals("memory-a", proposal.memoryId)
        assertEquals("memory-b", proposal.relatedMemoryId)
        assertEquals(CompanionMemoryEdgeType.AVOIDS, proposal.edgeType)
        assertEquals(listOf(42L), proposal.evidenceMessageIds)
    }

    @Test
    fun highValueBatchAutoConfirmsOnlyStrongImportantProposal() {
        val response =
            """[{"action":"CREATE","scope":"USER","type":"PREFERENCE","predicate":"food.dislikes","value":"不吃香菜","confidence":0.85,"importance":0.95,"evidence_message_id":42,"evidence_quote":"不吃香菜"}]"""

        val proposal =
            CompanionMemoryProposalExtractor.parseValidatedProposals(
                response = response,
                conversationId = "chat-1",
                messages = listOf(userMessage),
                requireReview = true,
                autoConfirmHighImportance = true,
                nowMs = 2_000L,
            ).single()

        assertEquals(CompanionMemorySourceKind.USER_EXPLICIT, proposal.sourceKind)
        assertNull(proposal.reviewAt)
    }

    @Test
    fun weakProposalStillRequiresReviewInHighValueBatch() {
        val response =
            """[{"action":"CREATE","scope":"USER","type":"FACT","predicate":"possible_fact","value":"可能不吃香菜","confidence":0.6,"importance":0.7,"evidence_message_id":42,"evidence_quote":"不吃香菜"}]"""

        val proposal =
            CompanionMemoryProposalExtractor.parseValidatedProposals(
                response = response,
                conversationId = "chat-1",
                messages = listOf(userMessage),
                requireReview = true,
                autoConfirmHighImportance = true,
                nowMs = 2_000L,
            ).single()

        assertEquals(CompanionMemorySourceKind.USER_IMPLIED, proposal.sourceKind)
        assertNotNull(proposal.reviewAt)
    }

    @Test
    fun canonicalizesPredicateAliasesFromModelOutput() {
        val response =
            """[{"action":"CREATE","scope":"USER","type":"PREFERENCE","predicate":"food.dislikes","value":"不吃香菜","evidence_message_id":42,"evidence_quote":"不吃香菜"}]"""

        val proposal =
            CompanionMemoryProposalExtractor.parseValidatedProposals(
                response = response,
                conversationId = "chat-1",
                messages = listOf(userMessage),
                nowMs = 2_000L,
            ).single()

        assertEquals("dislikes", proposal.predicate)
    }

    @Test
    fun rejectsSensitiveValueEvenWithExactEvidence() {
        val secretMessage = userMessage.copy(content = "我的密码是 123456", messageId = 43L)
        val response =
            """[{"action":"CREATE","scope":"USER","type":"FACT","predicate":"explicit_note","value":"密码是 123456","evidence_message_id":43,"evidence_quote":"密码是 123456"}]"""

        val proposals =
            CompanionMemoryProposalExtractor.parseValidatedProposals(
                response = response,
                conversationId = "chat-1",
                messages = listOf(secretMessage),
                nowMs = 2_000L,
            )

        assertTrue(proposals.isEmpty())
    }
}
