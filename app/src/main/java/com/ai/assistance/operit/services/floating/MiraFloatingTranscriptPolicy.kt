package com.ai.assistance.operit.services.floating

data class FloatingTranscriptSource(
    val sender: String,
    val text: String,
)

data class FloatingTranscriptLine(
    val sender: String,
    val text: String,
)

object MiraFloatingTranscriptPolicy {
    private const val MAX_PREVIEW_CHARS = 140

    fun latestConversation(
        messages: List<FloatingTranscriptSource>,
        limit: Int = 3,
    ): List<FloatingTranscriptLine> {
        if (limit <= 0) return emptyList()
        return messages
            .asSequence()
            .filter { it.sender == "user" || it.sender == "ai" }
            .map { source ->
                FloatingTranscriptLine(
                    sender = source.sender,
                    text = source.text.normalizePreviewText(),
                )
            }
            .filter { it.text.isNotBlank() }
            .toList()
            .takeLast(limit)
    }

    private fun String.normalizePreviewText(): String =
        replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_PREVIEW_CHARS)
}
