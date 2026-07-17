package com.ai.assistance.operit.core.chat

object CompanionMemoryImportanceDetector {
    private val highValuePatterns =
        listOf(
            Regex("(?:请|一定要|必须)?(?:记住|记下来|别忘了|不要忘记)"),
            Regex("(?:这件事|这个|这对我)?(?:很|非常|特别|极其)?重要"),
            Regex("(?:我叫|我的名字|我的姓名|我的生日|我家在|我来自|我住在)"),
            Regex("(?:我的职业|我从事|我在.{1,32}(?:工作|上班)|我就读于|我的专业)"),
            Regex("(?:过敏|病史|确诊|患有|长期服用|不能吃|忌口)"),
            Regex("(?:我(?:的)?|我有(?:一个|个)?)(?:妈妈|爸爸|母亲|父亲|妻子|丈夫|老婆|老公|女朋友|男朋友|儿子|女儿|孩子)"),
            Regex("(?:我的目标是|我长期打算|我一直想|我决定以后|我计划长期)"),
            Regex("(?:我每天|我每周|我每个月|我一直都|我的习惯是)"),
            Regex("(?:以后叫我|你可以叫我|请叫我|我们约好|你答应我|答应我)"),
            Regex("(?:以后)?(?:别|不要).{2,72}"),
        )

    fun isHighValue(content: String): Boolean {
        val normalized = content.trim()
        if (normalized.length < 2) return false
        return highValuePatterns.any { it.containsMatchIn(normalized) }
    }
}
