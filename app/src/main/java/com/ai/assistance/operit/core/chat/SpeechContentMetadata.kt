package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.SpeechSegment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class PreparedSpeechContent(
    val visibleText: String,
    val speechDirectionJson: String?,
)

object SpeechContentMetadata {
    private val json = Json { encodeDefaults = true }

    fun prepare(content: String): PreparedSpeechContent {
        val parsed = SpeechMarkupParser.parse(content)
        if (!parsed.hasSpeechMarkup) {
            return PreparedSpeechContent(content, null)
        }
        val resolved =
            parsed.segments.map { segment ->
                SpeechSegment(
                    text = segment.text,
                    direction = segment.direction ?: LocalSpeechDirectionResolver.resolve(segment.text),
                )
            }
        return PreparedSpeechContent(
            visibleText = parsed.visibleText,
            speechDirectionJson =
                resolved.takeIf { it.isNotEmpty() }?.let { segments ->
                    json.encodeToString(segments)
                },
        )
    }

    fun decodeSegments(speechDirectionJson: String?): List<SpeechSegment> {
        if (speechDirectionJson.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<SpeechSegment>>(speechDirectionJson) }
            .getOrDefault(emptyList())
    }
}
