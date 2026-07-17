package com.ai.assistance.operit.core.companion

import com.ai.assistance.operit.data.model.CompanionEventKind
import com.ai.assistance.operit.data.preferences.CompanionReminderIntensity
import com.ai.assistance.operit.data.preferences.CompanionReminderSettings
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

sealed interface CompanionReminderDecision {
    data object Send : CompanionReminderDecision
    data object Skip : CompanionReminderDecision
    data class Defer(val untilMs: Long) : CompanionReminderDecision
}

object CompanionReminderPolicy {
    fun decide(
        kind: CompanionEventKind,
        settings: CompanionReminderSettings,
        sentToday: Int,
        nowMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        cooldownUntilMs: Long? = null,
    ): CompanionReminderDecision {
        if (!settings.enabled || !isKindEnabled(kind, settings.intensity)) {
            return CompanionReminderDecision.Skip
        }
        if (kind == CompanionEventKind.REMINDER) {
            return CompanionReminderDecision.Send
        }

        val now = Instant.ofEpochMilli(nowMs).atZone(zoneId)
        val deferredUntil = mutableListOf<Long>()
        if (settings.quietHoursEnabled && isInQuietHours(now, settings)) {
            deferredUntil += nextQuietHoursEnd(now, settings).toInstant().toEpochMilli()
        }

        if (sentToday >= settings.dailyLimit) {
            val tomorrow = now.toLocalDate().plusDays(1).atStartOfDay(zoneId)
            val release =
                if (settings.quietHoursEnabled && settings.quietEndMinutes > 0) {
                    tomorrow.plusMinutes(settings.quietEndMinutes.toLong())
                } else {
                    tomorrow
                }
            deferredUntil += release.toInstant().toEpochMilli()
        }

        cooldownUntilMs?.takeIf { it > nowMs }?.let(deferredUntil::add)
        deferredUntil.maxOrNull()?.let { return CompanionReminderDecision.Defer(it) }

        return CompanionReminderDecision.Send
    }

    fun isKindEnabled(kind: CompanionEventKind, intensity: CompanionReminderIntensity): Boolean =
        when (intensity) {
            CompanionReminderIntensity.EXPLICIT_ONLY -> kind == CompanionEventKind.REMINDER
            CompanionReminderIntensity.OCCASIONAL ->
                kind == CompanionEventKind.REMINDER ||
                    kind == CompanionEventKind.PROMISE ||
                    kind == CompanionEventKind.ANNIVERSARY
            CompanionReminderIntensity.DAILY -> true
        }

    private fun isInQuietHours(now: ZonedDateTime, settings: CompanionReminderSettings): Boolean {
        val minuteOfDay = now.hour * 60 + now.minute
        val start = settings.quietStartMinutes
        val end = settings.quietEndMinutes
        if (start == end) return true
        return if (start < end) {
            minuteOfDay in start until end
        } else {
            minuteOfDay >= start || minuteOfDay < end
        }
    }

    private fun nextQuietHoursEnd(
        now: ZonedDateTime,
        settings: CompanionReminderSettings,
    ): ZonedDateTime {
        val endHour = settings.quietEndMinutes / 60
        val endMinute = settings.quietEndMinutes % 60
        val todayEnd = now.toLocalDate().atTime(endHour, endMinute).atZone(now.zone)
        val start = settings.quietStartMinutes
        val end = settings.quietEndMinutes
        return if (start < end) {
            todayEnd
        } else if (now.hour * 60 + now.minute >= start) {
            todayEnd.plusDays(1)
        } else {
            todayEnd
        }
    }
}
