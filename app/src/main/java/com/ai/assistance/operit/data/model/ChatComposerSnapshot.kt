package com.ai.assistance.operit.data.model

/** Immutable input captured when the user presses send. */
data class ChatComposerSnapshot(
    val text: String,
    val attachments: List<AttachmentInfo> = emptyList(),
    val replyToMessage: ChatMessage? = null,
)
