package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.CompanionMemorySourceKind
import com.ai.assistance.operit.data.model.CompanionMemoryType
import com.ai.assistance.operit.data.model.CompanionRecordScope
import com.ai.assistance.operit.data.model.CompanionRecordStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryRecallEligibilityTest {
    @Test
    fun ordinaryRecallOnlyAcceptsConfirmedActiveRecords() {
        assertTrue(record().isEligibleForRecall(includeReview = false))
        assertFalse(record(reviewAt = 1_000L).isEligibleForRecall(includeReview = false))
        assertFalse(record(needsReview = true).isEligibleForRecall(includeReview = false))
        assertFalse(
            record(status = CompanionRecordStatus.ARCHIVED.name)
                .isEligibleForRecall(includeReview = false),
        )
    }

    @Test
    fun managementRecallCanInspectPendingActiveRecords() {
        assertTrue(record(reviewAt = 1_000L).isEligibleForRecall(includeReview = true))
        assertTrue(record(needsReview = true).isEligibleForRecall(includeReview = true))
        assertFalse(
            record(status = CompanionRecordStatus.DELETED.name)
                .isEligibleForRecall(includeReview = true),
        )
    }

    private fun record(
        status: String = CompanionRecordStatus.ACTIVE.name,
        reviewAt: Long? = null,
        needsReview: Boolean = false,
    ) = CompanionMemoryRecordEntity(
        profileId = "profile",
        companionId = "character:mira",
        scope = CompanionRecordScope.RELATIONSHIP.name,
        type = CompanionMemoryType.FACT.name,
        subjectKey = "user",
        predicate = "identity",
        valueJson = "{\"value\":\"tester\"}",
        normalizedValue = "tester",
        status = status,
        sourceKind = CompanionMemorySourceKind.USER_EXPLICIT.name,
        reviewAt = reviewAt,
        needsReview = needsReview,
    )
}
