package com.ai.assistance.operit.core.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryImportanceDetectorTest {
    @Test
    fun detectsExplicitAndDurableHighValueFacts() {
        val messages =
            listOf(
                "这件事很重要，你要记住",
                "我对花生过敏",
                "我的职业是产品经理",
                "我女朋友叫小雨",
                "我的目标是明年考上研究生",
                "以后叫我阿策",
            )

        messages.forEach { content ->
            assertTrue("Expected high-value memory signal from: $content", CompanionMemoryImportanceDetector.isHighValue(content))
        }
    }

    @Test
    fun ignoresOrdinaryTransientChat() {
        assertFalse(CompanionMemoryImportanceDetector.isHighValue("今天天气还不错"))
        assertFalse(CompanionMemoryImportanceDetector.isHighValue("哈哈，确实挺有意思"))
    }
}
