package com.ai.assistance.operit.data.model

data class PendingQueueMessageItem(
    val id: Long,
    val chatId: String,
    val text: String,
)
