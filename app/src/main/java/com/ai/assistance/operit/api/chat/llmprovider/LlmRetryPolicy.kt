package com.ai.assistance.operit.api.chat.llmprovider

internal object LlmRetryPolicy {
    const val MAX_RETRY_ATTEMPTS = 5
    private const val RETRY_BASE_DELAY_MS = 1000L

    fun nextDelayMs(retryAttempt: Int): Long {
        val normalizedAttempt = retryAttempt.coerceAtLeast(1)
        return RETRY_BASE_DELAY_MS * (1L shl (normalizedAttempt - 1))
    }

    fun shouldRetryHttpStatus(statusCode: Int, candidateKeyCount: Int): Boolean {
        return when {
            statusCode == 408 || statusCode == 409 || statusCode == 425 -> true
            statusCode == 429 -> true
            statusCode == 401 || statusCode == 403 -> candidateKeyCount > 1
            statusCode in 500..599 -> true
            else -> false
        }
    }
}
