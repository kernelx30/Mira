package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.repository.CompanionMemoryRepository
import com.ai.assistance.operit.data.repository.decodedLabel
import com.ai.assistance.operit.data.repository.decodedValue

data class CompanionMemoryManagementMatch(
    val record: CompanionMemoryRecordEntity,
    val score: Double,
)

object CompanionMemoryManagementMatcher {
    private const val MIN_MATCH_SCORE = 0.55
    private val commandPrefix = Regex("^(?:请|帮我|麻烦)?(?:你)?(?:把|将)?")
    private val chineseCommandWords =
        Regex(
            "(?:关于|相关|这条|那条|刚才那条|上面那条|记忆里|记忆中|记忆|记录|删除|删掉|移除|清掉|撤回|忘掉|别再记得|不要再记得|别记得|不要记得|别记住|不要记住)",
        )
    private val englishCommandWords =
        Regex("\\b(?:please|delete|remove|erase|forget|memory|record|this|that)\\b", RegexOption.IGNORE_CASE)

    fun rank(
        records: List<CompanionMemoryRecordEntity>,
        query: String,
        limit: Int = 5,
    ): List<CompanionMemoryManagementMatch> {
        val cleanedQuery =
            query
                .replace(commandPrefix, "")
                .replace(chineseCommandWords, " ")
                .replace(englishCommandWords, " ")
                .trim()
                .trimEnd('的')
                .ifBlank { query.trim() }
        val normalizedQuery = CompanionMemoryRepository.normalizeValue(cleanedQuery)
        val queryTerms = CompanionMemoryRepository.tokenize(cleanedQuery)
        if (normalizedQuery.isBlank() && queryTerms.isEmpty()) return emptyList()

        return records
            .asSequence()
            .mapNotNull { record ->
                val score = score(record, normalizedQuery, queryTerms)
                score.takeIf { it >= MIN_MATCH_SCORE }
                    ?.let { CompanionMemoryManagementMatch(record, it) }
            }
            .sortedWith(
                compareByDescending<CompanionMemoryManagementMatch> { it.score }
                    .thenByDescending { it.record.importance }
                    .thenByDescending { it.record.updatedAt },
            )
            .distinctBy { it.record.id }
            .take(limit.coerceIn(1, 20))
            .toList()
    }

    private fun score(
        record: CompanionMemoryRecordEntity,
        normalizedQuery: String,
        queryTerms: Set<String>,
    ): Double {
        val value = record.decodedValue()
        val label = record.decodedLabel().orEmpty()
        val normalizedValue = CompanionMemoryRepository.normalizeValue(value)
        val normalizedLabel = CompanionMemoryRepository.normalizeValue(label)
        val normalizedRecord =
            CompanionMemoryRepository.normalizeValue(
                "$label ${record.subjectKey} ${record.predicate} $value",
            )

        if (normalizedQuery.isNotBlank()) {
            if (normalizedQuery == normalizedValue || normalizedQuery == normalizedLabel) return 1.0
            if (normalizedValue.contains(normalizedQuery) || normalizedLabel.contains(normalizedQuery)) return 0.98
            if (
                normalizedValue.length >= 2 && normalizedQuery.contains(normalizedValue) ||
                    normalizedLabel.length >= 2 && normalizedQuery.contains(normalizedLabel)
            ) {
                return 0.96
            }
            if (normalizedRecord.contains(normalizedQuery)) return 0.94
        }

        if (queryTerms.isEmpty()) return 0.0
        val recordTerms = CompanionMemoryRepository.tokenize("$label ${record.predicate} $value")
        if (recordTerms.isEmpty()) return 0.0
        val intersectionSize = (queryTerms intersect recordTerms).size.toDouble()
        val coverage = intersectionSize / queryTerms.size.toDouble()
        val unionSize = (queryTerms union recordTerms).size.coerceAtLeast(1)
        val jaccard = intersectionSize / unionSize.toDouble()
        return coverage * 0.8 + jaccard * 0.2
    }
}
