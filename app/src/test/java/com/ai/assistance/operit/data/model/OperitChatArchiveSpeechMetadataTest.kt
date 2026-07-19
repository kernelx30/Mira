package com.ai.assistance.operit.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperitChatArchiveSpeechMetadataTest {
    @Test
    fun archiveTypeKeepsMiraAndLegacyIdentifiersDistinct() {
        assertEquals("mira_chat_archive", OperitChatArchive.ARCHIVE_TYPE)
        assertEquals("operit_chat_archive", OperitChatArchive.LEGACY_ARCHIVE_TYPE)
        assertTrue(OperitChatArchive.CURRENT_FORMAT_VERSION >= 3)
    }

    @Test
    fun archivedMessageKeepsSpeechDirection() {
        val message =
            ChatMessage(
                sender = "ai",
                content = "今天辛苦了",
                timestamp = 100L,
                speechDirectionJson = "[{\"emotion\":\"WARM\"}]",
            )
        val archived = OperitArchivedMessage(baseMessage = message)

        val json = Json { encodeDefaults = true }
        val decoded = json.decodeFromString<OperitArchivedMessage>(json.encodeToString(archived))

        assertEquals(message.speechDirectionJson, decoded.baseMessage.speechDirectionJson)
    }
}
