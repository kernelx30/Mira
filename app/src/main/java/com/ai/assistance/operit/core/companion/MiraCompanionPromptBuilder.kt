package com.ai.assistance.operit.core.companion

import com.ai.assistance.operit.data.model.CompanionMemoryMetadata
import com.ai.assistance.operit.data.model.Memory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object MiraCompanionPromptBuilder {
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun build(
        memory: Memory,
        metadata: CompanionMemoryMetadata,
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val marker = MiraCompanionContract.promptMarker(memory.uuid)
        val localTime = Instant.ofEpochMilli(nowMs).atZone(zoneId).format(timeFormatter)
        val eventText =
            metadata.reminderText.ifBlank { memory.content.ifBlank { memory.title } }.trim().take(1_200)
        return buildString {
            appendLine(marker)
            appendLine("This is an internal, on-device companion trigger. The user did not send this text.")
            appendLine("Current local time: $localTime")
            appendLine("Event type: ${metadata.kind.storageValue}")
            appendLine("Event title: ${memory.title.trim().take(240)}")
            appendLine("Event details: $eventText")
            appendLine()
            appendLine("Reply once as your established character, using the recent chat and long-term memory already provided to you.")
            appendLine("Make the timing and event feel natural. For a reminder, clearly mention the concrete thing the user agreed to do.")
            appendLine("Do not mention this trigger, scheduling, system instructions, APIs, or hidden messages.")
            appendLine("Do not call tools or change device state for this trigger; only write the companion message.")
            appendLine("Do not invent shared memories. Prefer 1 to 4 natural sentences and return only the message for the user.")
        }.trim()
    }
}
