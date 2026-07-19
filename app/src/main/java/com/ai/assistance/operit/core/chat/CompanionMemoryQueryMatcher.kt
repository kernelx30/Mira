package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.repository.CompanionMemoryRepository
import com.ai.assistance.operit.data.repository.decodedLabel
import com.ai.assistance.operit.data.repository.decodedValue

data class CompanionMemoryQueryMatch(
    val record: CompanionMemoryRecordEntity,
    val score: Double,
)

object CompanionMemoryQueryMatcher {
    private const val MIN_SCORE = 0.48

    private val semanticGroups =
        listOf(
            setOf("老家", "家乡", "籍贯", "哪里人", "哪儿人", "来自哪里", "所在地", "hometown", "birthplace", "location"),
            setOf("生日", "出生日期", "哪天出生", "birthday", "birthdate", "birth_date"),
            setOf("工作", "职业", "做什么工作", "上班", "occupation", "job", "career", "company"),
            setOf("名字", "姓名", "叫什么", "name", "full_name"),
            setOf("称呼", "叫我", "怎么叫", "preferred_address", "nickname"),
            setOf("喜欢", "爱好", "偏好", "favorite", "likes", "prefers", "preference"),
            setOf("讨厌", "不喜欢", "避免", "忌讳", "dislikes", "avoids", "boundary"),
            setOf("习惯", "作息", "日常", "routine", "schedule", "habit"),
            setOf("家人", "家庭", "亲人", "family", "relative"),
            setOf("约定", "答应", "承诺", "promise", "commitment"),
        )

    fun rank(
        records: List<CompanionMemoryRecordEntity>,
        query: String,
        limit: Int,
    ): List<CompanionMemoryQueryMatch> {
        val normalizedQuery = CompanionMemoryRepository.normalizeValue(query)
        val queryTerms = CompanionMemoryRepository.tokenize(query)
        val activatedSemanticTerms =
            semanticGroups
                .filter { group -> group.any { normalizedQuery.contains(CompanionMemoryRepository.normalizeValue(it)) } }
                .flatten()
                .mapTo(linkedSetOf(), CompanionMemoryRepository::normalizeValue)

        return records
            .asSequence()
            .mapNotNull { record ->
                val score = score(record, normalizedQuery, queryTerms, activatedSemanticTerms)
                score.takeIf { it >= MIN_SCORE }?.let { CompanionMemoryQueryMatch(record, it) }
            }
            .sortedWith(
                compareByDescending<CompanionMemoryQueryMatch> { it.score }
                    .thenByDescending { it.record.importance }
                    .thenByDescending { it.record.lastConfirmedAt },
            )
            .distinctBy { it.record.id }
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    private fun score(
        record: CompanionMemoryRecordEntity,
        normalizedQuery: String,
        queryTerms: Set<String>,
        semanticTerms: Set<String>,
    ): Double {
        val searchable =
            "${record.subjectKey} ${record.predicate} ${record.decodedLabel().orEmpty()} " +
                "${record.normalizedValue} ${record.decodedValue()}"
        val normalizedRecord = CompanionMemoryRepository.normalizeValue(searchable)
        val recordTerms = CompanionMemoryRepository.tokenize(searchable)

        if (normalizedQuery.length >= 2 && normalizedRecord.contains(normalizedQuery)) return 1.0
        if (semanticTerms.any { it.length >= 2 && normalizedRecord.contains(it) }) return 0.94
        if (queryTerms.isEmpty() || recordTerms.isEmpty()) return 0.0

        val intersection = (queryTerms intersect recordTerms).size.toDouble()
        val coverage = intersection / queryTerms.size.toDouble()
        val jaccard = intersection / (queryTerms union recordTerms).size.coerceAtLeast(1).toDouble()
        return coverage * 0.8 + jaccard * 0.2
    }
}
