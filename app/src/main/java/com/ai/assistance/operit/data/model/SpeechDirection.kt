package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class SpeechEmotion {
    NEUTRAL,
    WARM,
    PLAYFUL,
    TEASING,
    CONCERNED,
    SOFT,
    FIRM,
    EXCITED,
    SAD,
}

@Serializable
enum class SpeechPauseStyle {
    NATURAL,
    DELIBERATE,
    QUICK,
}

@Serializable
enum class SpeechDelivery {
    CONVERSATION,
    VOICE_NOTE,
    CALL,
}

@Serializable
enum class SpeechExpressionStrength(val scale: Float) {
    RESTRAINED(0.65f),
    NATURAL(1f),
    VIVID(1.25f),
}

@Serializable
data class SpeechDirection(
    val emotion: SpeechEmotion = SpeechEmotion.NEUTRAL,
    val intensity: Float = 0.35f,
    val pace: Float = 1f,
    val pitch: Float = 1f,
    val pauseStyle: SpeechPauseStyle = SpeechPauseStyle.NATURAL,
    val delivery: SpeechDelivery = SpeechDelivery.CONVERSATION,
) {
    fun normalized(): SpeechDirection =
        copy(
            intensity = intensity.coerceIn(0f, 1f),
            pace = pace.coerceIn(0.85f, 1.15f),
            pitch = pitch.coerceIn(0.90f, 1.10f),
        )
}
