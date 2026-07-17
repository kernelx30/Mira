package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ChatMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ConversationTimeContext {
    const val META_MESSAGE_TIMESTAMP_MS = "messageTimestampMs"
    const val META_MESSAGE_SENT_AT_MS = "messageSentAtMs"
    const val META_MESSAGE_COMPLETED_AT_MS = "messageCompletedAtMs"
    const val META_MESSAGE_ACTIVITY_AT_MS = "messageActivityAtMs"
    const val META_SOURCE_SENDER = "sourceSender"
    const val META_IS_CURRENT_REQUEST = "isCurrentRequest"

    private val timestampFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun metadataFor(message: ChatMessage): Map<String, Any?> {
        val activityAt =
            when (message.sender) {
                "user" -> message.sentAt.takeIf { it > 0L } ?: message.timestamp
                "ai" ->
                    message.completedAt.takeIf { it > 0L }
                        ?: message.sentAt.takeIf { it > 0L }
                        ?: message.timestamp
                else -> message.timestamp
            }

        return mapOf(
            META_MESSAGE_TIMESTAMP_MS to message.timestamp,
            META_MESSAGE_SENT_AT_MS to message.sentAt,
            META_MESSAGE_COMPLETED_AT_MS to message.completedAt,
            META_MESSAGE_ACTIVITY_AT_MS to activityAt,
            META_SOURCE_SENDER to message.sender,
        )
    }

    fun markCurrentRequest(
        chatHistory: List<PromptTurn>,
        messageTimestamp: Long?,
    ): List<PromptTurn> {
        if (messageTimestamp == null) {
            return chatHistory
        }

        return chatHistory.map { turn ->
            val turnTimestamp =
                (turn.metadata[META_MESSAGE_TIMESTAMP_MS] as? Number)?.toLong()
            val sourceSender = turn.metadata[META_SOURCE_SENDER] as? String
            if (turnTimestamp == messageTimestamp && sourceSender == "user") {
                turn.copy(metadata = turn.metadata + (META_IS_CURRENT_REQUEST to true))
            } else {
                turn
            }
        }
    }

    fun buildPromptNote(
        chatHistory: List<PromptTurn>,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val timedTurns =
            chatHistory.mapIndexedNotNull { index, turn ->
                val activityAt =
                    (turn.metadata[META_MESSAGE_ACTIVITY_AT_MS] as? Number)?.toLong()
                        ?: return@mapIndexedNotNull null
                val sourceSender =
                    turn.metadata[META_SOURCE_SENDER] as? String
                        ?: return@mapIndexedNotNull null
                if (activityAt <= 0L) {
                    return@mapIndexedNotNull null
                }
                TimedTurn(
                    index = index,
                    turn = turn,
                    sourceSender = sourceSender,
                    activityAt = activityAt,
                )
            }

        val lastTimedTurn = timedTurns.lastOrNull()
        val currentStoredRequest =
            lastTimedTurn?.takeIf { candidate ->
                candidate.sourceSender == "user" &&
                    candidate.turn.metadata[META_IS_CURRENT_REQUEST] == true
            }
        val turnsBeforeCurrent =
            if (currentStoredRequest == null) {
                timedTurns
            } else {
                timedTurns.filter { it.index < currentStoredRequest.index }
            }
        val previousInteraction =
            turnsBeforeCurrent.lastOrNull { it.sourceSender == "user" || it.sourceSender == "ai" }
                ?: turnsBeforeCurrent.lastOrNull()
        val lastUser = turnsBeforeCurrent.lastOrNull { it.sourceSender == "user" }
        val lastAssistant = turnsBeforeCurrent.lastOrNull { it.sourceSender == "ai" }
        val requestAt = currentStoredRequest?.activityAt ?: nowMs
        val elapsedSincePrevious =
            previousInteraction?.let { (requestAt - it.activityAt).coerceAtLeast(0L) }

        return buildString {
            appendLine("[Local persisted conversation timing; timezone=${zoneId.id}]")
            appendLine("current_local_time=${formatTimestamp(nowMs, zoneId)}")
            appendLine(
                "current_request_recorded_at=${formatTimestamp(requestAt, zoneId)}; " +
                    "current_request_source=" +
                    if (currentStoredRequest == null) "runtime_request" else "persisted_turn"
            )
            appendLine(
                "previous_interaction_at=" +
                    (previousInteraction?.let { formatTimestamp(it.activityAt, zoneId) } ?: "none") +
                    "; elapsed_since_previous_interaction=" +
                    (elapsedSincePrevious?.let(::formatElapsed) ?: "none")
            )
            appendLine(
                "last_user_message_before_current_request_at=" +
                    (lastUser?.let { formatTimestamp(it.activityAt, zoneId) } ?: "none")
            )
            appendLine(
                "last_assistant_message_before_current_request_at=" +
                    (lastAssistant?.let { formatTimestamp(it.activityAt, zoneId) } ?: "none")
            )
            append(
                "Use the elapsed gap for conversational continuity and proactive behavior. " +
                    "Do not recite exact timestamps unless they are naturally relevant."
            )
        }
    }

    private fun formatTimestamp(timestampMs: Long, zoneId: ZoneId): String {
        return Instant.ofEpochMilli(timestampMs)
            .atZone(zoneId)
            .withNano(0)
            .format(timestampFormatter)
    }

    private fun formatElapsed(durationMs: Long): String {
        var remainingSeconds = durationMs.coerceAtLeast(0L) / 1000L
        val days = remainingSeconds / 86_400L
        remainingSeconds %= 86_400L
        val hours = remainingSeconds / 3_600L
        remainingSeconds %= 3_600L
        val minutes = remainingSeconds / 60L
        val seconds = remainingSeconds % 60L

        val parts = buildList {
            if (days > 0L) add("${days}d")
            if (hours > 0L) add("${hours}h")
            if (minutes > 0L) add("${minutes}m")
            if (seconds > 0L || isEmpty()) add("${seconds}s")
        }
        return parts.take(2).joinToString(" ")
    }

    private data class TimedTurn(
        val index: Int,
        val turn: PromptTurn,
        val sourceSender: String,
        val activityAt: Long,
    )
}
