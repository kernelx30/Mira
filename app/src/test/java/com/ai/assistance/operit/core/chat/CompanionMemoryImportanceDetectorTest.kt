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
                "把我不吃香菜这件事存一下",
                "这条写进记忆，我周末一般睡到十点",
            )

        messages.forEach { content ->
            assertTrue("Expected high-value memory signal from: $content", CompanionMemoryImportanceDetector.isHighValue(content))
        }
    }

    @Test
    fun detectsStableInterestsWithoutAnExplicitRememberCommand() {
        listOf(
            "我喜欢玩原神",
            "我平时主要玩国服原神",
            "我的爱好是摄影和游戏",
            "我对科幻电影很感兴趣",
            "I enjoy playing Genshin Impact",
            "My favorite game is Genshin Impact",
        ).forEach { content ->
            assertTrue("Expected preference signal from: $content", CompanionMemoryImportanceDetector.isHighValue(content))
        }
    }

    @Test
    fun ignoresOrdinaryTransientChat() {
        assertFalse(CompanionMemoryImportanceDetector.isHighValue("今天天气还不错"))
        assertFalse(CompanionMemoryImportanceDetector.isHighValue("哈哈，确实挺有意思"))
    }

    @Test
    fun detectsExplicitSaveRequestsSeparatelyFromOtherImportantFacts() {
        assertTrue(CompanionMemoryImportanceDetector.isExplicitSaveRequest("把我不吃香菜这件事存一下"))
        assertTrue(CompanionMemoryImportanceDetector.isExplicitSaveRequest("这条写进记忆"))
        assertTrue(CompanionMemoryImportanceDetector.isExplicitSaveRequest("Please remember this preference"))
        assertFalse(CompanionMemoryImportanceDetector.isExplicitSaveRequest("我对花生过敏"))
        assertFalse(CompanionMemoryImportanceDetector.isExplicitSaveRequest("帮我保存一下这个文件"))
        assertFalse(CompanionMemoryImportanceDetector.isExplicitSaveRequest("Save this file to Downloads"))
    }

    @Test
    fun acceptsStableFactsAsMemoryEvidenceWithoutAnExplicitCommand() {
        assertTrue(CompanionMemoryImportanceDetector.isMemoryEvidence("我喜欢玩无畏契约"))
        assertTrue(CompanionMemoryImportanceDetector.isMemoryEvidence("记住我不吃香菜"))
        assertFalse(CompanionMemoryImportanceDetector.isMemoryEvidence("今天天气还不错"))
    }

    @Test
    fun explicitOptOutDoesNotCreateAMemoryCandidate() {
        listOf(
            "不要记住我喜欢原神",
            "别把这件事写进记忆",
            "我不想让你记住这句话",
            "Do not remember this preference",
            "Don't save this in memory",
        ).forEach { content ->
            assertFalse(
                "Expected memory opt-out from: $content",
                CompanionMemoryImportanceDetector.isMemoryEvidence(content),
            )
            assertFalse(CompanionMemoryImportanceDetector.isExplicitSaveRequest(content))
        }
    }

    @Test
    fun doNotForgetRemainsAPositiveSaveRequest() {
        assertTrue(CompanionMemoryImportanceDetector.isExplicitSaveRequest("不要忘记我不吃香菜"))
        assertTrue(CompanionMemoryImportanceDetector.isExplicitSaveRequest("Don't forget my birthday"))
    }
}
