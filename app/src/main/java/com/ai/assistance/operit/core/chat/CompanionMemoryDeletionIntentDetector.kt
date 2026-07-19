package com.ai.assistance.operit.core.chat

object CompanionMemoryDeletionIntentDetector {
    // Bulk-delete verbs must participate in negation detection or "不要清空" becomes destructive.
    private val deletionNegationPatterns =
        listOf(
            Regex("(?:(?:不要(?!忘记))|(?:别(?!忘))|不用|无需|不必)[^。！？!?\\n]{0,40}(?:删除|删掉|移除|清掉|清空|清除|忘掉|撤回)"),
            Regex("(?:do\\s+not|don't|never)\\s+(?:delete|remove|erase|forget)", RegexOption.IGNORE_CASE),
        )

    private val informationalPatterns =
        listOf(
            Regex("(?:怎么|如何|怎样|哪里).{0,12}(?:删除|删掉|移除).{0,8}(?:记忆|记录)"),
            Regex("(?:有没有|是否有|支不支持|支持不支持).{0,12}(?:删除|删掉|移除).{0,8}(?:工具|功能|按钮|包)"),
            Regex("(?:删除|删掉|移除).{0,16}(?:有|有没有|是否有|对应).{0,12}(?:工具|功能|按钮|包)"),
            Regex("用户说.{0,12}(?:删除|删掉|移除).{0,20}(?:怎么|如何|会不会|是否|有|对应)"),
            Regex("(?:how (?:do|can) i|where (?:do|can) i).{0,20}(?:delete|remove|forget).{0,12}memor", RegexOption.IGNORE_CASE),
            Regex("(?:is there|does .* support).{0,20}(?:delete|remove).{0,12}(?:tool|feature|button|memory)", RegexOption.IGNORE_CASE),
        )

    private val deletionPatterns =
        listOf(
            Regex("(?:请|帮我|麻烦)?(?:把|将)?.{0,80}(?:记忆|记录).{0,16}(?:删除|删掉|移除|清掉|撤回)"),
            Regex("(?:请|帮我|麻烦)?(?:删除|删掉|移除|清掉|撤回).{0,80}(?:记忆|记录|那条|这条|刚才那条|上面那条)"),
            Regex("(?:请|帮我|麻烦)?(?:删除|删掉|移除|清掉|撤回).{0,80}(?:你记得的|关于我的|我的|我是|我不|我喜欢|我讨厌)"),
            Regex("(?:忘掉|别再记得|不要再记得|别记得|不要记得|别记住|不要记住).{0,80}"),
            Regex("(?:从|在).{0,12}(?:记忆|记录).{0,8}(?:里|中)?.{0,8}(?:删除|删掉|移除|清掉)"),
            Regex("""(?:please\s+)?(?:delete|remove|erase).{0,80}(?:memory|fact|detail|record|that|this|my)""", RegexOption.IGNORE_CASE),
            Regex("""(?:please\s+)?forget.{0,80}(?:that|this|what i said|about|memory)""", RegexOption.IGNORE_CASE),
            Regex("""(?:do not|don't|stop) remember(?:ing)?.{0,80}""", RegexOption.IGNORE_CASE),
        )

    private val referentialDeletionPatterns =
        listOf(
            Regex("(?:这个|这条|刚才那条|上面那条|刚才说的)(?:记忆|记录)"),
            Regex("(?:delete|remove|erase|forget).{0,20}(?:this|that)", RegexOption.IGNORE_CASE),
        )

    private val bulkDeletionPatterns =
        listOf(
            Regex("(?:删除|删掉|移除|清掉|清空|忘掉).{0,24}(?:全部|所有).{0,24}(?:记忆|记录|记忆库)"),
            Regex("(?:清空|清除).{0,16}(?:记忆库|全部记忆|所有记忆)"),
            Regex("(?:全部|所有).{0,12}(?:删除|删掉|移除|清掉|清空|清除)"),
            Regex("""(?:delete|remove|erase|clear).{0,40}(?:all|every).{0,40}(?:memories|memory|records)""", RegexOption.IGNORE_CASE),
        )

    private val bulkConfirmationPatterns =
        listOf(
            Regex("^(?:我)?(?:确认|确定)(?:要|继续)?(?:清空|删除|移除)(?:全部|所有)(?:的)?(?:记忆|记录|记忆库)[。.!！]?$"),
            Regex("^(?:我)?(?:确认|确定)(?:全部|所有)(?:的)?(?:删除|清空|移除)(?:记忆|记录|记忆库)?[。.!！]?$"),
            Regex("""^(?:i\s+)?confirm(?:\s+to)?\s+(?:clear|delete|remove)\s+(?:all|every)\s+(?:memories|memory|records)[.!]?$""", RegexOption.IGNORE_CASE),
        )

    fun isExplicitDeletionRequest(content: String): Boolean {
        val normalized = content.trim()
        if (
            normalized.isBlank() ||
                informationalPatterns.any { it.containsMatchIn(normalized) } ||
                deletionNegationPatterns.any { it.containsMatchIn(normalized) }
        ) {
            return false
        }
        return deletionPatterns.any { it.containsMatchIn(normalized) }
    }

    fun isBulkDeletionRequest(content: String): Boolean {
        val normalized = content.trim()
        return normalized.isNotBlank() &&
            !deletionNegationPatterns.any { it.containsMatchIn(normalized) } &&
            bulkDeletionPatterns.any { it.containsMatchIn(normalized) }
    }

    fun isBulkDeletionConfirmation(content: String): Boolean {
        val normalized = content.trim()
        return normalized.isNotBlank() && bulkConfirmationPatterns.any { it.matches(normalized) }
    }

    fun isReferentialDeletionRequest(content: String): Boolean {
        val normalized = content.trim()
        return normalized.isNotBlank() &&
            isExplicitDeletionRequest(normalized) &&
            referentialDeletionPatterns.any { it.containsMatchIn(normalized) }
    }
}
