package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.MessageEntity

object CompanionMemorySaveEvidencePolicy {
    private val bareReferentialSavePatterns =
        listOf(
            Regex("^(?:请|你)?(?:记住|记下来|记下|记录下来|保存|存一下|别忘了|不要忘记)[。！？!?]*$"),
            Regex("^(?:please\\s+)?(?:remember|save|note)(?:\\s+(?:it|this|that))?[.!?]*$", RegexOption.IGNORE_CASE),
        )
    private val referentialSavePatterns =
        bareReferentialSavePatterns +
            listOf(
                Regex("^(?:请|你)?(?:记住|记下来|记下|记录下来|保存)(?:这个|这条|那个|那条|刚才(?:那|这)?条|上一条|上面(?:那|这)?条|我刚才说的)[。！？!?]*$"),
                Regex("^(?:请|你)?(?:把|将)(?:这个|这条|那个|那条|刚才(?:那|这)?条|上一条|上面(?:那|这)?条|我刚才说的)(?:记住|记下来|保存)[。！？!?]*$"),
                Regex("^(?:please\\s+)?(?:remember|save|note)\\s+(?:this|that|the\\s+previous\\s+message|what\\s+i\\s+just\\s+said)(?:\\s+(?:in\\s+memory|down))?[.!?]*$", RegexOption.IGNORE_CASE),
            )

    data class EvidenceSelection(
        val message: MessageEntity,
        val quote: String,
    )

    fun isReferentialSaveRequest(content: String): Boolean {
        val normalized = content.trim()
        return normalized.isNotBlank() && referentialSavePatterns.any { it.matches(normalized) }
    }

    private fun isBareReferentialSaveRequest(content: String): Boolean {
        val normalized = content.trim()
        return normalized.isNotBlank() && bareReferentialSavePatterns.any { it.matches(normalized) }
    }

    /** Resolve a quote from the save request or the user message it explicitly refers to. */
    fun resolveEvidence(
        recentUserMessagesDesc: List<MessageEntity>,
        evidenceQuote: String,
    ): EvidenceSelection? {
        val requestedQuote = evidenceQuote.trim()
        if (requestedQuote.isBlank()) return null
        val latest = recentUserMessagesDesc.firstOrNull() ?: return null
        if (!isReferentialSaveRequest(latest.content)) {
            return latest
                .takeIf { it.content.contains(requestedQuote) }
                ?.let { EvidenceSelection(it, requestedQuote) }
        }

        val previous = recentUserMessagesDesc.getOrNull(1) ?: return null
        if (previous.content.contains(requestedQuote)) {
            return EvidenceSelection(previous, requestedQuote)
        }
        if (isBareReferentialSaveRequest(latest.content)) {
            return EvidenceSelection(previous, previous.content.trim())
        }
        if (latest.content.contains(requestedQuote)) {
            return EvidenceSelection(previous, previous.content.trim())
        }
        return null
    }

    fun selectEvidenceMessage(
        recentUserMessagesDesc: List<MessageEntity>,
        evidenceQuote: String,
    ): MessageEntity? = resolveEvidence(recentUserMessagesDesc, evidenceQuote)?.message
}
