package com.ai.assistance.operit.ui.features.chat.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechInterruptionPolicyTest {
    @Test
    fun queuedPlaybackIsInterruptedBeforeUiStateTurnsActive() {
        assertTrue(
            shouldInterruptSpeechForUserTurn(
                playbackJobActive = true,
                speechSessionActive = false,
                playing = false,
                paused = false,
                hasCurrentSegment = false,
            )
        )
    }

    @Test
    fun fullyIdleSpeechDoesNotTriggerStop() {
        assertFalse(
            shouldInterruptSpeechForUserTurn(
                playbackJobActive = false,
                speechSessionActive = false,
                playing = false,
                paused = false,
                hasCurrentSegment = false,
            )
        )
    }
}
