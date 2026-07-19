package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmRetryPolicyTest {
    @Test
    fun invalidRequestIsNotRetried() {
        assertFalse(LlmRetryPolicy.shouldRetryHttpStatus(400, candidateKeyCount = 1))
        assertFalse(LlmRetryPolicy.shouldRetryHttpStatus(422, candidateKeyCount = 1))
    }

    @Test
    fun transientStatusesAreRetried() {
        assertTrue(LlmRetryPolicy.shouldRetryHttpStatus(408, candidateKeyCount = 1))
        assertTrue(LlmRetryPolicy.shouldRetryHttpStatus(429, candidateKeyCount = 1))
        assertTrue(LlmRetryPolicy.shouldRetryHttpStatus(503, candidateKeyCount = 1))
    }

    @Test
    fun authenticationRetriesOnlyWhenAnotherKeyExists() {
        assertFalse(LlmRetryPolicy.shouldRetryHttpStatus(401, candidateKeyCount = 1))
        assertTrue(LlmRetryPolicy.shouldRetryHttpStatus(401, candidateKeyCount = 2))
        assertFalse(LlmRetryPolicy.shouldRetryHttpStatus(403, candidateKeyCount = 1))
        assertTrue(LlmRetryPolicy.shouldRetryHttpStatus(403, candidateKeyCount = 2))
    }
}
