package com.ai.assistance.operit.services.floating

import org.junit.Assert.assertEquals
import org.junit.Test

class MiraFloatingTranscriptPolicyTest {
    @Test
    fun keepsLastThreeMeaningfulConversationMessages() {
        val result = MiraFloatingTranscriptPolicy.latestConversation(
            listOf(
                FloatingTranscriptSource("system", "hidden"),
                FloatingTranscriptSource("user", "older"),
                FloatingTranscriptSource("ai", "   "),
                FloatingTranscriptSource("ai", "first reply"),
                FloatingTranscriptSource("user", "second question"),
                FloatingTranscriptSource("ai", "latest reply"),
            )
        )

        assertEquals(3, result.size)
        assertEquals("first reply", result[0].text)
        assertEquals("second question", result[1].text)
        assertEquals("latest reply", result[2].text)
    }

    @Test
    fun normalizesMarkupAndMultilineText() {
        val result = MiraFloatingTranscriptPolicy.latestConversation(
            listOf(FloatingTranscriptSource("ai", "<speech>hello</speech>\n  Mira"))
        )

        assertEquals("hello Mira", result.single().text)
    }
}
