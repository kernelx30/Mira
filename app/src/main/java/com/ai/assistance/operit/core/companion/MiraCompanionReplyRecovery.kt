package com.ai.assistance.operit.core.companion

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode

data class MiraRecoveredReply(
    val content: String,
    val triggerTimestamp: Long,
)

object MiraCompanionReplyRecovery {
    fun find(messages: List<ChatMessage>, memoryUuid: String): MiraRecoveredReply? {
        val marker = MiraCompanionContract.promptMarker(memoryUuid)
        val triggerIndex =
            messages.indexOfLast { message ->
                message.sender == "user" &&
                    message.displayMode == ChatMessageDisplayMode.HIDDEN_PLACEHOLDER &&
                    marker in message.content
            }
        if (triggerIndex < 0) return null
        val trigger = messages[triggerIndex]
        val replies =
            messages.drop(triggerIndex + 1)
                .takeWhile { it.sender != "user" }
                .filter {
                    it.sender == "ai" &&
                        it.completedAt > 0L &&
                        it.content.isNotBlank()
                }
        if (replies.isEmpty()) return null
        return MiraRecoveredReply(
            content = replies.joinToString("\n") { it.content },
            triggerTimestamp = trigger.timestamp,
        )
    }
}
