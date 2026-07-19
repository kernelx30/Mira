package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.MemoryAutoSaveCandidate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryAutoSaveCandidateRepositoryPolicyTest {
    @Test
    fun legacyCandidateWithoutTargetIsQuarantined() {
        assertTrue(shouldQuarantineLegacyMemoryCandidate(MemoryAutoSaveCandidate(companionId = "")))
    }

    @Test
    fun scopedAndAlreadyQuarantinedCandidatesStayUntouched() {
        assertFalse(
            shouldQuarantineLegacyMemoryCandidate(
                MemoryAutoSaveCandidate(companionId = "character:zero"),
            ),
        )
        assertFalse(
            shouldQuarantineLegacyMemoryCandidate(
                MemoryAutoSaveCandidate(
                    companionId = "",
                    status = MemoryAutoSaveCandidate.STATUS_SKIPPED_LEGACY_UNSCOPED,
                ),
            ),
        )
    }
}
