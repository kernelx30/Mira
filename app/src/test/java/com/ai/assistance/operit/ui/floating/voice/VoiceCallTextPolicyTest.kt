package com.ai.assistance.operit.ui.floating.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCallTextPolicyTest {
    @Test
    fun terminalPunctuationClosesTurnFaster() {
        assertTrue(VoiceCallTextPolicy.silenceDelayMs("说完了。", true, 1_800L) < 1_800L)
    }

    @Test
    fun shortFragmentsGetMoreTime() {
        assertTrue(VoiceCallTextPolicy.silenceDelayMs("嗯", false, 1_800L) > 1_800L)
    }

    @Test
    fun playbackSubstringIsFilteredAsEcho() {
        assertTrue(
            VoiceCallTextPolicy.isLikelyPlaybackEcho(
                recognizedText = "今天下雨了",
                spokenText = "今天下雨了，出门记得带伞。",
            )
        )
    }

    @Test
    fun differentUserSpeechIsNotFiltered() {
        assertFalse(
            VoiceCallTextPolicy.isLikelyPlaybackEcho(
                recognizedText = "先停一下，我有话说",
                spokenText = "今天下雨了，出门记得带伞。",
            )
        )
    }
}
