package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.util.ConversationContentVisibility
import java.time.Instant
import java.time.ZoneId

internal object ChatTimelinePolicy {
    const val SEPARATOR_GAP_MS = 5 * 60 * 1000L

    fun activityAt(message: ChatMessage): Long {
        return when (message.sender) {
            "user" -> message.sentAt.takeIf { it > 0L } ?: message.timestamp
            "ai" ->
                message.completedAt.takeIf { it > 0L }
                    ?: message.sentAt.takeIf { it > 0L }
                    ?: message.timestamp
            else -> message.timestamp
        }
    }

    fun separatorIndices(
        messages: List<ChatMessage>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Set<Int> {
        val result = linkedSetOf<Int>()
        var previousVisibleMessage: ChatMessage? = null

        messages.forEachIndexed { index, message ->
            if (!isTimelineVisible(message)) return@forEachIndexed

            val previous = previousVisibleMessage
            if (
                previous == null ||
                    (!isSameAssistantTurn(previous, message) &&
                        shouldStartNewTimeBlock(previous, message, zoneId))
            ) {
                result += index
            }
            previousVisibleMessage = message
        }

        return result
    }

    fun shouldShowSeparatorAt(
        messages: List<ChatMessage>,
        index: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        val current = messages.getOrNull(index) ?: return false
        if (!isTimelineVisible(current)) return false

        var previousIndex = index - 1
        while (previousIndex >= 0) {
            val previous = messages[previousIndex]
            if (isTimelineVisible(previous)) {
                return !isSameAssistantTurn(previous, current) &&
                    shouldStartNewTimeBlock(previous, current, zoneId)
            }
            previousIndex -= 1
        }
        return true
    }

    internal fun isTimelineVisible(message: ChatMessage): Boolean {
        if (message.sender != "user" && message.sender != "ai") return false
        if (message.displayMode == ChatMessageDisplayMode.HIDDEN_PLACEHOLDER) return false
        if (message.sender == "user") {
            return ConversationContentVisibility.hasRenderableText(message.content)
        }

        val trimmedContent = message.content.trim()
        if (trimmedContent.isEmpty()) return false
        return ConversationContentVisibility.hasRenderableAssistantContent(trimmedContent)
    }

    private fun shouldStartNewTimeBlock(
        previous: ChatMessage,
        current: ChatMessage,
        zoneId: ZoneId,
    ): Boolean {
        val previousAt = activityAt(previous)
        val currentAt = activityAt(current)
        val previousDate = Instant.ofEpochMilli(previousAt).atZone(zoneId).toLocalDate()
        val currentDate = Instant.ofEpochMilli(currentAt).atZone(zoneId).toLocalDate()

        return previousDate != currentDate || currentAt - previousAt >= SEPARATOR_GAP_MS
    }

    private fun isSameAssistantTurn(previous: ChatMessage, current: ChatMessage): Boolean {
        if (previous.sender != "ai" || current.sender != "ai") return false

        return when {
            previous.sentAt > 0L && current.sentAt > 0L -> previous.sentAt == current.sentAt
            previous.completedAt > 0L && current.completedAt > 0L ->
                previous.completedAt == current.completedAt
            else -> false
        }
    }
}
