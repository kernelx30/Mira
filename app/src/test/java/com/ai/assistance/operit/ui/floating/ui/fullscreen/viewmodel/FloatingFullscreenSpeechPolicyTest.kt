package com.ai.assistance.operit.ui.floating.ui.fullscreen.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingFullscreenSpeechPolicyTest {
    @Test
    fun textModeRequiresEffectiveAutoReadAndNoLocalMute() {
        assertTrue(
            shouldSpeakFullscreenResponse(
                voiceCallActive = false,
                locallyMuted = false,
                effectiveAutoReadEnabled = true,
            )
        )
        assertFalse(
            shouldSpeakFullscreenResponse(
                voiceCallActive = false,
                locallyMuted = false,
                effectiveAutoReadEnabled = false,
            )
        )
        assertFalse(
            shouldSpeakFullscreenResponse(
                voiceCallActive = false,
                locallyMuted = true,
                effectiveAutoReadEnabled = true,
            )
        )
    }

    @Test
    fun voiceCallAlwaysSpeaksItsResponse() {
        assertTrue(
            shouldSpeakFullscreenResponse(
                voiceCallActive = true,
                locallyMuted = true,
                effectiveAutoReadEnabled = false,
            )
        )
    }
}
