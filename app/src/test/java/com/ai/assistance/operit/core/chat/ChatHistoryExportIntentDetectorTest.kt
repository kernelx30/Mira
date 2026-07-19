package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.converter.ExportFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryExportIntentDetectorTest {
    @Test
    fun `explicit chat export requests are accepted`() {
        assertTrue(ChatHistoryExportIntentDetector.isExplicitRequest("导出聊天记录"))
        assertTrue(ChatHistoryExportIntentDetector.isExplicitRequest("把当前这次对话导出成txt文件"))
        assertTrue(ChatHistoryExportIntentDetector.isExplicitRequest("export the current conversation as JSON"))
    }

    @Test
    fun `questions and memory exports are not treated as chat exports`() {
        assertFalse(ChatHistoryExportIntentDetector.isExplicitRequest("为什么不能直接导出聊天记录"))
        assertFalse(ChatHistoryExportIntentDetector.isExplicitRequest("怎么导出聊天记录"))
        assertFalse(ChatHistoryExportIntentDetector.isExplicitRequest("导出我的记忆"))
    }

    @Test
    fun `scope and format come from the user wording`() {
        assertTrue(ChatHistoryExportIntentDetector.requestsAllChats("备份所有聊天记录"))
        assertFalse(ChatHistoryExportIntentDetector.requestsAllChats("导出当前对话"))
        assertEquals(ExportFormat.TXT, ChatHistoryExportIntentDetector.detectFormat("导出成txt"))
        assertEquals(ExportFormat.JSON, ChatHistoryExportIntentDetector.detectFormat("导出 JSON"))
        assertEquals(ExportFormat.MARKDOWN, ChatHistoryExportIntentDetector.detectFormat("保存为md"))
    }
}
