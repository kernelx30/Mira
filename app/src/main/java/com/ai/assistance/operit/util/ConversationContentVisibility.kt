package com.ai.assistance.operit.util

object ConversationContentVisibility {
    private val speechMarkupTag =
        Regex("</?speech\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val trailingInternalBlockStart =
        Regex(
            pattern =
                "<(?:status|think(?:ing)?|search|meta|emotion|memory|" +
                    "workspace_attachment|attachment|" +
                    ChatMarkupRegex.TOOL_RESULT_TAG_NAME_REGEX_SOURCE +
                    "|" +
                    ChatMarkupRegex.TOOL_TAG_NAME_REGEX_SOURCE +
                    ")\\b",
            option = RegexOption.IGNORE_CASE,
        )
    private val invisibleCharacters = Regex("[\\s\\p{Z}\\p{Cf}\\p{Cc}]+")
    private val markdownStructureOnly =
        Regex(
            pattern =
                "^(?:" +
                    "#{1,6}|" +
                    ">+|" +
                    "[-+*]|" +
                    "\\d{1,9}[.)]|" +
                    "[-+*]\\s*\\[[ xX]]|" +
                    "`{3,}|~{3,}|" +
                    "(?:[-*_]\\s*){3,}" +
                    ")$",
        )

    fun hasRenderableAssistantContent(content: String): Boolean =
        hasRenderableText(extractAssistantConversationContent(content))

    fun hasRenderableText(content: String): Boolean {
        val visibleLines =
            content
                .lineSequence()
                .map { line -> invisibleCharacters.replace(line, "").trim() }
                .filter { it.isNotEmpty() }
                .toList()
        if (visibleLines.isEmpty()) return false
        return visibleLines.any { line -> !markdownStructureOnly.matches(line) }
    }

    fun extractAssistantConversationContent(content: String): String {
        val withoutClosedInternalBlocks =
            content
                .replace(speechMarkupTag, "")
                .replace(ChatMarkupRegex.statusTag, "")
                .replace(ChatMarkupRegex.statusSelfClosingTag, "")
                .replace(ChatMarkupRegex.thinkTag, "")
                .replace(ChatMarkupRegex.thinkSelfClosingTag, "")
                .replace(ChatMarkupRegex.searchTag, "")
                .replace(ChatMarkupRegex.searchSelfClosingTag, "")
                .replace(ChatMarkupRegex.toolTag, "")
                .replace(ChatMarkupRegex.toolSelfClosingTag, "")
                .replace(ChatMarkupRegex.toolResultTag, "")
                .replace(ChatMarkupRegex.toolResultSelfClosingTag, "")
                .replace(ChatMarkupRegex.metaTag, "")
                .replace(ChatMarkupRegex.memoryTag, "")
                .replace(ChatMarkupRegex.emotionTag, "")
                .replace(ChatMarkupRegex.proxySenderTag, "")
                .replace(ChatMarkupRegex.workspaceAttachmentTag, "")
                .replace(ChatMarkupRegex.attachmentTag, "")
                .replace(ChatMarkupRegex.attachmentSelfClosingTag, "")
        val trailingInternalBlock = trailingInternalBlockStart.find(withoutClosedInternalBlocks)
        return if (trailingInternalBlock == null) {
            withoutClosedInternalBlocks.trim()
        } else {
            withoutClosedInternalBlocks.substring(0, trailingInternalBlock.range.first).trim()
        }
    }
}
