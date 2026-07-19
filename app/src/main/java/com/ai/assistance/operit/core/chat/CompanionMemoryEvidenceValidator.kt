package com.ai.assistance.operit.core.chat

import java.util.Locale

object CompanionMemoryEvidenceValidator {
    private val punctuationAndWhitespace = Regex("[\\s，。！？、,.!?;；:：'\"’‘“”()（）\\[\\]【】]+")
    private val leadingSubjects =
        listOf(
            "该用户",
            "用户",
            "本人",
            "我的",
            "我",
            "theuser",
            "user",
            "my",
            "i",
        )
    private val negationSuffixes =
        listOf(
            "没有来自",
            "没有住在",
            "并非来自",
            "并非住在",
            "不是来自",
            "不是住在",
            "不生活在",
            "不居住在",
            "不来自",
            "不住在",
            "从来不",
            "不怎么",
            "不是很",
            "不是",
            "不能",
            "不会",
            "不再",
            "不太",
            "并不",
            "并非",
            "不要",
            "没有",
            "从不",
            "从未",
            "dont",
            "donot",
            "never",
            "not",
            "不",
            "没",
            "无",
            "未",
            "非",
            "别",
        )
    private val relationPrefixes =
        listOf(
            "家乡是",
            "老家在",
            "生活在",
            "居住在",
            "出生于",
            "来自",
            "家在",
            "住在",
            "是",
            "amfrom",
            "isfrom",
            "livein",
            "from",
        )
    private val relationSuffixes = listOf("的人", "人", "的")

    /** Prevents a model score from confirming a value whose wording drops evidence negation. */
    fun supportsValue(evidenceQuote: String, value: String): Boolean {
        val evidence = stripLeadingSubject(normalize(evidenceQuote))
        val candidate = stripLeadingSubject(normalize(value))
        if (evidence.isBlank() || candidate.isBlank()) return false
        if (containsWithoutDroppedNegation(evidence, candidate)) return true

        val evidenceCores = relationCores(evidence)
        val candidateCores = relationCores(candidate)
        return evidenceCores.any { evidenceCore ->
            candidateCores.any { candidateCore ->
                candidateCore.length >= MIN_SEMANTIC_CORE_LENGTH &&
                    containsWithoutDroppedNegation(evidenceCore, candidateCore)
            }
        }
    }

    private fun normalize(value: String): String =
        value
            .trim()
            .lowercase(Locale.ROOT)
            .replace(punctuationAndWhitespace, "")

    private fun stripLeadingSubject(value: String): String =
        leadingSubjects.firstOrNull(value::startsWith)?.let(value::removePrefix) ?: value

    private fun relationCores(value: String): Set<String> {
        val cores = linkedSetOf(value)
        val prefix = relationPrefixes.firstOrNull(value::startsWith) ?: return cores
        val withoutPrefix = value.removePrefix(prefix)
        if (withoutPrefix.isBlank()) return cores
        cores += withoutPrefix
        relationSuffixes.firstOrNull(withoutPrefix::endsWith)?.let { suffix ->
            withoutPrefix.removeSuffix(suffix).takeIf { it.isNotBlank() }?.let(cores::add)
        }
        return cores
    }

    private fun containsWithoutDroppedNegation(evidence: String, candidate: String): Boolean {
        var index = evidence.indexOf(candidate)
        while (index >= 0) {
            val prefix = evidence.substring(0, index)
            if (negationSuffixes.none(prefix::endsWith)) {
                return true
            }
            index = evidence.indexOf(candidate, startIndex = index + 1)
        }
        return false
    }

    private const val MIN_SEMANTIC_CORE_LENGTH = 1
}
