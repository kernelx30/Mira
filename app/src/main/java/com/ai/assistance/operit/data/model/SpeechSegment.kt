package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SpeechSegment(
    val text: String,
    val direction: SpeechDirection? = null,
)
