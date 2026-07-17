package com.ai.assistance.operit.core.companion

import com.ai.assistance.operit.data.model.CompanionEventKind
import com.ai.assistance.operit.data.preferences.CompanionReminderIntensity
import com.ai.assistance.operit.data.preferences.CompanionReminderSettings
import java.time.ZonedDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionReminderPolicyTest {
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun `explicit mode sends only explicit reminders`() {
        val settings =
            CompanionReminderSettings(
                intensity = CompanionReminderIntensity.EXPLICIT_ONLY,
                quietHoursEnabled = false,
            )
        val now = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, zoneId).toInstant().toEpochMilli()

        assertEquals(
            CompanionReminderDecision.Send,
            CompanionReminderPolicy.decide(CompanionEventKind.REMINDER, settings, 0, now, zoneId),
        )
        assertEquals(
            CompanionReminderDecision.Skip,
            CompanionReminderPolicy.decide(CompanionEventKind.PROMISE, settings, 0, now, zoneId),
        )
    }

    @Test
    fun `quiet hours defer an overnight reminder until morning`() {
        val settings =
            CompanionReminderSettings(
                intensity = CompanionReminderIntensity.OCCASIONAL,
                quietStartMinutes = 23 * 60,
                quietEndMinutes = 8 * 60,
            )
        val now = ZonedDateTime.of(2026, 7, 15, 23, 30, 0, 0, zoneId).toInstant().toEpochMilli()
        val decision =
            CompanionReminderPolicy.decide(
                CompanionEventKind.PROMISE,
                settings,
                sentToday = 0,
                nowMs = now,
                zoneId = zoneId,
            )

        assertTrue(decision is CompanionReminderDecision.Defer)
        val expected = ZonedDateTime.of(2026, 7, 16, 8, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        assertEquals(expected, (decision as CompanionReminderDecision.Defer).untilMs)
    }

    @Test
    fun `explicit reminders ignore proactive quiet hours and daily budget`() {
        val settings =
            CompanionReminderSettings(
                intensity = CompanionReminderIntensity.EXPLICIT_ONLY,
                quietHoursEnabled = true,
                quietStartMinutes = 23 * 60,
                quietEndMinutes = 8 * 60,
                dailyLimit = 1,
            )
        val now = ZonedDateTime.of(2026, 7, 15, 23, 30, 0, 0, zoneId).toInstant().toEpochMilli()

        assertEquals(
            CompanionReminderDecision.Send,
            CompanionReminderPolicy.decide(
                kind = CompanionEventKind.REMINDER,
                settings = settings,
                sentToday = 8,
                nowMs = now,
                zoneId = zoneId,
                cooldownUntilMs = now + 24 * 60 * 60 * 1_000L,
            ),
        )
    }

    @Test
    fun `daily budget defers additional reminders to next release window`() {
        val settings =
            CompanionReminderSettings(
                intensity = CompanionReminderIntensity.OCCASIONAL,
                quietHoursEnabled = true,
                quietEndMinutes = 8 * 60,
                dailyLimit = 2,
            )
        val now = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val decision =
            CompanionReminderPolicy.decide(
                CompanionEventKind.PROMISE,
                settings,
                sentToday = 2,
                nowMs = now,
                zoneId = zoneId,
            )

        val expected = ZonedDateTime.of(2026, 7, 16, 8, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        assertEquals(expected, (decision as CompanionReminderDecision.Defer).untilMs)
    }

    @Test
    fun `unanswered companion message defers non-explicit outreach`() {
        val now = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val cooldownUntil = now + 6 * 60 * 60 * 1_000L
        val decision =
            CompanionReminderPolicy.decide(
                kind = CompanionEventKind.PROMISE,
                settings =
                    CompanionReminderSettings(
                        intensity = CompanionReminderIntensity.OCCASIONAL,
                        quietHoursEnabled = false,
                    ),
                sentToday = 0,
                nowMs = now,
                zoneId = zoneId,
                cooldownUntilMs = cooldownUntil,
            )

        assertEquals(cooldownUntil, (decision as CompanionReminderDecision.Defer).untilMs)
    }
}
