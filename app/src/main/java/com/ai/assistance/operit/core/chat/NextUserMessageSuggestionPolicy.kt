package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.util.ConversationContentVisibility

data class NextUserSuggestionMessage(
    val sender: String,
    val content: String,
    val timestamp: Long,
)

object NextUserMessageSuggestionPolicy {
    private const val MAX_CONTEXT_MESSAGES = 8
    private const val MAX_MESSAGE_CHARS = 600
    private const val MAX_SUGGESTION_CHARS = 120
    private val rolePrefix =
        Regex(
            "^(?:用户(?:可能会说)?|user(?: might say)?|我|me)\\s*[:：-]\\s*",
            RegexOption.IGNORE_CASE,
        )
    private val assistantPrefix =
        Regex("^(?:助手|assistant|ai)\\s*[:：-]\\s*", RegexOption.IGNORE_CASE)
    private val leadingDecoration = Regex("^[#>*_`\\-•\\s]+")
    private val xmlLikeTag = Regex("<[^>]+>")

    fun buildContext(messages: List<NextUserSuggestionMessage>): List<NextUserSuggestionMessage> {
        val normalized =
            messages.mapNotNull { message ->
                if (message.sender != "user" && message.sender != "ai") return@mapNotNull null
                val visible =
                    if (message.sender == "ai") {
                        ConversationContentVisibility.extractAssistantConversationContent(message.content)
                    } else {
                        xmlLikeTag.replace(message.content, " ")
                    }
                val text = visible.replace(Regex("\\s+"), " ").trim().take(MAX_MESSAGE_CHARS)
                if (text.isBlank()) null else message.copy(content = text)
            }
        if (normalized.lastOrNull()?.sender != "ai") return emptyList()
        return normalized.takeLast(MAX_CONTEXT_MESSAGES)
    }

    fun sanitizeSuggestion(raw: String): String {
        val visible = ConversationContentVisibility.extractAssistantConversationContent(raw)
        val firstLine = visible.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
        val cleaned =
            firstLine
            .replace(leadingDecoration, "")
            .trim()
            .trim('"', '\'', '“', '”', '‘', '’')
            .replace(rolePrefix, "")
            .trim()
            .trim('"', '\'', '“', '”', '‘', '’')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_SUGGESTION_CHARS)
        return if (assistantPrefix.containsMatchIn(cleaned)) "" else cleaned
    }
}
