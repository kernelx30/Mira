package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ChatMessage
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTimeContextTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun metadataUsesPersistedCompletionAndSendTimes() {
        val assistant =
            ChatMessage(
                sender = "ai",
                timestamp = instant("2026-07-01T08:00:00Z"),
                sentAt = instant("2026-07-01T08:00:01Z"),
                completedAt = instant("2026-07-01T08:00:07Z"),
            )
        val user =
            ChatMessage(
                sender = "user",
                timestamp = instant("2026-07-02T09:00:00Z"),
                sentAt = instant("2026-07-02T09:00:02Z"),
                completedAt = instant("2026-07-02T09:00:30Z"),
            )

        assertEquals(
            assistant.completedAt,
            ConversationTimeContext.metadataFor(assistant)[ConversationTimeContext.META_MESSAGE_ACTIVITY_AT_MS],
        )
        assertEquals(
            user.sentAt,
            ConversationTimeContext.metadataFor(user)[ConversationTimeContext.META_MESSAGE_ACTIVITY_AT_MS],
        )
    }

    @Test
    fun returningUserReceivesPreviousInteractionGap() {
        val rawHistory =
            listOf(
                turn("user", "good morning", "2026-07-01T08:00:00Z"),
                turn("ai", "Take care today.", "2026-07-01T09:00:00Z"),
                turn("user", "I'm back", "2026-07-03T11:00:00Z"),
            )
        val currentTimestamp = instant("2026-07-03T11:00:00Z")
        val history =
            ConversationTimeContext.markCurrentRequest(rawHistory, currentTimestamp)

        val note =
            ConversationTimeContext.buildPromptNote(
                chatHistory = history,
                nowMs = instant("2026-07-03T11:00:05Z"),
                zoneId = utc,
            )

        assertTrue(note.contains("current_request_source=persisted_turn"))
        assertTrue(note.contains("previous_interaction_at=2026-07-01T09:00:00Z"))
        assertTrue(note.contains("elapsed_since_previous_interaction=2d 2h"))
        assertTrue(note.contains("last_user_message_before_current_request_at=2026-07-01T08:00:00Z"))
        assertTrue(note.contains("last_assistant_message_before_current_request_at=2026-07-01T09:00:00Z"))
    }

    @Test
    fun proactiveRuntimeRequestUsesCurrentTimeAgainstStoredHistory() {
        val history =
            listOf(
                turn("user", "good night", "2026-07-01T22:00:00Z"),
                turn("ai", "Good night. See you tomorrow.", "2026-07-01T22:05:00Z"),
            )

        val note =
            ConversationTimeContext.buildPromptNote(
                chatHistory = history,
                nowMs = instant("2026-07-04T22:05:00Z"),
                zoneId = utc,
            )

        assertTrue(note.contains("current_request_source=runtime_request"))
        assertTrue(note.contains("previous_interaction_at=2026-07-01T22:05:00Z"))
        assertTrue(note.contains("elapsed_since_previous_interaction=3d"))
    }

    @Test
    fun explicitCurrentRequestMarkerSurvivesContentProjection() {
        val timestamp = instant("2026-07-03T11:00:00Z")
        val original =
            PromptTurn(
                kind = PromptTurnKind.USER,
                content = "![image](image://current)",
                metadata =
                    mapOf(
                        ConversationTimeContext.META_MESSAGE_TIMESTAMP_MS to timestamp,
                        ConversationTimeContext.META_MESSAGE_ACTIVITY_AT_MS to timestamp,
                        ConversationTimeContext.META_SOURCE_SENDER to "user",
                    ),
            )
        val marked =
            ConversationTimeContext.markCurrentRequest(
                chatHistory = listOf(original),
                messageTimestamp = timestamp,
            ).single().copy(content = "[Earlier image omitted]")

        val note =
            ConversationTimeContext.buildPromptNote(
                chatHistory = listOf(marked),
                nowMs = instant("2026-07-03T11:00:01Z"),
                zoneId = utc,
            )

        assertTrue(note.contains("current_request_source=persisted_turn"))
        assertTrue(note.contains("previous_interaction_at=none"))
    }

    @Test
    fun unmarkedUserTurnRemainsHistorical() {
        val history = listOf(turn("user", "same text", "2026-07-01T08:00:00Z"))

        val note =
            ConversationTimeContext.buildPromptNote(
                chatHistory = history,
                nowMs = instant("2026-07-04T08:00:00Z"),
                zoneId = utc,
            )

        assertTrue(note.contains("current_request_source=runtime_request"))
        assertTrue(note.contains("previous_interaction_at=2026-07-01T08:00:00Z"))
        assertTrue(note.contains("elapsed_since_previous_interaction=3d"))
    }

    @Test
    fun bridgedRoleMessageKeepsAssistantTimingIdentity() {
        val bridgedAssistant =
            PromptTurn(
                kind = PromptTurnKind.USER,
                content = "[From role: Lin]\nI will wait here.",
                metadata =
                    mapOf(
                        ConversationTimeContext.META_MESSAGE_ACTIVITY_AT_MS to
                            instant("2026-07-02T10:00:00Z"),
                        ConversationTimeContext.META_SOURCE_SENDER to "ai",
                    ),
            )
        val currentTimestamp = instant("2026-07-03T10:00:00Z")
        val currentUser = turn("user", "are you there", "2026-07-03T10:00:00Z")
        val history =
            ConversationTimeContext.markCurrentRequest(
                chatHistory = listOf(bridgedAssistant, currentUser),
                messageTimestamp = currentTimestamp,
            )

        val note =
            ConversationTimeContext.buildPromptNote(
                chatHistory = history,
                nowMs = instant("2026-07-03T10:00:01Z"),
                zoneId = utc,
            )

        assertTrue(note.contains("last_assistant_message_before_current_request_at=2026-07-02T10:00:00Z"))
        assertTrue(note.contains("last_user_message_before_current_request_at=none"))
        assertTrue(note.contains("elapsed_since_previous_interaction=1d"))
    }

    private fun turn(sender: String, content: String, timestamp: String): PromptTurn {
        val kind = if (sender == "ai") PromptTurnKind.ASSISTANT else PromptTurnKind.USER
        val timestampMs = instant(timestamp)
        return PromptTurn(
            kind = kind,
            content = content,
            metadata =
                mapOf(
                    ConversationTimeContext.META_MESSAGE_TIMESTAMP_MS to timestampMs,
                    ConversationTimeContext.META_MESSAGE_ACTIVITY_AT_MS to timestampMs,
                    ConversationTimeContext.META_SOURCE_SENDER to sender,
                ),
        )
    }

    private fun instant(value: String): Long = Instant.parse(value).toEpochMilli()
}
