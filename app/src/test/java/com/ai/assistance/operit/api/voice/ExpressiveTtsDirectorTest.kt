package com.ai.assistance.operit.api.voice

import com.ai.assistance.operit.data.model.SpeechEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveTtsDirectorTest {
    @Test
    fun instructionProvider_receivesDirectionWithoutReadingMarkup() {
        val requests =
            ExpressiveTtsDirector.plan(
                content = "<speech emotion=\"soft\" pace=\"0.90\">晚安，早点休息。</speech>",
                capabilities =
                    VoiceCapabilities(
                        supportsStyleInstruction = true,
                        supportsRate = true,
                        supportsPitch = true,
                    ),
            )

        val request = requests.single()
        assertEquals("晚安，早点休息。", request.text)
        assertEquals(SpeechEmotion.SOFT, request.direction.emotion)
        assertTrue(request.extraParams.getValue("instruction").contains("轻柔低声"))
        assertFalse(request.text.contains("speech"))
        assertEquals(0.90f, request.rate)
    }

    @Test
    fun plainProvider_doesNotReceiveUnsupportedParameters() {
        val request =
            ExpressiveTtsDirector.plan(
                content = "嘴硬归嘴硬，这块我给你看着。",
                capabilities = VoiceCapabilities.PLAIN,
            ).single()

        assertEquals(SpeechEmotion.TEASING, request.direction.emotion)
        assertNull(request.rate)
        assertNull(request.pitch)
        assertTrue(request.extraParams.isEmpty())
    }

    @Test
    fun director_capsRequestsAtFourSegments() {
        val content =
            (1..6).joinToString(separator = "") { index ->
                "<speech emotion=\"${if (index % 2 == 0) "warm" else "firm"}\">第${index}段。</speech>"
            }

        assertEquals(
            4,
            ExpressiveTtsDirector.plan(content, VoiceCapabilities.PROSODY_ONLY).size,
        )
    }
}
