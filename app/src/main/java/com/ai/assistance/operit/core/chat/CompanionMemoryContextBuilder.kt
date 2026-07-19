package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.CompanionMemoryPredicate
import com.ai.assistance.operit.data.model.CompanionRecordScope
import com.ai.assistance.operit.data.model.MemoryPerspective
import com.ai.assistance.operit.data.repository.CompanionMemoryRecallResult
import com.ai.assistance.operit.data.repository.CompanionMemoryRepository
import com.ai.assistance.operit.data.repository.decodedValue
import com.ai.assistance.operit.util.ChatUtils
import kotlinx.coroutines.CancellationException

object CompanionMemoryContextBuilder {
    private const val DEFAULT_TOKEN_BUDGET = 700

    suspend fun buildPromptNoteOrEmpty(
        useEnglish: Boolean,
        tokenBudget: Int = DEFAULT_TOKEN_BUDGET,
        authoritativeUserName: String = "",
        authoritativePreferredAddress: String = "",
        loadRecall: suspend () -> CompanionMemoryRecallResult,
        onFailure: (Throwable) -> Unit = {},
    ): String =
        try {
            buildPromptNote(
                recall = loadRecall(),
                useEnglish = useEnglish,
                tokenBudget = tokenBudget,
                authoritativeUserName = authoritativeUserName,
                authoritativePreferredAddress = authoritativePreferredAddress,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            onFailure(error)
            ""
        }

    fun buildPromptNote(
        recall: CompanionMemoryRecallResult,
        useEnglish: Boolean,
        tokenBudget: Int = DEFAULT_TOKEN_BUDGET,
        authoritativeUserName: String = "",
        authoritativePreferredAddress: String = "",
    ): String {
        if (recall.records.isEmpty() && recall.episodes.isEmpty()) return ""
        val header =
            if (useEnglish) {
                "[Local companion memory]\nUser-explicit facts outrank inferred facts. Items marked shared-by-user are second-hand: never describe them as your own experience. Use only relevant items and never invent missing history."
            } else {
                "【本地伴侣记忆】\n用户明确说过的事实优先于推断；只在相关时自然使用，不补造缺失经历。"
            }
        val lines = mutableListOf<String>()
        val eligibleRecords =
            recall.records.filterNot { record ->
                conflictsWithAuthoritativeIdentity(
                    record = record,
                    authoritativeUserName = authoritativeUserName,
                    authoritativePreferredAddress = authoritativePreferredAddress,
                )
            }
        eligibleRecords.forEach { record ->
            val line = formatRecord(record, useEnglish)
            if (fitsBudget(header, lines, line, tokenBudget)) lines += line
        }
        val recalledValues = eligibleRecords.mapTo(hashSetOf()) { record ->
            CompanionMemoryRepository.normalizeValue(record.decodedValue())
        }
        recall.episodes
            .filterNot { episode ->
                CompanionMemoryRepository.normalizeValue(episode.summary) in recalledValues
            }
            .forEach { episode ->
            val line =
                if (useEnglish) {
                    "- recent episode | ${episode.summary.take(220)}"
                } else {
                    "- 近期经历 | ${episode.summary.take(220)}"
                }
            if (fitsBudget(header, lines, line, tokenBudget)) lines += line
            }
        return if (lines.isEmpty()) "" else header + "\n" + lines.joinToString("\n")
    }

    private fun formatRecord(record: CompanionMemoryRecordEntity, useEnglish: Boolean): String {
        val source = if (record.perspective == MemoryPerspective.USER_SHARED.name) {
            if (useEnglish) "shared-by-user" else "用户转告"
        } else if (record.sourceKind == "USER_EXPLICIT") {
            if (useEnglish) "explicit" else "用户明确"
        } else {
            if (useEnglish) "inferred" else "待核实"
        }
        return "- ${record.type.lowercase()} | ${record.subjectKey}.${record.predicate} = ${record.decodedValue().take(180)} | $source"
    }

    private fun conflictsWithAuthoritativeIdentity(
        record: CompanionMemoryRecordEntity,
        authoritativeUserName: String,
        authoritativePreferredAddress: String,
    ): Boolean {
        if (record.scope != CompanionRecordScope.USER.name) return false
        val configuredValue =
            when (CompanionMemoryPredicate.canonicalize(record.predicate)) {
                "name" -> authoritativeUserName
                "preferred_address" -> authoritativePreferredAddress
                else -> return false
            }.trim()
        if (configuredValue.isBlank()) return false
        return CompanionMemoryRepository.normalizeValue(record.decodedValue()) !=
            CompanionMemoryRepository.normalizeValue(configuredValue)
    }

    private fun fitsBudget(
        header: String,
        existingLines: List<String>,
        nextLine: String,
        tokenBudget: Int,
    ): Boolean {
        val candidate = buildString {
            append(header)
            existingLines.forEach { append('\n').append(it) }
            append('\n').append(nextLine)
        }
        return ChatUtils.estimateTokenCount(candidate) <= tokenBudget.coerceAtLeast(128)
    }
}
