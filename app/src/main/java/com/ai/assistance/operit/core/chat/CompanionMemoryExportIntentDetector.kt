package com.ai.assistance.operit.core.chat

object CompanionMemoryExportIntentDetector {
    private val chineseExport = Regex("(?:导出|备份|打包|生成|保存)(?:一份|一下|出来|成文件)?")
    private val chineseMemory = Regex("(?:记忆|关系档案|共同记忆|记住的内容)")
    private val chineseQuestion = Regex("^(?:能不能|可不可以|是否|怎么|如何|哪里|支持不支持)")
    private val englishExport = Regex("\\b(?:export|backup|back up|create an? archive)\\b", RegexOption.IGNORE_CASE)
    private val englishMemory = Regex("\\b(?:memory|memories|relationship profile)\\b", RegexOption.IGNORE_CASE)
    private val englishQuestion = Regex("^(?:can|could|does|do|how|where|is it possible)\\b", RegexOption.IGNORE_CASE)

    fun isExplicitRequest(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isBlank()) return false
        val chineseRequest =
            !chineseQuestion.containsMatchIn(normalized) &&
                chineseExport.containsMatchIn(normalized) &&
                chineseMemory.containsMatchIn(normalized)
        val englishRequest =
            !englishQuestion.containsMatchIn(normalized) &&
                englishExport.containsMatchIn(normalized) &&
                englishMemory.containsMatchIn(normalized)
        return chineseRequest || englishRequest
    }
}
