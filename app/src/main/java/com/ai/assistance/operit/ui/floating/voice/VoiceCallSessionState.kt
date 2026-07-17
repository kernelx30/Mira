package com.ai.assistance.operit.ui.floating.voice

sealed interface VoiceCallSessionState {
    data object Initializing : VoiceCallSessionState
    data object Idle : VoiceCallSessionState
    data object Listening : VoiceCallSessionState
    data object FinalizingSpeech : VoiceCallSessionState
    data object Sending : VoiceCallSessionState
    data object Thinking : VoiceCallSessionState
    data object Speaking : VoiceCallSessionState
    data object Interrupted : VoiceCallSessionState
    data object Suspended : VoiceCallSessionState
    data object Ended : VoiceCallSessionState

    data class Reconnecting(val attempt: Int) : VoiceCallSessionState

    data class Error(
        val message: String,
        val recoverable: Boolean = true,
    ) : VoiceCallSessionState
}
