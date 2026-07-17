package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.SpeechDelivery
import com.ai.assistance.operit.data.model.SpeechDirection
import com.ai.assistance.operit.data.model.SpeechEmotion
import com.ai.assistance.operit.data.model.SpeechPauseStyle
import com.ai.assistance.operit.data.model.SpeechSegment
import com.ai.assistance.operit.util.ConversationContentVisibility

data class ParsedSpeechMarkup(
    val visibleText: String,
    val segments: List<SpeechSegment>,
    val hasSpeechMarkup: Boolean,
)

object SpeechMarkupParser {
    private val speechTag =
        Regex(
            "<speech\\b([^>]*)>([\\s\\S]*?)</speech>",
            setOf(RegexOption.IGNORE_CASE),
        )
    private val attribute =
        Regex("([a-zA-Z][a-zA-Z0-9_-]*)\\s*=\\s*([\"'])(.*?)\\2")
    private val openingSpeechTag = Regex("<speech\\b([^>]*)>", RegexOption.IGNORE_CASE)

    fun parse(content: String): ParsedSpeechMarkup {
        if (content.isBlank()) return ParsedSpeechMarkup("", emptyList(), false)

        val matches = speechTag.findAll(content).toList()
        if (matches.isEmpty()) {
            val visible = stripSpeechTags(content).trim()
            val unclosedOpening = openingSpeechTag.find(content)
            val speakable = extractSpeakableText(visible)
            return ParsedSpeechMarkup(
                visibleText = visible,
                segments =
                    speakable.takeIf { it.isNotBlank() }?.let {
                        listOf(
                            SpeechSegment(
                                text = it,
                                direction = unclosedOpening?.let { match -> parseDirection(match.groupValues[1]) },
                            )
                        )
                    }.orEmpty(),
                hasSpeechMarkup = unclosedOpening != null,
            )
        }

        val segments = mutableListOf<SpeechSegment>()
        val visible = StringBuilder()
        var cursor = 0
        matches.forEach { match ->
            val prefix = stripSpeechTags(content.substring(cursor, match.range.first))
            appendVisible(visible, prefix)
            appendSpeakableSegment(segments, prefix)

            val text = stripSpeechTags(match.groupValues[2]).trim()
            if (text.isNotBlank()) {
                appendVisible(visible, text)
                val speakableText = extractSpeakableText(text)
                if (speakableText.isNotBlank()) {
                    segments +=
                        SpeechSegment(
                            text = speakableText,
                            direction = parseDirection(match.groupValues[1]),
                        )
                }
            }
            cursor = match.range.last + 1
        }
        val suffix = stripSpeechTags(content.substring(cursor))
        appendVisible(visible, suffix)
        appendSpeakableSegment(segments, suffix)

        return ParsedSpeechMarkup(
            visibleText = visible.toString().trim(),
            segments = segments,
            hasSpeechMarkup = true,
        )
    }

    fun stripSpeechTags(content: String): String =
        content
            .replace(Regex("</?speech\\b[^>]*>", RegexOption.IGNORE_CASE), "")
            .trim()

    private fun parseDirection(rawAttributes: String): SpeechDirection {
        val values =
            attribute.findAll(rawAttributes).associate { match ->
                match.groupValues[1].lowercase() to match.groupValues[3].trim()
            }
        return SpeechDirection(
            emotion = values["emotion"].toEnumOrNull<SpeechEmotion>() ?: SpeechEmotion.NEUTRAL,
            intensity = values["intensity"]?.toFloatOrNull() ?: 0.35f,
            pace = values["pace"]?.toFloatOrNull() ?: 1f,
            pitch = values["pitch"]?.toFloatOrNull() ?: 1f,
            pauseStyle =
                values["pausestyle"].toEnumOrNull<SpeechPauseStyle>()
                    ?: values["pause_style"].toEnumOrNull<SpeechPauseStyle>()
                    ?: SpeechPauseStyle.NATURAL,
            delivery = values["delivery"].toEnumOrNull<SpeechDelivery>() ?: SpeechDelivery.CONVERSATION,
        ).normalized()
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
        this?.let { value -> enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } }

    private fun appendVisible(builder: StringBuilder, value: String) {
        val text = value.trim()
        if (text.isBlank()) return
        if (builder.isNotEmpty() && !builder.last().isWhitespace()) builder.append('\n')
        builder.append(text)
    }

    private fun appendSpeakableSegment(segments: MutableList<SpeechSegment>, value: String) {
        extractSpeakableText(value)
            .takeIf { it.isNotBlank() }
            ?.let { segments += SpeechSegment(it) }
    }

    private fun extractSpeakableText(value: String): String =
        ConversationContentVisibility.extractAssistantConversationContent(value)
}
