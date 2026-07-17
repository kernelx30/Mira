package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.CompanionMemoryProposal
import com.ai.assistance.operit.data.model.CompanionMemoryProposalAction
import com.ai.assistance.operit.data.model.CompanionMemoryPredicate
import com.ai.assistance.operit.data.model.CompanionMemorySourceKind
import com.ai.assistance.operit.data.model.CompanionMemoryType
import com.ai.assistance.operit.data.model.CompanionRecordScope
import com.ai.assistance.operit.data.repository.CompanionMemoryRepository

object CompanionMemoryRuleExtractor {
    private data class Rule(
        val regex: Regex,
        val scope: CompanionRecordScope,
        val type: CompanionMemoryType,
        val predicate: String,
        val importance: Double,
    )

    private val rules =
        listOf(
            Rule(Regex("(?:我叫|我的名字(?:是|叫)|我的姓名是)\\s*([^，。！？,.!?\\s]{1,24})"), CompanionRecordScope.USER, CompanionMemoryType.IDENTITY, "name", 0.95),
            Rule(Regex("(?:我的生日(?:是|在)?|我生日(?:是|在)?)\\s*([0-9０-９一二三四五六七八九十年月日号./\\-]{2,24})"), CompanionRecordScope.USER, CompanionMemoryType.IDENTITY, "birthday", 1.0),
            Rule(Regex("(?:我今年|我的年龄(?:是)?)\\s*([0-9０-９一二三四五六七八九十]{1,3}岁?)"), CompanionRecordScope.USER, CompanionMemoryType.IDENTITY, "age", 0.8),
            Rule(Regex("(?:我家在|我来自|我(?:现在|目前)?住在)\\s*([^，。！？,.!?]{2,48})"), CompanionRecordScope.USER, CompanionMemoryType.IDENTITY, "location", 0.9),
            Rule(Regex("(?:我的职业是|我(?:现在|目前)?从事|我做的是)\\s*([^，。！？,.!?]{2,48})"), CompanionRecordScope.USER, CompanionMemoryType.IDENTITY, "occupation", 0.9),
            Rule(Regex("我(?:现在|目前)?在\\s*([^，。！？,.!?]{2,48})(?:工作|上班)"), CompanionRecordScope.USER, CompanionMemoryType.FACT, "workplace", 0.85),
            Rule(Regex("(?:我就读于|我在)\\s*([^，。！？,.!?]{2,48})(?:上学|读书|读研|读博)"), CompanionRecordScope.USER, CompanionMemoryType.IDENTITY, "school", 0.85),
            Rule(Regex("(?:我(?:很|挺|最|特别)?喜欢|我爱)\\s*([^，。！？,.!?]{1,48})"), CompanionRecordScope.USER, CompanionMemoryType.PREFERENCE, "likes", 0.8),
            Rule(Regex("(?:我不喜欢|我讨厌|我受不了)\\s*([^，。！？,.!?]{1,48})"), CompanionRecordScope.USER, CompanionMemoryType.PREFERENCE, "dislikes", 0.9),
            Rule(Regex("我(?:现在|已经|以后)?不(?:再)?(?:吃|喝)\\s*([^，。！？,.!?]{1,48})"), CompanionRecordScope.USER, CompanionMemoryType.PREFERENCE, "dislikes", 0.9),
            Rule(Regex("我(?:对)?\\s*([^，。！？,.!?]{1,32})过敏"), CompanionRecordScope.USER, CompanionMemoryType.FACT, "health.allergy", 1.0),
            Rule(Regex("我(?:有|患有|被确诊为)\\s*([^，。！？,.!?]{1,48}(?:病|症|炎|障碍|综合征))"), CompanionRecordScope.USER, CompanionMemoryType.FACT, "health.condition", 1.0),
            Rule(Regex("我(?:每天|长期|现在)?(?:在)?(?:吃|服用)\\s*([^，。！？,.!?]{1,48}(?:药|片|胶囊))"), CompanionRecordScope.USER, CompanionMemoryType.ROUTINE, "health.medication", 0.95),
            Rule(Regex("(?:我的目标是|我长期打算|我一直想)\\s*([^，。！？,.!?]{2,72})"), CompanionRecordScope.USER, CompanionMemoryType.FACT, "long_term_goal", 0.9),
            Rule(Regex("(?:我每天|我每周|我每个月|我的习惯是)\\s*([^，。！？,.!?]{2,72})"), CompanionRecordScope.USER, CompanionMemoryType.ROUTINE, "routine", 0.85),
            Rule(Regex("(?:以后叫我|你可以叫我|请叫我)\\s*([^，。！？,.!?\\s]{1,24})"), CompanionRecordScope.RELATIONSHIP, CompanionMemoryType.RELATIONSHIP, "preferred_address", 0.9),
            Rule(Regex("(?:我们约好|你答应我|答应我)\\s*([^，。！？,.!?]{1,72})"), CompanionRecordScope.RELATIONSHIP, CompanionMemoryType.COMMITMENT, "commitment", 0.95),
            Rule(Regex("(?:以后)?(?:别|不要)\\s*([^，。！？,.!?]{2,72})"), CompanionRecordScope.USER, CompanionMemoryType.BOUNDARY, "boundary", 1.0),
            Rule(Regex("(?:请)?记住(?:：|:)?\\s*([^，。！？!?]{2,96})"), CompanionRecordScope.USER, CompanionMemoryType.FACT, "explicit_note", 0.85),
        )

    private val familyMemberRegex =
        Regex(
            "(?:我(?:的)?|我有(?:一个|个)?)(妈妈|爸爸|母亲|父亲|妻子|丈夫|老婆|老公|女朋友|男朋友|儿子|女儿|孩子)(?:叫|名字(?:是|叫))\\s*([^，。！？,.!?\\s]{1,24})"
        )

    private val familyRoleKeys =
        mapOf(
            "妈妈" to "mother",
            "母亲" to "mother",
            "爸爸" to "father",
            "父亲" to "father",
            "妻子" to "wife",
            "老婆" to "wife",
            "丈夫" to "husband",
            "老公" to "husband",
            "女朋友" to "girlfriend",
            "男朋友" to "boyfriend",
            "儿子" to "son",
            "女儿" to "daughter",
            "孩子" to "child",
        )

    fun extract(
        content: String,
        conversationId: String,
        messageTimestamp: Long,
        messageId: Long? = null,
    ): List<CompanionMemoryProposal> {
        val normalizedContent = content.trim()
        if (normalizedContent.isBlank()) return emptyList()
        val ruleProposals =
            rules.flatMap { rule ->
                rule.regex.findAll(normalizedContent).mapNotNull { match ->
                    val value =
                        match.groupValues.getOrNull(1)
                            ?.trim()
                            ?.trimEnd('了')
                            ?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                    if (
                        CompanionMemorySensitiveDataGuard.containsSensitiveData(value) ||
                            CompanionMemorySensitiveDataGuard.containsSensitiveData(match.value)
                    ) {
                        return@mapNotNull null
                    }
                    val predicate = CompanionMemoryPredicate.canonicalize(rule.predicate)
                    CompanionMemoryProposal(
                        action = CompanionMemoryProposalAction.CREATE,
                        scope = rule.scope,
                        type = rule.type,
                        subjectKey = if (rule.scope == CompanionRecordScope.RELATIONSHIP) "relationship" else "user",
                        predicate = predicate,
                        value = value,
                        normalizedValue = CompanionMemoryRepository.normalizeValue(value),
                        confidence = 0.99,
                        importance = rule.importance,
                        sourceKind = CompanionMemorySourceKind.USER_EXPLICIT,
                        conversationId = conversationId,
                        messageId = messageId,
                        messageTimestamp = messageTimestamp,
                        evidenceQuote = match.value,
                    )
                }.toList()
            }
        val familyProposals =
            familyMemberRegex.findAll(normalizedContent).mapNotNull { match ->
                val role = match.groupValues.getOrNull(1)?.trim().orEmpty()
                val name = match.groupValues.getOrNull(2)?.trim().orEmpty()
                val roleKey = familyRoleKeys[role] ?: return@mapNotNull null
                if (name.isBlank()) return@mapNotNull null
                val value = "${role}叫$name"
                if (CompanionMemorySensitiveDataGuard.containsSensitiveData(match.value)) {
                    return@mapNotNull null
                }
                CompanionMemoryProposal(
                    action = CompanionMemoryProposalAction.CREATE,
                    scope = CompanionRecordScope.USER,
                    type = CompanionMemoryType.FACT,
                    subjectKey = "user",
                    predicate = "family.$roleKey.name",
                    value = value,
                    normalizedValue = CompanionMemoryRepository.normalizeValue(value),
                    confidence = 0.99,
                    importance = 0.95,
                    sourceKind = CompanionMemorySourceKind.USER_EXPLICIT,
                    conversationId = conversationId,
                    messageId = messageId,
                    messageTimestamp = messageTimestamp,
                    evidenceQuote = match.value,
                )
            }.toList()
        return (ruleProposals + familyProposals)
            .distinctBy { "${it.scope}|${it.type}|${it.predicate}|${it.normalizedValue}" }
    }
}
