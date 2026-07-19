package com.ai.assistance.operit.core.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryExportIntentDetectorTest {
    @Test
    fun `explicit export commands are accepted`() {
        assertTrue(CompanionMemoryExportIntentDetector.isExplicitRequest("把我的记忆导出来"))
        assertTrue(CompanionMemoryExportIntentDetector.isExplicitRequest("请备份一下共同记忆"))
        assertTrue(CompanionMemoryExportIntentDetector.isExplicitRequest("export my memories"))
    }

    @Test
    fun `capability questions and unrelated exports are rejected`() {
        assertFalse(CompanionMemoryExportIntentDetector.isExplicitRequest("能不能导出记忆？"))
        assertFalse(CompanionMemoryExportIntentDetector.isExplicitRequest("怎么备份记忆"))
        assertFalse(CompanionMemoryExportIntentDetector.isExplicitRequest("导出聊天记录"))
        assertFalse(CompanionMemoryExportIntentDetector.isExplicitRequest("把记忆删掉"))
    }
}
