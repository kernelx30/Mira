package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessage

internal data class ChatMessageVisualGroupPosition(
    val isStart: Boolean,
    val isEnd: Boolean,
)

internal object ChatMessageVisualGrouping {
    private const val LEGACY_SEGMENT_GROUP_WINDOW_MS = 30_000L

    fun position(
        messages: List<ChatMessage>,
        index: Int,
        separatorIndices: Set<Int> = emptySet(),
    ): ChatMessageVisualGroupPosition {
        val message = messages.getOrNull(index)
            ?: return ChatMessageVisualGroupPosition(isStart = true, isEnd = true)

        val groupedWithPrevious =
            index !in separatorIndices &&
                belongsToSameGroup(messages.getOrNull(index - 1), message)
        val groupedWithNext =
            index + 1 !in separatorIndices &&
                belongsToSameGroup(message, messages.getOrNull(index + 1))

        return ChatMessageVisualGroupPosition(
            isStart = !groupedWithPrevious,
            isEnd = !groupedWithNext,
        )
    }

    internal fun belongsToSameGroup(
        previous: ChatMessage?,
        current: ChatMessage?,
    ): Boolean {
        if (previous == null || current == null) return false
        if (previous.sender != current.sender) return false
        if (current.sender != "ai" && current.sender != "user") return false
        if (previous.roleName != current.roleName) return false

        return when {
            previous.sentAt > 0L && current.sentAt > 0L -> previous.sentAt == current.sentAt
            previous.completedAt > 0L && current.completedAt > 0L ->
                previous.completedAt == current.completedAt
            else ->
                current.timestamp >= previous.timestamp &&
                    current.timestamp - previous.timestamp <= LEGACY_SEGMENT_GROUP_WINDOW_MS
        }
    }
}
