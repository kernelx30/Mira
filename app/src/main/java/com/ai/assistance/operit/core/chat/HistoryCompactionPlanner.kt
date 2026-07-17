package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.ChatMessage

data class HistoryCompactionPlan(
    val summaryInputMessages: List<ChatMessage>,
    val beforeTimestamp: Long?,
    val afterTimestamp: Long?,
    val compactedMessageCount: Int,
    val protectedMessageCount: Int
)

/** Selects an old, completed conversation prefix and leaves the recent tail untouched. */
object HistoryCompactionPlanner {
    const val DEFAULT_PROTECTED_RECENT_MESSAGES = 8
    const val DEFAULT_MIN_COMPACTED_MESSAGES = 4

    fun plan(
        messages: List<ChatMessage>,
        protectedRecentMessages: Int = DEFAULT_PROTECTED_RECENT_MESSAGES,
        minCompactedMessages: Int = DEFAULT_MIN_COMPACTED_MESSAGES
    ): HistoryCompactionPlan? {
        if (messages.isEmpty()) return null

        val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }
        val activeStart = lastSummaryIndex + 1
        val conversationalIndices =
            (activeStart until messages.size).filter { index ->
                messages[index].sender == "user" || messages[index].sender == "ai"
            }
        val minimum = minCompactedMessages.coerceAtLeast(1)
        if (conversationalIndices.size < minimum) return null

        val desiredProtected = protectedRecentMessages.coerceAtLeast(0)
        val actualProtected = desiredProtected.coerceAtMost(
            (conversationalIndices.size - minimum).coerceAtLeast(0)
        )
        val protectedStartIndex =
            if (actualProtected == 0) {
                messages.size
            } else {
                conversationalIndices[conversationalIndices.size - actualProtected]
            }

        val compactedEndIndex = conversationalIndices
            .asReversed()
            .firstOrNull { index ->
                index < protectedStartIndex && messages[index].sender == "ai"
            }
            ?.plus(1)
            ?: return null

        val compactedCount = conversationalIndices.count { it < compactedEndIndex }
        if (compactedCount < minimum) return null

        val summaryInput = buildList {
            if (lastSummaryIndex >= 0) add(messages[lastSummaryIndex])
            addAll(messages.subList(activeStart, compactedEndIndex))
        }
        val protectedCount = conversationalIndices.count { it >= compactedEndIndex }

        return HistoryCompactionPlan(
            summaryInputMessages = summaryInput,
            beforeTimestamp = messages.getOrNull(compactedEndIndex - 1)?.timestamp,
            afterTimestamp = messages.getOrNull(compactedEndIndex)?.timestamp,
            compactedMessageCount = compactedCount,
            protectedMessageCount = protectedCount
        )
    }
}
