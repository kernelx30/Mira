package com.ai.assistance.operit.ui.features.chat.viewmodel

import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatComposerSnapshot
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.services.core.remainingAttachmentsAfterConsume

internal data class RestoredComposerState(
    val attachments: List<AttachmentInfo>,
    val replyToMessage: ChatMessage?,
)

internal class PerChatComposerStateStore {
    private val attachmentsByChatId = mutableMapOf<String, List<AttachmentInfo>>()
    private val replyByChatId = mutableMapOf<String, ChatMessage>()
    private var activeChatId: String? = null

    @Synchronized
    fun switchTo(
        nextChatId: String?,
        currentAttachments: List<AttachmentInfo>,
        currentReply: ChatMessage?,
    ): RestoredComposerState? {
        if (nextChatId == activeChatId) return null

        if (
            activeChatId == null &&
                nextChatId != null &&
                nextChatId !in attachmentsByChatId &&
                nextChatId !in replyByChatId &&
                (currentAttachments.isNotEmpty() || currentReply != null)
        ) {
            activeChatId = nextChatId
            return RestoredComposerState(currentAttachments.toList(), currentReply)
        }

        activeChatId?.let { previousChatId ->
            if (currentAttachments.isEmpty()) {
                attachmentsByChatId.remove(previousChatId)
            } else {
                attachmentsByChatId[previousChatId] = currentAttachments.toList()
            }
            if (currentReply == null) {
                replyByChatId.remove(previousChatId)
            } else {
                replyByChatId[previousChatId] = currentReply
            }
        }

        activeChatId = nextChatId
        return RestoredComposerState(
            attachments = nextChatId?.let { attachmentsByChatId[it] }.orEmpty(),
            replyToMessage = nextChatId?.let { replyByChatId[it] },
        )
    }

    @Synchronized
    fun consume(chatId: String, snapshot: ChatComposerSnapshot): Boolean {
        val remaining =
            remainingAttachmentsAfterConsume(
                current = attachmentsByChatId[chatId].orEmpty(),
                consumed = snapshot.attachments,
            )
        if (remaining.isEmpty()) {
            attachmentsByChatId.remove(chatId)
        } else {
            attachmentsByChatId[chatId] = remaining
        }

        val storedReply = replyByChatId[chatId]
        if (
            storedReply?.timestamp == snapshot.replyToMessage?.timestamp &&
                storedReply?.sender == snapshot.replyToMessage?.sender
        ) {
            replyByChatId.remove(chatId)
        }
        return activeChatId == chatId
    }
}
