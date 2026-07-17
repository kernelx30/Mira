package com.ai.assistance.operit.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import java.util.Date

@Entity
data class MemoryAutoSaveCandidate(
    @Id var id: Long = 0,
    @Index var chatId: String = "",
    var triggerMessageTimestamp: Long = 0L,
    var createdAt: Date = Date(),
    var updatedAt: Date = Date(),
    var status: String = STATUS_PENDING,
    var attemptCount: Int = 0,
    var lastError: String = "",
    var sourceType: String = SOURCE_TYPE_REPLY_FINALIZED_AUTO,
    var nextAttemptAtMs: Long = 0L,
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_PROCESSING = "processing"
        const val STATUS_FAILED = "failed"

        const val SOURCE_TYPE_REPLY_FINALIZED_AUTO = "reply_finalized_auto"
        const val SOURCE_TYPE_HIGH_VALUE_AUTO = "high_value_auto"
        const val SOURCE_TYPE_SELECTED_USER_MESSAGE = "selected_user_message"

        const val PROCESSING_STALE_AFTER_MS = 10L * 60L * 1000L
        const val MAX_RETRY_DELAY_MS = 6L * 60L * 60L * 1000L

        fun retryDelayMs(attemptCount: Int): Long {
            val exponent = (attemptCount - 1).coerceIn(0, 8)
            return (60_000L * (1L shl exponent)).coerceAtMost(MAX_RETRY_DELAY_MS)
        }

        fun isSelectedUserMessageSource(sourceType: String): Boolean {
            return sourceType == SOURCE_TYPE_SELECTED_USER_MESSAGE
        }

        fun isHighValueAutoSource(sourceType: String): Boolean {
            return sourceType == SOURCE_TYPE_HIGH_VALUE_AUTO
        }
    }
}
