package com.ai.assistance.operit.ui.features.chat.viewmodel

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode

internal data class PendingWaifuMergeMessage(
    val sequenceId: Long,
    val chatId: String,
    val text: String,
    val visibleMessageTimestamp: Long,
)

internal fun buildWaifuMergeDispatchText(
    pendingMessages: List<PendingWaifuMergeMessage>,
    throughSequenceId: Long,
): String =
    pendingMessages
        .asSequence()
        .filter { it.sequenceId <= throughSequenceId }
        .sortedBy { it.sequenceId }
        .map { it.text.trim() }
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n")

internal fun recoverablePendingWaifuMessages(messages: List<ChatMessage>): List<ChatMessage> =
    messages
        .takeLastWhile { message ->
            message.sender == "user" &&
                message.displayMode == ChatMessageDisplayMode.PENDING_DISPATCH
        }
        .filter { message -> message.content.isNotBlank() }
