package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.CompanionMemoryProposal
import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.MemoryTriggerKind
import com.ai.assistance.operit.data.repository.decodedLabel
import com.ai.assistance.operit.data.repository.decodedValue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class CompanionMemoryReceiptKind {
    SAVED,
    DELETED,
}

data class CompanionMemoryReceipt(
    val recordId: String,
    val conversationId: String,
    val summary: String,
    val displayLabel: String?,
    val scope: String,
    val type: String,
    val evidenceQuote: String,
    val kind: CompanionMemoryReceiptKind,
)

object CompanionMemoryReceiptBus {
    private const val DUPLICATE_WINDOW_MS = 30_000L
    private val recentRecordIds = mutableMapOf<String, Long>()
    private val mutableEvents =
        MutableSharedFlow<CompanionMemoryReceipt>(
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val events = mutableEvents.asSharedFlow()

    fun publishConfirmed(recordId: String, proposal: CompanionMemoryProposal) {
        if (
            recordId.isBlank() ||
                !shouldPublishSavedReceipt(proposal)
        ) {
            return
        }
        val now = System.currentTimeMillis()
        synchronized(recentRecordIds) {
            recentRecordIds.entries.removeAll { now - it.value > DUPLICATE_WINDOW_MS }
            val dedupeKey = "${CompanionMemoryReceiptKind.SAVED}:$recordId"
            val previous = recentRecordIds[dedupeKey]
            if (previous != null && now - previous <= DUPLICATE_WINDOW_MS) return
            recentRecordIds[dedupeKey] = now
        }
        mutableEvents.tryEmit(
            CompanionMemoryReceipt(
                recordId = recordId,
                conversationId = proposal.conversationId,
                summary = proposal.value.trim().take(48),
                displayLabel = proposal.displayLabel?.trim()?.take(48),
                scope = proposal.scope.name,
                type = proposal.type.name,
                evidenceQuote = proposal.evidenceQuote.trim().take(500),
                kind = CompanionMemoryReceiptKind.SAVED,
            ),
        )
    }

    internal fun shouldPublishSavedReceipt(proposal: CompanionMemoryProposal): Boolean {
        return proposal.reviewAt == null &&
            proposal.triggerKind == MemoryTriggerKind.EXPLICIT_REQUEST
    }

    fun publishDeleted(
        record: CompanionMemoryRecordEntity,
        conversationId: String,
        evidenceQuote: String,
    ) {
        val now = System.currentTimeMillis()
        synchronized(recentRecordIds) {
            recentRecordIds.entries.removeAll { now - it.value > DUPLICATE_WINDOW_MS }
            val dedupeKey = "${CompanionMemoryReceiptKind.DELETED}:${record.id}"
            val previous = recentRecordIds[dedupeKey]
            if (previous != null && now - previous <= DUPLICATE_WINDOW_MS) return
            recentRecordIds[dedupeKey] = now
        }
        mutableEvents.tryEmit(
            CompanionMemoryReceipt(
                recordId = record.id,
                conversationId = conversationId,
                summary = record.decodedValue().trim().take(48),
                displayLabel = record.decodedLabel()?.take(48),
                scope = record.scope,
                type = record.type,
                evidenceQuote = evidenceQuote.trim().take(500),
                kind = CompanionMemoryReceiptKind.DELETED,
            ),
        )
    }
}
