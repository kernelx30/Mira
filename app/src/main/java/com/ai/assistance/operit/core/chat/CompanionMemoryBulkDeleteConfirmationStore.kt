package com.ai.assistance.operit.core.chat

/** Holds the exact bulk-delete snapshot between the request and the user's second confirmation. */
object CompanionMemoryBulkDeleteConfirmationStore {
    private const val CONFIRMATION_WINDOW_MS = 10 * 60 * 1_000L
    private val pendingDeletes = mutableMapOf<String, PendingBulkDelete>()

    fun request(key: String, recordIds: List<String>, nowMs: Long = System.currentTimeMillis()) {
        if (key.isBlank() || recordIds.isEmpty()) return
        synchronized(pendingDeletes) {
            removeExpired(nowMs)
            pendingDeletes[key] = PendingBulkDelete(recordIds.distinct(), nowMs + CONFIRMATION_WINDOW_MS)
        }
    }

    fun consume(key: String, nowMs: Long = System.currentTimeMillis()): List<String>? =
        synchronized(pendingDeletes) {
            removeExpired(nowMs)
            pendingDeletes.remove(key)?.recordIds
        }

    fun clear(key: String) {
        synchronized(pendingDeletes) {
            pendingDeletes.remove(key)
        }
    }

    private fun removeExpired(nowMs: Long) {
        pendingDeletes.entries.removeAll { (_, pending) -> pending.expiresAtMs <= nowMs }
    }

    private data class PendingBulkDelete(
        val recordIds: List<String>,
        val expiresAtMs: Long,
    )
}
