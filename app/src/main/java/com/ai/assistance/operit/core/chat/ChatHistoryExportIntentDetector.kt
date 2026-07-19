package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.converter.ExportFormat

object ChatHistoryExportIntentDetector {
    private val chineseExport = Regex("(?:导出|备份|打包|生成|保存)(?:一份|一下|出来|成文件)?")
    private val chineseChat = Regex("(?:聊天记录|对话记录|当前对话|当前会话|这段对话|这次对话|本次对话|聊天内容|对话内容)")
    private val chineseQuestion = Regex("^(?:为什么|能不能|可不可以|是否|怎么|如何|哪里|支持不支持)")
    private val englishExport = Regex("\\b(?:export|backup|back up|save|create an? archive)\\b", RegexOption.IGNORE_CASE)
    private val englishChat = Regex("\\b(?:chat history|conversation|current chat|current conversation|all chats|all conversations)\\b", RegexOption.IGNORE_CASE)
    private val englishQuestion = Regex("^(?:why|can|could|does|do|how|where|is it possible)\\b", RegexOption.IGNORE_CASE)
    private val allChats = Regex("(?:全部|所有|全量).{0,12}(?:聊天记录|对话记录|聊天|对话)|(?:聊天记录|对话记录).{0,12}(?:全部|所有|全量)")
    private val allChatsEnglish = Regex("\\b(?:all|every)\\s+(?:chats?|conversations?|chat histories)\\b", RegexOption.IGNORE_CASE)

    fun isExplicitRequest(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isBlank()) return false
        val chineseRequest =
            !chineseQuestion.containsMatchIn(normalized) &&
                chineseExport.containsMatchIn(normalized) &&
                chineseChat.containsMatchIn(normalized)
        val englishRequest =
            !englishQuestion.containsMatchIn(normalized) &&
                englishExport.containsMatchIn(normalized) &&
                englishChat.containsMatchIn(normalized)
        return chineseRequest || englishRequest
    }

    fun requestsAllChats(text: String): Boolean =
        allChats.containsMatchIn(text) || allChatsEnglish.containsMatchIn(text)

    fun detectFormat(text: String): ExportFormat? {
        val normalized = text.trim()
        return when {
            Regex("(?i)(?:^|[^a-z])json(?:$|[^a-z])|原生备份").containsMatchIn(normalized) -> ExportFormat.JSON
            Regex("(?i)(?:^|[^a-z])txt(?:$|[^a-z])|纯文本").containsMatchIn(normalized) -> ExportFormat.TXT
            Regex("(?i)(?:^|[^a-z])html?(?:$|[^a-z])").containsMatchIn(normalized) -> ExportFormat.HTML
            Regex("(?i)markdown|(?:^|[^a-z])md(?:$|[^a-z])").containsMatchIn(normalized) -> ExportFormat.MARKDOWN
            else -> null
        }
    }
}
