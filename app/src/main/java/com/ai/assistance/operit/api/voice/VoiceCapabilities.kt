package com.ai.assistance.operit.api.voice

data class VoiceCapabilities(
    val supportsEmotion: Boolean = false,
    val supportsStyleInstruction: Boolean = false,
    val supportsRate: Boolean = true,
    val supportsPitch: Boolean = true,
    val supportsSsml: Boolean = false,
) {
    companion object {
        val PLAIN =
            VoiceCapabilities(
                supportsRate = false,
                supportsPitch = false,
            )

        val PROSODY_ONLY = VoiceCapabilities()
    }
}
