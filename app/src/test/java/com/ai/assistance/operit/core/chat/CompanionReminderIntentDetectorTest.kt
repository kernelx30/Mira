package com.ai.assistance.operit.core.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionReminderIntentDetectorTest {
    @Test
    fun `explicit reminder is time sensitive`() {
        assertTrue(CompanionReminderIntentDetector.isTimeSensitive("Remind me tomorrow at 8 to submit the report"))
        assertTrue(CompanionReminderIntentDetector.isExplicitReminder("Remind me tomorrow at 8 to submit the report"))
    }

    @Test
    fun `dated commitment is time sensitive`() {
        assertTrue(CompanionReminderIntentDetector.isTimeSensitive("I have to finish the draft next Friday"))
        assertFalse(CompanionReminderIntentDetector.isExplicitReminder("I have to finish the draft next Friday"))
    }

    @Test
    fun `ordinary time question stays on batch path`() {
        assertFalse(CompanionReminderIntentDetector.isTimeSensitive("What will the weather be tomorrow?"))
    }
}
