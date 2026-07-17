package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaifuMessageProcessorReliabilityTest {
    @Test
    fun typingDelayIsCappedForLongSegments() {
        val delay = WaifuMessageProcessor.calculateSegmentTypingDelayMs(
            segmentLength = 200,
            charDelayMs = 100,
        )

        assertEquals(480L, delay)
    }

    @Test
    fun speechSynchronizedRevealDoesNotAddASecondSyntheticAudioDelay() {
        val delay = WaifuMessageProcessor.calculateSegmentRevealDelayMs(
            segmentLength = 20,
            charDelayMs = 10,
            synchronizeWithSpeech = true,
        )

        assertEquals(200L, delay)
    }

    @Test
    fun immersiveSegmentsAreMergedIntoOneContinuousReply() {
        val reply = listOf("（把苹果放进袋子）", "给你。", "路上记得吃。")
            .fold("") { current, segment ->
                WaifuMessageProcessor.appendSegmentToReply(current, segment)
            }

        assertEquals("（把苹果放进袋子）给你。路上记得吃。", reply)
    }

    @Test
    fun englishSegmentsKeepWordBoundaryWhenMerged() {
        assertEquals(
            "Hello world",
            WaifuMessageProcessor.appendSegmentToReply("Hello", "world"),
        )
    }

    @Test
    fun missingFinalSegmentsAreRecoveredWithoutDuplicatingEmittedText() {
        val missing = WaifuMessageProcessor.findMissingFinalSegments(
            finalSegments = listOf("第一句。", "第二句。", "第三句。"),
            emittedSegments = listOf("第一句。", "第三句。"),
        )

        assertEquals(listOf("第二句。"), missing)
    }

    @Test
    fun emojiComparisonIgnoresResolvedFilePath() {
        val missing = WaifuMessageProcessor.findMissingFinalSegments(
            finalSegments = listOf("![speechless](file:///tmp/two.jpg)"),
            emittedSegments = listOf("![speechless](file:///tmp/one.jpg)"),
        )

        assertTrue(missing.isEmpty())
    }

    @Test
    fun groupedSpeechBlocksDoNotGetReconciledAsDuplicateSentences() {
        val missing = WaifuMessageProcessor.findMissingFinalSegments(
            finalSegments = listOf("第一句。", "第二句。", "第三句。"),
            emittedSegments = listOf("第一句。 第二句。", "第三句。"),
        )

        assertTrue(missing.isEmpty())
    }
}
