package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsSegmenterTest {
    @Test
    fun decimalNumber_isNotSplitByDot() {
        assertEquals(listOf("价格是 12.25 元。"), TtsSegmenter.split("价格是 12.25 元。"))
    }

    @Test
    fun versionNumber_isNotSplitByDot() {
        assertEquals(listOf("当前版本 v1.2 已发布。"), TtsSegmenter.split("当前版本 v1.2 已发布。"))
    }

    @Test
    fun sentenceEndingDot_stillSplitsNormally() {
        assertEquals(listOf("第一句.", "第二句"), TtsSegmenter.split("第一句.第二句"))
    }

    @Test
    fun naturalBlocks_mergeShortSentences() {
        assertEquals(
            listOf("第一句。 第二句。", "第三句。"),
            TtsSegmenter.splitNaturalBlocks("第一句。第二句。第三句。"),
        )
    }

    @Test
    fun naturalBlockBuffer_emitsAfterTargetLength() {
        val buffer =
            TtsNaturalBlockBuffer(
                minBlockLength = 12,
                maxBlockLength = 30,
                minSegmentCount = 3,
            )

        assertEquals(emptyList<String>(), buffer.append("第一句。"))
        assertEquals(listOf("第一句。 第二句比较长。"), buffer.append("第二句比较长。"))
        assertEquals(null, buffer.flush())
    }
}
