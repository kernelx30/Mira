package com.ai.assistance.operit.data.model

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanionMemoryMetadataTest {
    @Test
    fun `property values round trip preserves companion event`() {
        val metadata =
            CompanionMemoryMetadata(
                kind = CompanionEventKind.PROMISE,
                eventAtMs = 1_800_000_000_000L,
                status = CompanionEventStatus.PENDING,
                reminderText = "Do not forget the report tomorrow morning.",
                characterId = "zero",
                characterName = "Zero",
                chatId = "chat-1",
                notifiedAtMs = 1_799_999_000_000L,
                nextAttemptAtMs = 1_800_000_300_000L,
            )

        assertEquals(metadata, CompanionMemoryMetadata.fromPropertyValues(metadata.toPropertyValues()))
    }

    @Test
    fun `RFC3339 timestamp keeps explicit offset`() {
        val parsed =
            CompanionMemoryMetadata.parseEventAt(
                "2026-07-16T08:30:00+08:00",
                ZoneId.of("Asia/Shanghai"),
            )

        assertEquals(Instant.parse("2026-07-16T00:30:00Z").toEpochMilli(), parsed)
    }

    @Test
    fun `invalid timestamp does not create a reminder time`() {
        assertNull(CompanionMemoryMetadata.parseEventAt("sometime later"))
    }
}
