package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionRecordStatusTest {
    @Test
    fun migratesLegacyTerminalStatusesWithoutRestoringThem() {
        assertEquals(
            CompanionRecordStatus.DELETED,
            CompanionRecordStatus.fromStorageValue("RETRACTED"),
        )
        assertEquals(
            CompanionRecordStatus.ARCHIVED,
            CompanionRecordStatus.fromStorageValue("EXPIRED"),
        )
    }
}
