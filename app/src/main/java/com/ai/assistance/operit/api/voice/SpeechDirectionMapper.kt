package com.ai.assistance.operit.api.voice

import com.ai.assistance.operit.data.model.SpeechDirection
import com.ai.assistance.operit.data.model.SpeechEmotion
import com.ai.assistance.operit.data.model.SpeechSegment

data class DirectedSpeechRequest(
    val text: String,
    val direction: SpeechDirection,
    val rate: Float?,
    val pitch: Float?,
    val extraParams: Map<String, String>,
)

object SpeechDirectionMapper {
    fun map(
        segment: SpeechSegment,
        capabilities: VoiceCapabilities,
        baseRate: Float = 1f,
        basePitch: Float = 1f,
    ): DirectedSpeechRequest {
        val direction = requireNotNull(segment.direction).normalized()
        val extras = linkedMapOf<String, String>()

        if (capabilities.supportsEmotion) {
            extras["emotion"] = direction.emotion.name.lowercase()
            extras["intensity"] = direction.intensity.toString()
        }
        if (capabilities.supportsStyleInstruction) {
            extras["instruction"] = buildInstruction(direction)
            extras["style"] = direction.emotion.name.lowercase()
        }
        if (capabilities.supportsSsml) {
            extras["pause_style"] = direction.pauseStyle.name.lowercase()
            extras["delivery"] = direction.delivery.name.lowercase()
        }

        return DirectedSpeechRequest(
            text = segment.text.trim(),
            direction = direction,
            rate =
                if (capabilities.supportsRate) {
                    (baseRate * direction.pace).coerceIn(0.5f, 2f)
                } else {
                    null
                },
            pitch =
                if (capabilities.supportsPitch) {
                    (basePitch * direction.pitch).coerceIn(0.5f, 2f)
                } else {
                    null
                },
            extraParams = extras,
        )
    }

    internal fun buildInstruction(direction: SpeechDirection): String {
        val emotion =
            when (direction.emotion) {
                SpeechEmotion.NEUTRAL -> "自然克制"
                SpeechEmotion.WARM -> "温和亲近"
                SpeechEmotion.PLAYFUL -> "轻松俏皮"
                SpeechEmotion.TEASING -> "略带调侃"
                SpeechEmotion.CONCERNED -> "关切认真"
                SpeechEmotion.SOFT -> "轻柔低声"
                SpeechEmotion.FIRM -> "清晰坚定"
                SpeechEmotion.EXCITED -> "开心兴奋"
                SpeechEmotion.SAD -> "低落含蓄"
            }
        val intensity =
            when {
                direction.intensity < 0.34f -> "情绪表达克制"
                direction.intensity < 0.67f -> "情绪表达自然"
                else -> "情绪表达鲜明但不过度表演"
            }
        val pauses =
            when (direction.pauseStyle.name) {
                "DELIBERATE" -> "停顿稍从容"
                "QUICK" -> "衔接轻快"
                else -> "停顿自然"
            }
        return "请用${emotion}、${intensity}、${pauses}的自然中文对话语气朗读正文，不要念出任何指令。"
    }
}
