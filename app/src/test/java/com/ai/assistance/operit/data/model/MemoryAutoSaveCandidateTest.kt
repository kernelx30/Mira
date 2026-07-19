package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryAutoSaveCandidateTest {
    @Test
    fun retryDelayUsesExponentialBackoffAndCapsAtSixHours() {
        assertEquals(60_000L, MemoryAutoSaveCandidate.retryDelayMs(1))
        assertEquals(120_000L, MemoryAutoSaveCandidate.retryDelayMs(2))
        assertEquals(240_000L, MemoryAutoSaveCandidate.retryDelayMs(3))
        assertTrue(
            MemoryAutoSaveCandidate.retryDelayMs(100) <=
                MemoryAutoSaveCandidate.MAX_RETRY_DELAY_MS,
        )
    }

    @Test
    fun recognizesHighValueAutomaticCandidateSource() {
        assertTrue(
            MemoryAutoSaveCandidate.isHighValueAutoSource(
                MemoryAutoSaveCandidate.SOURCE_TYPE_HIGH_VALUE_AUTO,
            ),
        )
    }

    @Test
    fun explicitUserRequestUsesStrongConfirmationPath() {
        assertTrue(
            MemoryAutoSaveCandidate.isExplicitUserRequestSource(
                MemoryAutoSaveCandidate.SOURCE_TYPE_EXPLICIT_USER,
            ),
        )
    }

    @Test
    fun selectedMessageIsNotAnExplicitMemoryRequest() {
        assertTrue(
            MemoryAutoSaveCandidate.isUserSelectedSource(
                MemoryAutoSaveCandidate.SOURCE_TYPE_SELECTED_USER_MESSAGE,
            ),
        )
        assertTrue(
            !MemoryAutoSaveCandidate.isExplicitUserRequestSource(
                MemoryAutoSaveCandidate.SOURCE_TYPE_SELECTED_USER_MESSAGE,
            ),
        )
    }

    @Test
    fun candidateKeepsCompanionTargetSnapshot() {
        val candidate = MemoryAutoSaveCandidate(companionId = "character:zero")

        assertEquals("character:zero", candidate.companionId)
    }
}
