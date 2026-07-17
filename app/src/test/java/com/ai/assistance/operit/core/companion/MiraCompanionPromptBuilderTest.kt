package com.ai.assistance.operit.core.companion

import com.ai.assistance.operit.data.model.CompanionEventKind
import com.ai.assistance.operit.data.model.CompanionMemoryMetadata
import com.ai.assistance.operit.data.model.Memory
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiraCompanionPromptBuilderTest {
    @Test
    fun `prompt carries an idempotency marker and concrete reminder`() {
        val memory =
            Memory(
                uuid = "event-42",
                title = "Submit report",
                content = "The report is due before the morning stand-up.",
            )
        val metadata =
            CompanionMemoryMetadata(
                kind = CompanionEventKind.REMINDER,
                reminderText = "Remind the user to submit the report.",
            )

        val prompt =
            MiraCompanionPromptBuilder.build(
                memory = memory,
                metadata = metadata,
                nowMs = 1_773_801_600_000L,
                zoneId = ZoneId.of("Asia/Shanghai"),
            )

        assertTrue(prompt.contains(MiraCompanionContract.promptMarker("event-42")))
        assertTrue(prompt.contains("Remind the user to submit the report."))
        assertTrue(prompt.contains("The user did not send this text"))
        assertFalse(prompt.contains("null"))
    }
}
