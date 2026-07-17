package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.util.StructuredAssistantContentParser

internal object ImmersiveMessagePresentation {
    private val trailingClosers = setOf('"', '\'', '”', '’', '」', '』')

    fun fragments(content: String): List<String> {
        if (content.isBlank()) return emptyList()

        val fragments =
            StructuredAssistantContentParser.parse(content).flatMap { block ->
                when (block.kind) {
                    StructuredAssistantContentParser.BlockKind.TEXT ->
                        splitText(block.rawContent)

                    StructuredAssistantContentParser.BlockKind.XML ->
                        if (block.tagName.equals("speech", ignoreCase = true)) {
                            splitText(block.content)
                        } else {
                            listOf(block.rawContent.trim())
                        }
                }
            }.filter { it.isNotBlank() }

        return fragments.ifEmpty { listOf(content.trim()) }
    }

    internal fun splitText(content: String): List<String> {
        if (content.isBlank()) return emptyList()

        val result = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        var inlineCode = false
        var fencedCode = false

        fun flush() {
            current.toString().trim().takeIf { it.isNotEmpty() }?.let(result::add)
            current.clear()
        }

        while (index < content.length) {
            if (content.startsWith("```", index)) {
                fencedCode = !fencedCode
                current.append("```")
                index += 3
                continue
            }

            val char = content[index]
            if (!fencedCode && char == '`') {
                inlineCode = !inlineCode
            }
            current.append(char)

            if (!fencedCode && !inlineCode) {
                val next = content.getOrNull(index + 1)
                val isHardSentenceEnd = char in setOf('。', '！', '？', '!', '?', '~', '～')
                val isEnglishPeriod = char == '.' && (next == null || next.isWhitespace())
                val isCompletedEllipsis =
                    char == '…' && content.getOrNull(index - 1) == '…' &&
                        (next == null || next.isWhitespace())
                val isParagraphEnd =
                    char == '\n' && content.getOrNull(index + 1) == '\n'

                if (isHardSentenceEnd || isEnglishPeriod || isCompletedEllipsis || isParagraphEnd) {
                    while (index + 1 < content.length && content[index + 1] in trailingClosers) {
                        index += 1
                        current.append(content[index])
                    }
                    flush()
                }
            }
            index += 1
        }

        flush()
        return result
    }
}
