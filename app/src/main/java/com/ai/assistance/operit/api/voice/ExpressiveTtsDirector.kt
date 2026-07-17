package com.ai.assistance.operit.api.voice

import com.ai.assistance.operit.core.chat.LocalSpeechDirectionResolver
import com.ai.assistance.operit.core.chat.SpeechMarkupParser
import com.ai.assistance.operit.data.model.SpeechDelivery
import com.ai.assistance.operit.data.model.SpeechDirection
import com.ai.assistance.operit.data.model.SpeechSegment
import com.ai.assistance.operit.util.TtsSegmenter

object ExpressiveTtsDirector {
    const val DEFAULT_MAX_SEGMENTS = 4

    fun plan(
        content: String,
        capabilities: VoiceCapabilities,
        baseRate: Float = 1f,
        basePitch: Float = 1f,
        delivery: SpeechDelivery = SpeechDelivery.CONVERSATION,
        maxSegments: Int = DEFAULT_MAX_SEGMENTS,
        expressionScale: Float = 1f,
    ): List<DirectedSpeechRequest> {
        if (content.isBlank()) return emptyList()
        return planSegments(
            segments = SpeechMarkupParser.parse(content).segments,
            capabilities = capabilities,
            baseRate = baseRate,
            basePitch = basePitch,
            delivery = delivery,
            maxSegments = maxSegments,
            expressionScale = expressionScale,
        )
    }

    fun planSegments(
        segments: List<SpeechSegment>,
        capabilities: VoiceCapabilities,
        baseRate: Float = 1f,
        basePitch: Float = 1f,
        delivery: SpeechDelivery = SpeechDelivery.CONVERSATION,
        maxSegments: Int = DEFAULT_MAX_SEGMENTS,
        expressionScale: Float = 1f,
    ): List<DirectedSpeechRequest> {
        val resolved =
            segments.flatMap { segment ->
                val direction =
                    segment.direction?.copy(delivery = delivery)?.normalized()
                        ?: LocalSpeechDirectionResolver.resolve(segment.text, delivery)
                val scaledDirection = scaleDirection(direction, expressionScale)
                TtsSegmenter.splitNaturalBlocks(segment.text).map { block ->
                    SpeechSegment(text = block, direction = scaledDirection)
                }
            }
        return limitSegments(mergeAdjacent(resolved), maxSegments.coerceAtLeast(1)).map { segment ->
            SpeechDirectionMapper.map(segment, capabilities, baseRate, basePitch)
        }
    }

    private fun mergeAdjacent(segments: List<SpeechSegment>): List<SpeechSegment> {
        val merged = mutableListOf<SpeechSegment>()
        segments.forEach { next ->
            if (next.text.isBlank()) return@forEach
            val previous = merged.lastOrNull()
            if (previous != null && sameDirection(previous.direction, next.direction)) {
                merged[merged.lastIndex] = previous.copy(text = joinText(previous.text, next.text))
            } else {
                merged += next.copy(text = next.text.trim())
            }
        }
        return merged
    }

    private fun limitSegments(segments: List<SpeechSegment>, maxSegments: Int): List<SpeechSegment> {
        if (segments.size <= maxSegments) return segments
        val head = segments.take(maxSegments - 1).toMutableList()
        val tail = segments.drop(maxSegments - 1)
        val tailDirection = tail.first().direction
        head += SpeechSegment(
            text = tail.joinToString(separator = "\n") { it.text.trim() },
            direction = tailDirection,
        )
        return head
    }

    private fun sameDirection(first: SpeechDirection?, second: SpeechDirection?): Boolean =
        first?.normalized() == second?.normalized()

    private fun scaleDirection(direction: SpeechDirection, scale: Float): SpeechDirection {
        val safeScale = scale.coerceIn(0f, 1.5f)
        return direction.copy(
            intensity = direction.intensity * safeScale,
            pace = 1f + (direction.pace - 1f) * safeScale,
            pitch = 1f + (direction.pitch - 1f) * safeScale,
        ).normalized()
    }

    private fun joinText(first: String, second: String): String {
        val separator = if (first.endsWith('\n')) "" else "\n"
        return first.trimEnd() + separator + second.trimStart()
    }
}
