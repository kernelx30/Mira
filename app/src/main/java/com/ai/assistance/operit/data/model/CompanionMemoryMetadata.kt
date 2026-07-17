package com.ai.assistance.operit.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class CompanionEventKind(val storageValue: String) {
    PROMISE("promise"),
    EVENT("event"),
    ANNIVERSARY("anniversary"),
    REMINDER("reminder");

    companion object {
        fun fromStorageValue(value: String?): CompanionEventKind? =
            entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() }
    }
}

enum class CompanionEventStatus(val storageValue: String) {
    PENDING("pending"),
    DONE("done"),
    CANCELLED("cancelled");

    companion object {
        fun fromStorageValue(value: String?): CompanionEventStatus =
            entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() } ?: PENDING
    }
}

data class CompanionMemoryMetadata(
    val kind: CompanionEventKind,
    val eventAtMs: Long? = null,
    val status: CompanionEventStatus = CompanionEventStatus.PENDING,
    val reminderText: String = "",
    val characterId: String = "",
    val characterName: String = "",
    val characterGroupId: String = "",
    val chatId: String = "",
    val notifiedAtMs: Long? = null,
    val nextAttemptAtMs: Long? = null,
) {
    fun toPropertyValues(): Map<String, String> =
        buildMap {
            put(CompanionMemoryKeys.KIND, kind.storageValue)
            put(CompanionMemoryKeys.STATUS, status.storageValue)
            eventAtMs?.let { put(CompanionMemoryKeys.EVENT_AT, it.toString()) }
            reminderText.trim().takeIf { it.isNotEmpty() }
                ?.let { put(CompanionMemoryKeys.REMINDER_TEXT, it) }
            characterId.trim().takeIf { it.isNotEmpty() }
                ?.let { put(CompanionMemoryKeys.CHARACTER_ID, it) }
            characterName.trim().takeIf { it.isNotEmpty() }
                ?.let { put(CompanionMemoryKeys.CHARACTER_NAME, it) }
            characterGroupId.trim().takeIf { it.isNotEmpty() }
                ?.let { put(CompanionMemoryKeys.CHARACTER_GROUP_ID, it) }
            chatId.trim().takeIf { it.isNotEmpty() }
                ?.let { put(CompanionMemoryKeys.CHAT_ID, it) }
            notifiedAtMs?.let { put(CompanionMemoryKeys.NOTIFIED_AT, it.toString()) }
            nextAttemptAtMs?.let { put(CompanionMemoryKeys.NEXT_ATTEMPT_AT, it.toString()) }
        }

    companion object {
        fun fromPropertyValues(values: Map<String, String>): CompanionMemoryMetadata? {
            val kind = CompanionEventKind.fromStorageValue(values[CompanionMemoryKeys.KIND]) ?: return null
            return CompanionMemoryMetadata(
                kind = kind,
                eventAtMs = values[CompanionMemoryKeys.EVENT_AT]?.toLongOrNull()?.takeIf { it > 0L },
                status = CompanionEventStatus.fromStorageValue(values[CompanionMemoryKeys.STATUS]),
                reminderText = values[CompanionMemoryKeys.REMINDER_TEXT].orEmpty(),
                characterId = values[CompanionMemoryKeys.CHARACTER_ID].orEmpty(),
                characterName = values[CompanionMemoryKeys.CHARACTER_NAME].orEmpty(),
                characterGroupId = values[CompanionMemoryKeys.CHARACTER_GROUP_ID].orEmpty(),
                chatId = values[CompanionMemoryKeys.CHAT_ID].orEmpty(),
                notifiedAtMs = values[CompanionMemoryKeys.NOTIFIED_AT]?.toLongOrNull()?.takeIf { it > 0L },
                nextAttemptAtMs =
                    values[CompanionMemoryKeys.NEXT_ATTEMPT_AT]?.toLongOrNull()?.takeIf { it > 0L },
            )
        }

        fun parseEventAt(value: String?, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
            val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            normalized.toLongOrNull()?.takeIf { it > 0L }?.let { return it }

            val parsers =
                listOf<(String) -> Instant>(
                    { Instant.parse(it) },
                    { OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() },
                    { ZonedDateTime.parse(it, DateTimeFormatter.ISO_ZONED_DATE_TIME).toInstant() },
                    { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zoneId).toInstant() },
                    { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(zoneId).toInstant() },
                )
            parsers.forEach { parser ->
                try {
                    return parser(normalized).toEpochMilli()
                } catch (_: DateTimeParseException) {
                    // Continue through the documented timestamp formats.
                }
            }
            return null
        }
    }
}

object CompanionMemoryKeys {
    const val KIND = "companion.kind"
    const val EVENT_AT = "companion.eventAt"
    const val STATUS = "companion.status"
    const val REMINDER_TEXT = "companion.reminderText"
    const val CHARACTER_ID = "companion.characterId"
    const val CHARACTER_NAME = "companion.characterName"
    const val CHARACTER_GROUP_ID = "companion.characterGroupId"
    const val CHAT_ID = "companion.chatId"
    const val NOTIFIED_AT = "companion.notifiedAt"
    const val NEXT_ATTEMPT_AT = "companion.nextAttemptAt"

    val ALL: Set<String> =
        setOf(
            KIND,
            EVENT_AT,
            STATUS,
            REMINDER_TEXT,
            CHARACTER_ID,
            CHARACTER_NAME,
            CHARACTER_GROUP_ID,
            CHAT_ID,
            NOTIFIED_AT,
            NEXT_ATTEMPT_AT,
        )
}

fun Memory.companionMetadata(): CompanionMemoryMetadata? =
    CompanionMemoryMetadata.fromPropertyValues(properties.associate { it.key to it.value })
