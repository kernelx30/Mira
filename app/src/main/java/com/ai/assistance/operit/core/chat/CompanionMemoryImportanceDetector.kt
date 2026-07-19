package com.ai.assistance.operit.core.chat

object CompanionMemoryImportanceDetector {
    private val explicitSaveNegationPatterns =
        listOf(
            Regex("(?:(?:不要(?!忘记))|(?:别(?!忘))|不用|无需|不必)[^。！？!?\\n]{0,40}(?:记住|记下来|记录下来|保存到记忆|写进记忆|收进记忆)"),
            Regex("(?:不想(?:让你|要你)?|不希望你)(?:再)?记住"),
            Regex("(?:do\\s+not|don't|never)\\s+(?:remember|note\\s+(?:this|that)\\s+down|store\\s+(?:this|that)\\s+in\\s+memory|save\\s+(?:this|that)\\s+in\\s+memory)", RegexOption.IGNORE_CASE),
        )

    private val explicitSavePatterns =
        listOf(
            Regex("(?:请|一定要|必须)?(?:记住|记下来|记录下来|保存到记忆|写进记忆|收进记忆|别忘了|不要忘记)"),
            Regex("(?:把|将)(?:我|我的)[^。！？!?\\n]{1,80}(?:存一下|存下来|保存一下)"),
            Regex("(?:please\\s+)?(?:remember\\s+(?:this|that)|note\\s+this\\s+down|store\\s+this\\s+in\\s+memory|save\\s+this\\s+(?:fact|preference|detail)(?:\\s+in\\s+memory)?|do\\s+not\\s+forget|don't\\s+forget)", RegexOption.IGNORE_CASE),
        )

    private val highValuePatterns =
        explicitSavePatterns +
        listOf(
            Regex("(?:这件事|这个|这对我)?(?:很|非常|特别|极其)?重要"),
            Regex("(?:我叫|我的名字|我的姓名|我的生日|我家在|我来自|我住在)"),
            Regex("(?:我是|我是一名|我的身份是|我今年\\d{1,3}岁|我\\d{1,3}岁)"),
            Regex("(?:我在|我住在|我生活在|我的家在)[^。！？!?\\n]{1,60}"),
            Regex("(?:我的职业|我从事|我在.{1,32}(?:工作|上班)|我就读于|我的专业)"),
            Regex("(?:我在读|我毕业于|我的学校|我的学历|我学的是|我主修)[^。！？!?\\n]{1,60}"),
            Regex("(?:过敏|病史|确诊|患有|长期服用|不能吃|忌口)"),
            Regex("(?:我(?:的)?|我有(?:一个|个)?)(?:妈妈|爸爸|母亲|父亲|妻子|丈夫|老婆|老公|女朋友|男朋友|儿子|女儿|孩子)"),
            Regex("(?:我有(?:一只|一条|一个|个)?(?:猫|狗|宠物)|我的(?:猫|狗|宠物)|我养的)[^。！？!?\\n]{0,60}"),
            Regex("(?:我的目标是|我长期打算|我一直想|我决定以后|我计划长期)"),
            Regex("(?:我每天|我每周|我每个月|我一直都|我的习惯是)"),
            Regex("(?:我的作息|我的日程|我通常在|我一般会|我每天都会|我周末会)[^。！？!?\\n]{1,80}"),
            // 稳定兴趣和偏好会直接影响后续陪伴话题，无需等待用户额外说“记住”。
            Regex("我(?:平时|一直|最近)?(?:喜欢|很喜欢|特别喜欢|爱玩|常玩|经常玩|主要玩|偏好)[^。！？!?\\n]{1,80}"),
            Regex("我(?:的)?(?:爱好|兴趣)(?:是|包括|有)[^。！？!?\\n]{1,80}"),
            Regex("我(?:对|对于)[^。！？!?\\n]{1,60}(?:感兴趣|有兴趣)"),
            Regex("我(?:平时)?(?:不喜欢|讨厌|不爱玩|不太喜欢)[^。！？!?\\n]{1,80}"),
            Regex("(?:我不想|我不希望|请不要|别对我|不要对我|我介意|我的底线是)[^。！？!?\\n]{1,80}"),
            Regex("(?:希望你|以后请你|你可以|你最好|跟我说话时|回复我时)[^。！？!?\\n]{1,80}"),
            Regex("(?:我的性格|我的脾气|我这个人|我属于)[^。！？!?\\n]{1,80}"),
            Regex("(?:I\\s+)?(?:like|love|enjoy|prefer|play)\\s+(?:to\\s+)?(?:play\\s+)?[^.!?\\n]{1,80}", RegexOption.IGNORE_CASE),
            Regex("(?:my\\s+)?(?:favorite\\s+(?:game|games|genre|hobby)|hobbies?)\\s+(?:are|is)\\s+[^.!?\\n]{1,80}", RegexOption.IGNORE_CASE),
            Regex("I\\s+(?:am\\s+)?interested\\s+in\\s+[^.!?\\n]{1,80}", RegexOption.IGNORE_CASE),
            Regex("I\\s+(?:do\\s+not|don't|dislike|hate)\\s+(?:like\\s+)?[^.!?\\n]{1,80}", RegexOption.IGNORE_CASE),
            Regex("(?:I\\s+am|I'm)\\s+(?:a|an)\\s+[^.!?\\n]{1,60}", RegexOption.IGNORE_CASE),
            Regex("(?:I\\s+live|I\\s+work|I\\s+study|I\\s+am\\s+from)\\s+[^.!?\\n]{1,60}", RegexOption.IGNORE_CASE),
            Regex("(?:my\\s+job|my\\s+occupation|my\\s+major|my\\s+school|my\\s+education)\\s+(?:is|are)\\s+[^.!?\\n]{1,60}", RegexOption.IGNORE_CASE),
            Regex("(?:my\\s+goal|my\\s+plan|my\\s+routine|my\\s+schedule|my\\s+boundary)\\s+(?:is|are)\\s+[^.!?\\n]{1,80}", RegexOption.IGNORE_CASE),
            Regex("(?:please\\s+)?(?:don't|do\\s+not|never)\\s+[^.!?\\n]{2,80}", RegexOption.IGNORE_CASE),
            Regex("(?:以后叫我|你可以叫我|请叫我|我们约好|你答应我|答应我)"),
            Regex("(?:以后)?(?:别|不要).{2,72}"),
        )

    fun isHighValue(content: String): Boolean {
        val normalized = content.trim()
        if (normalized.length < 2) return false
        if (explicitSaveNegationPatterns.any { it.containsMatchIn(normalized) }) return false
        return highValuePatterns.any { it.containsMatchIn(normalized) }
    }

    fun isExplicitSaveRequest(content: String): Boolean {
        val normalized = content.trim()
        if (normalized.length < 2) return false
        if (explicitSaveNegationPatterns.any { it.containsMatchIn(normalized) }) return false
        return explicitSavePatterns.any { it.containsMatchIn(normalized) }
    }

    /** True when a user message is valid evidence for an immediate memory write. */
    fun isMemoryEvidence(content: String): Boolean =
        isExplicitSaveRequest(content) || isHighValue(content)
}
