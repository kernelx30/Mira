package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.SpeechEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechMarkupParserTest {
    @Test
    fun parse_stripsMarkupAndKeepsDirections() {
        val parsed =
            SpeechMarkupParser.parse(
                "<speech emotion=\"teasing\" intensity=\"0.55\" pace=\"1.02\">行，嘴硬归嘴硬。</speech>"
            )

        assertTrue(parsed.hasSpeechMarkup)
        assertEquals("行，嘴硬归嘴硬。", parsed.visibleText)
        assertEquals(SpeechEmotion.TEASING, parsed.segments.single().direction?.emotion)
        assertEquals(1.02f, parsed.segments.single().direction?.pace)
    }

    @Test
    fun parse_plainTextRemainsPlain() {
        val parsed = SpeechMarkupParser.parse("普通回复")

        assertFalse(parsed.hasSpeechMarkup)
        assertEquals("普通回复", parsed.visibleText)
        assertEquals(null, parsed.segments.single().direction)
    }

    @Test
    fun metadata_preservesVisibleTextAndResolvedDirections() {
        val prepared =
            SpeechContentMetadata.prepare(
                "<speech emotion=\"warm\">别怕，我在。</speech>"
            )

        assertEquals("别怕，我在。", prepared.visibleText)
        assertTrue(prepared.speechDirectionJson?.contains("WARM") == true)
    }

    @Test
    fun unclosedStreamingMarkup_neverLeaksTagIntoStoredText() {
        val prepared =
            SpeechContentMetadata.prepare(
                "<speech emotion=\"concerned\" pace=\"0.93\">你还好吗？"
            )

        assertEquals("你还好吗？", prepared.visibleText)
        assertTrue(prepared.speechDirectionJson?.contains("CONCERNED") == true)
    }

    @Test
    fun metadata_stripsSpeechMarkupButPreservesToolTrace() {
        val prepared =
            SpeechContentMetadata.prepare(
                """
                <think>需要启动目标应用。</think>
                <tool name="start_app"><param name="package_name">com.example.app</param></tool>
                <tool_result name="start_app" status="success"><content>{"success":true}</content></tool_result>
                <speech emotion="warm">已经打开了。</speech>
                """.trimIndent()
            )

        assertTrue(prepared.visibleText.contains("<think>需要启动目标应用。</think>"))
        assertTrue(prepared.visibleText.contains("<tool name=\"start_app\">"))
        assertTrue(prepared.visibleText.contains("<tool_result name=\"start_app\" status=\"success\">"))
        assertTrue(prepared.visibleText.contains("已经打开了。"))
        assertFalse(prepared.visibleText.contains("<speech"))

        val speechText =
            SpeechContentMetadata.decodeSegments(prepared.speechDirectionJson)
                .joinToString("\n") { it.text }
        assertEquals("已经打开了。", speechText)
        assertFalse(speechText.contains("start_app"))
        assertFalse(speechText.contains("com.example.app"))
        assertFalse(speechText.contains("success"))
        assertFalse(speechText.contains("需要启动"))
    }

    @Test
    fun parse_plainToolTrace_keepsDisplayTraceButExcludesItFromSpeech() {
        val parsed =
            SpeechMarkupParser.parse(
                """
                <think>检查目标状态。</think>
                <tool name="get_device_info"><param name="scope">basic</param></tool>
                <tool_result name="get_device_info" status="success"><content>{"model":"TARGET"}</content></tool_result>
                设备信息已经拿到了。
                """.trimIndent()
            )

        assertTrue(parsed.visibleText.contains("<tool name=\"get_device_info\">"))
        assertEquals("设备信息已经拿到了。", parsed.segments.single().text)
    }
}
