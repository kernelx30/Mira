package com.ai.assistance.operit.services.floating

import com.ai.assistance.operit.ui.floating.FloatingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingAutoReadPolicyTest {
    @Test
    fun ordinaryFloatingModesUseCoreAutoRead() {
        assertTrue(shouldUseFloatingCoreAutoRead(FloatingMode.WINDOW))
        assertTrue(shouldUseFloatingCoreAutoRead(FloatingMode.BALL))
        assertTrue(shouldUseFloatingCoreAutoRead(FloatingMode.VOICE_BALL))
        assertTrue(shouldUseFloatingCoreAutoRead(FloatingMode.RESULT_DISPLAY))
    }

    @Test
    fun fullscreenModesKeepTheirOwnStreamingTts() {
        assertFalse(shouldUseFloatingCoreAutoRead(FloatingMode.FULLSCREEN))
        assertFalse(shouldUseFloatingCoreAutoRead(FloatingMode.SCREEN_OCR))
    }

    @Test
    fun ordinaryFloatingModesRespectConversationAndGlobalAutoReadSettings() {
        assertNull(resolveFloatingCoreAutoReadOverride(FloatingMode.WINDOW, null))
        assertEquals(false, resolveFloatingCoreAutoReadOverride(FloatingMode.WINDOW, false))
        assertEquals(true, resolveFloatingCoreAutoReadOverride(FloatingMode.BALL, true))
    }

    @Test
    fun fullscreenModesDisableCoreAutoReadEvenWhenConversationEnablesIt() {
        assertEquals(false, resolveFloatingCoreAutoReadOverride(FloatingMode.FULLSCREEN, true))
        assertEquals(false, resolveFloatingCoreAutoReadOverride(FloatingMode.SCREEN_OCR, null))
    }
}
