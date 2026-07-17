package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChatMessageDisplayMode {
    NORMAL,
    IMMERSIVE_TURN,
    PENDING_DISPATCH,
    HIDDEN_PLACEHOLDER
}
