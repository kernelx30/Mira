package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTimelinePolicyTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun activityTimeUsesSenderSpecificPersistedFields() {
        val user =
            message(
                sender = "user",
                timestamp = instant("2026-07-01T10:00:00Z"),
                sentAt = instant("2026-07-01T10:00:01Z"),
                completedAt = instant("2026-07-01T10:00:09Z"),
            )
        val assistant =
            message(
                sender = "ai",
                timestamp = instant("2026-07-01T10:01:00Z"),
                sentAt = instant("2026-07-01T10:01:01Z"),
                completedAt = instant("2026-07-01T10:01:09Z"),
            )

        assertEquals(user.sentAt, ChatTimelinePolicy.activityAt(user))
        assertEquals(assistant.completedAt, ChatTimelinePolicy.activityAt(assistant))
    }

    @Test
    fun firstVisibleMessageAndFiveMinuteGapStartTimeBlocks() {
        val messages =
            listOf(
                message("user", instant("2026-07-01T10:00:00Z")),
                message("ai", instant("2026-07-01T10:04:59Z")),
                message("user", instant("2026-07-01T10:09:59Z")),
            )

        assertEquals(setOf(0, 2), ChatTimelinePolicy.separatorIndices(messages, utc))
    }

    @Test
    fun dateChangeStartsTimeBlockEvenWhenGapIsShort() {
        val messages =
            listOf(
                message("user", instant("2026-07-01T23:59:00Z")),
                message("ai", instant("2026-07-02T00:01:00Z")),
            )

        assertEquals(setOf(0, 1), ChatTimelinePolicy.separatorIndices(messages, utc))
    }

    @Test
    fun hiddenAndStreamingPlaceholdersDoNotParticipate() {
        val messages =
            listOf(
                message(
                    sender = "user",
                    timestamp = instant("2026-07-01T10:00:00Z"),
                    displayMode = ChatMessageDisplayMode.HIDDEN_PLACEHOLDER,
                ),
                message(
                    sender = "ai",
                    timestamp = instant("2026-07-01T10:01:00Z"),
                    content = "",
                ),
                message("user", instant("2026-07-01T10:02:00Z")),
            )

        assertEquals(setOf(2), ChatTimelinePolicy.separatorIndices(messages, utc))
    }

    @Test
    fun pureAssistantToolAndThinkingBlocksDoNotParticipate() {
        val messages =
            listOf(
                message(
                    sender = "ai",
                    timestamp = instant("2026-07-01T10:00:00Z"),
                    content =
                        "<think>checking</think>" +
                            "<tool name=\"calendar\"><param name=\"date\">tomorrow</param></tool>",
                ),
                message("user", instant("2026-07-01T10:01:00Z")),
            )

        assertEquals(setOf(1), ChatTimelinePolicy.separatorIndices(messages, utc))
    }

    @Test
    fun assistantTextAroundToolBlocksStillParticipates() {
        val messages =
            listOf(
                message(
                    sender = "ai",
                    timestamp = instant("2026-07-01T10:00:00Z"),
                    content = "I will note that.<tool name=\"calendar\"></tool>",
                ),
                message("user", instant("2026-07-01T10:04:00Z")),
            )

        assertEquals(setOf(0), ChatTimelinePolicy.separatorIndices(messages, utc))
    }

    @Test
    fun regularHtmlContentStillParticipates() {
        val messages =
            listOf(
                message(
                    sender = "ai",
                    timestamp = instant("2026-07-01T10:00:00Z"),
                    content = "<b>visible</b>",
                )
            )

        assertEquals(setOf(0), ChatTimelinePolicy.separatorIndices(messages, utc))
    }

    @Test
    fun unclosedInternalBlockDoesNotParticipate() {
        val messages =
            listOf(
                message(
                    sender = "ai",
                    timestamp = instant("2026-07-01T10:00:00Z"),
                    content = "<think>still working",
                ),
                message("user", instant("2026-07-01T10:01:00Z")),
            )

        assertEquals(setOf(1), ChatTimelinePolicy.separatorIndices(messages, utc))
    }

    @Test
    fun standaloneCompanionStickerParticipates() {
        val messages =
            listOf(
                message(
                    sender = "ai",
                    timestamp = instant("2026-07-01T10:00:00Z"),
                    content =
                        "![happy](file:///data/user/0/com.ai.assistance.mira/files/custom_emoji/zero/happy/a.webp)",
                )
            )

        assertEquals(setOf(0), ChatTimelinePolicy.separatorIndices(messages, utc))
    }

    @Test
    fun splitAssistantMessagesWithSameRequestTimeRemainOneBlock() {
        val requestAt = instant("2026-07-01T10:00:00Z")
        val messages =
            listOf(
                message(
                    sender = "ai",
                    timestamp = requestAt,
                    sentAt = requestAt,
                    completedAt = instant("2026-07-01T10:01:00Z"),
                ),
                message(
                    sender = "ai",
                    timestamp = instant("2026-07-01T10:08:00Z"),
                    sentAt = requestAt,
                    completedAt = instant("2026-07-01T10:08:00Z"),
                ),
            )

        assertEquals(setOf(0), ChatTimelinePolicy.separatorIndices(messages, utc))
    }

    @Test
    fun lazySeparatorLookupMatchesFullTimelineCalculation() {
        val messages =
            listOf(
                message("user", instant("2026-07-01T10:00:00Z")),
                message(
                    sender = "ai",
                    timestamp = instant("2026-07-01T10:01:00Z"),
                    content = "<think>hidden</think>",
                ),
                message("ai", instant("2026-07-01T10:06:00Z")),
                message("user", instant("2026-07-01T10:07:00Z")),
            )

        val lazyIndices =
            messages.indices
                .filter { ChatTimelinePolicy.shouldShowSeparatorAt(messages, it, utc) }
                .toSet()

        assertEquals(ChatTimelinePolicy.separatorIndices(messages, utc), lazyIndices)
    }

    private fun message(
        sender: String,
        timestamp: Long,
        content: String = "visible",
        sentAt: Long = timestamp,
        completedAt: Long = if (sender == "ai") timestamp else 0L,
        displayMode: ChatMessageDisplayMode = ChatMessageDisplayMode.NORMAL,
    ): ChatMessage {
        return ChatMessage(
            sender = sender,
            content = content,
            timestamp = timestamp,
            sentAt = sentAt,
            completedAt = completedAt,
            displayMode = displayMode,
        )
    }

    private fun instant(value: String): Long = Instant.parse(value).toEpochMilli()
}
