package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.CompanionMemoryProposal
import com.ai.assistance.operit.data.model.CompanionMemorySourceKind
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class CompanionMemoryReceipt(
    val recordId: String,
    val conversationId: String,
    val summary: String,
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

    fun publishConfirmedHighValue(recordId: String, proposal: CompanionMemoryProposal) {
        if (
            recordId.isBlank() ||
                proposal.reviewAt != null ||
                proposal.importance < 0.85 ||
                proposal.sourceKind != CompanionMemorySourceKind.USER_EXPLICIT
        ) {
            return
        }
        val now = System.currentTimeMillis()
        synchronized(recentRecordIds) {
            recentRecordIds.entries.removeAll { now - it.value > DUPLICATE_WINDOW_MS }
            val previous = recentRecordIds[recordId]
            if (previous != null && now - previous <= DUPLICATE_WINDOW_MS) return
            recentRecordIds[recordId] = now
        }
        mutableEvents.tryEmit(
            CompanionMemoryReceipt(
                recordId = recordId,
                conversationId = proposal.conversationId,
                summary = proposal.value.trim().take(48),
            ),
        )
    }
}
