package com.ai.assistance.operit.services

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatComposerSnapshot

interface ChatServiceUiBridge {
    fun updateWebServerForCurrentChat(chatId: String)
    fun resetAttachmentPanelState()
    fun clearReplyToMessage()
    fun clearReplyToMessageIfMatches(expected: ChatMessage)
    fun consumeComposerSnapshot(chatId: String, snapshot: ChatComposerSnapshot)
    fun getReplyToMessage(): ChatMessage?
}

object EmptyChatServiceUiBridge : ChatServiceUiBridge {
    override fun updateWebServerForCurrentChat(chatId: String) = Unit

    override fun resetAttachmentPanelState() = Unit

    override fun clearReplyToMessage() = Unit

    override fun clearReplyToMessageIfMatches(expected: ChatMessage) = Unit

    override fun consumeComposerSnapshot(chatId: String, snapshot: ChatComposerSnapshot) = Unit

    override fun getReplyToMessage(): ChatMessage? = null
}
