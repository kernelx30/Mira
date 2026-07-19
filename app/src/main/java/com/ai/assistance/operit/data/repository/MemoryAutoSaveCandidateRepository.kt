package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.model.MemoryAutoSaveCandidate
import com.ai.assistance.operit.data.model.MemoryAutoSaveCandidate_
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import java.util.Date

internal fun shouldQuarantineLegacyMemoryCandidate(candidate: MemoryAutoSaveCandidate): Boolean =
    candidate.companionId.isBlank() &&
        candidate.status != MemoryAutoSaveCandidate.STATUS_SKIPPED_LEGACY_UNSCOPED

class MemoryAutoSaveCandidateRepository(
    context: Context,
    profileId: String
) {
    private val store = ObjectBoxManager.get(context, profileId)
    private val candidateBox: Box<MemoryAutoSaveCandidate> = store.boxFor()

    @Synchronized
    fun enqueue(
        chatId: String,
        companionId: String,
        triggerMessageTimestamp: Long,
        sourceType: String = MemoryAutoSaveCandidate.SOURCE_TYPE_REPLY_FINALIZED_AUTO
    ): Long {
        if (chatId.isBlank() || companionId.isBlank() || triggerMessageTimestamp <= 0L) return 0L
        candidateBox
            .query(MemoryAutoSaveCandidate_.chatId.equal(chatId))
            .build()
            .find()
            .firstOrNull {
                it.triggerMessageTimestamp == triggerMessageTimestamp &&
                    it.sourceType == sourceType &&
                    it.companionId == companionId
            }
            ?.let { return it.id }

        val now = Date()
        val candidate =
            MemoryAutoSaveCandidate(
                chatId = chatId,
                companionId = companionId,
                triggerMessageTimestamp = triggerMessageTimestamp,
                createdAt = now,
                updatedAt = now,
                status = MemoryAutoSaveCandidate.STATUS_PENDING,
                sourceType = sourceType
            )
        return candidateBox.put(candidate)
    }

    fun enqueueSelectedUserMessages(
        chatId: String,
        companionId: String,
        triggerMessageTimestamps: List<Long>
    ): Int {
        val normalizedTimestamps =
            triggerMessageTimestamps
                .filter { it > 0L }
                .distinct()
                .sorted()
        if (chatId.isBlank() || companionId.isBlank() || normalizedTimestamps.isEmpty()) return 0

        val queuedTimestamps =
            candidateBox
                .query(MemoryAutoSaveCandidate_.chatId.equal(chatId))
                .build()
                .find()
                .asSequence()
                .filter {
                    MemoryAutoSaveCandidate.isSelectedUserMessageSource(it.sourceType)
                        && it.companionId == companionId
                }
                .map { it.triggerMessageTimestamp }
                .toHashSet()
        val newTimestamps = normalizedTimestamps.filterNot(queuedTimestamps::contains)
        newTimestamps.forEach { timestamp ->
            enqueue(
                chatId = chatId,
                companionId = companionId,
                triggerMessageTimestamp = timestamp,
                sourceType = MemoryAutoSaveCandidate.SOURCE_TYPE_SELECTED_USER_MESSAGE
            )
        }
        return newTimestamps.size
    }

    fun getPendingAndFailedCandidates(): List<MemoryAutoSaveCandidate> {
        quarantineLegacyUnscopedCandidates()
        recoverStaleProcessing()
        return candidateBox
            .query(
                MemoryAutoSaveCandidate_.status
                    .equal(MemoryAutoSaveCandidate.STATUS_PENDING)
                    .or(
                        MemoryAutoSaveCandidate_.status.equal(
                            MemoryAutoSaveCandidate.STATUS_FAILED
                        )
                    )
            )
            .build()
            .find()
            .sortedBy { it.createdAt.time }
    }

    fun getProcessableCandidates(nowMs: Long = System.currentTimeMillis()): List<MemoryAutoSaveCandidate> {
        recoverStaleProcessing(nowMs)
        return getPendingAndFailedCandidates().filter { candidate ->
            candidate.status == MemoryAutoSaveCandidate.STATUS_PENDING ||
                candidate.nextAttemptAtMs <= nowMs
        }
    }

    fun recoverStaleProcessing(nowMs: Long = System.currentTimeMillis()): Int {
        val staleBefore = nowMs - MemoryAutoSaveCandidate.PROCESSING_STALE_AFTER_MS
        val stale =
            candidateBox.all.filter {
                it.status == MemoryAutoSaveCandidate.STATUS_PROCESSING &&
                    it.updatedAt.time <= staleBefore
            }
        if (stale.isEmpty()) return 0
        val now = Date(nowMs)
        stale.forEach { candidate ->
            candidate.status = MemoryAutoSaveCandidate.STATUS_PENDING
            candidate.updatedAt = now
            candidate.nextAttemptAtMs = 0L
            candidate.lastError = "Recovered after interrupted processing"
        }
        candidateBox.put(stale)
        return stale.size
    }

    fun quarantineLegacyUnscopedCandidates(nowMs: Long = System.currentTimeMillis()): Int {
        val legacyCandidates = candidateBox.all.filter(::shouldQuarantineLegacyMemoryCandidate)
        if (legacyCandidates.isEmpty()) return 0
        val now = Date(nowMs)
        legacyCandidates.forEach { candidate ->
            candidate.status = MemoryAutoSaveCandidate.STATUS_SKIPPED_LEGACY_UNSCOPED
            candidate.updatedAt = now
            candidate.nextAttemptAtMs = 0L
            candidate.lastError = "Legacy candidate has no companion target snapshot"
        }
        candidateBox.put(legacyCandidates)
        return legacyCandidates.size
    }

    fun countPendingAndFailedChats(): Int {
        return getPendingAndFailedCandidates()
            .map { it.chatId }
            .filter { it.isNotBlank() }
            .distinct()
            .size
    }

    fun countPendingAndFailedCandidates(): Int {
        return getPendingAndFailedCandidates().size
    }

    fun markProcessing(candidateIds: List<Long>) {
        if (candidateIds.isEmpty()) return
        val now = Date()
        val candidates = candidateIds.mapNotNull { candidateBox.get(it) }
        candidates.forEach { candidate ->
            candidate.status = MemoryAutoSaveCandidate.STATUS_PROCESSING
            candidate.updatedAt = now
            candidate.lastError = ""
            candidate.nextAttemptAtMs = 0L
        }
        candidateBox.put(candidates)
    }

    fun markPending(candidateIds: List<Long>) {
        if (candidateIds.isEmpty()) return
        val now = Date()
        val candidates = candidateIds.mapNotNull { candidateBox.get(it) }
        candidates.forEach { candidate ->
            candidate.status = MemoryAutoSaveCandidate.STATUS_PENDING
            candidate.updatedAt = now
            candidate.lastError = ""
            candidate.nextAttemptAtMs = 0L
        }
        candidateBox.put(candidates)
    }

    fun deleteCandidates(candidateIds: List<Long>) {
        if (candidateIds.isEmpty()) return
        candidateIds.forEach { candidateBox.remove(it) }
    }

    fun markFailed(candidateIds: List<Long>, errorMessage: String) {
        if (candidateIds.isEmpty()) return
        val now = Date()
        val normalizedError = errorMessage.take(500)
        val candidates = candidateIds.mapNotNull { candidateBox.get(it) }
        candidates.forEach { candidate ->
            candidate.status = MemoryAutoSaveCandidate.STATUS_FAILED
            candidate.attemptCount += 1
            candidate.lastError = normalizedError
            candidate.updatedAt = now
            candidate.nextAttemptAtMs =
                now.time + MemoryAutoSaveCandidate.retryDelayMs(candidate.attemptCount)
        }
        candidateBox.put(candidates)
    }
}
