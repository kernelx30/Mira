package com.ai.assistance.operit.ui.features.github

import com.ai.assistance.operit.data.api.GitHubAccessTokenResponse
import com.ai.assistance.operit.data.api.GitHubDeviceTokenPollResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubDeviceTokenPollerTest {

    @Test
    fun `pending then granted follows server interval`() = runBlocking {
        var now = 0L
        val waits = mutableListOf<Long>()
        val results = ArrayDeque<GitHubDeviceTokenPollResult>().apply {
            add(GitHubDeviceTokenPollResult.AuthorizationPending)
            add(
                GitHubDeviceTokenPollResult.Granted(
                    GitHubAccessTokenResponse("token", "bearer", "public_repo")
                )
            )
        }
        val poller = GitHubDeviceTokenPoller(
            elapsedRealtime = { now },
            wait = { milliseconds ->
                waits += milliseconds
                now += milliseconds
            }
        )

        val token = poller.awaitAccessToken(session(expiresAt = 20_000L, interval = 2L)) {
            Result.success(results.removeFirst())
        }

        assertEquals("token", token.access_token)
        assertEquals(listOf(5_000L, 5_000L), waits)
    }

    @Test
    fun `slow down adds five seconds to every later interval`() = runBlocking {
        var now = 0L
        val waits = mutableListOf<Long>()
        val results = ArrayDeque<GitHubDeviceTokenPollResult>().apply {
            add(GitHubDeviceTokenPollResult.SlowDown)
            add(GitHubDeviceTokenPollResult.AuthorizationPending)
            add(
                GitHubDeviceTokenPollResult.Granted(
                    GitHubAccessTokenResponse("token", "bearer", null)
                )
            )
        }
        val poller = GitHubDeviceTokenPoller(
            elapsedRealtime = { now },
            wait = { milliseconds ->
                waits += milliseconds
                now += milliseconds
            }
        )

        poller.awaitAccessToken(session(expiresAt = 30_000L, interval = 1L)) {
            Result.success(results.removeFirst())
        }

        assertEquals(listOf(5_000L, 10_000L, 10_000L), waits)
    }

    @Test
    fun `expired response stops polling`() = runBlocking {
        var now = 0L
        val poller = GitHubDeviceTokenPoller(
            elapsedRealtime = { now },
            wait = { now += it }
        )

        val error = runCatching {
            poller.awaitAccessToken(session(expiresAt = 10_000L, interval = 1L)) {
                Result.success(GitHubDeviceTokenPollResult.Expired)
            }
        }.exceptionOrNull()

        assertTrue(error is GitHubDeviceFlowException)
        assertEquals(
            GitHubDeviceFlowFailure.AUTHORIZATION_EXPIRED,
            (error as GitHubDeviceFlowException).failure
        )
    }

    @Test
    fun `local deadline stops before another request`() = runBlocking {
        var now = 0L
        var polls = 0
        val poller = GitHubDeviceTokenPoller(
            elapsedRealtime = { now },
            wait = { now += it }
        )

        val error = runCatching {
            poller.awaitAccessToken(session(expiresAt = 500L, interval = 5L)) {
                polls += 1
                Result.success(GitHubDeviceTokenPollResult.AuthorizationPending)
            }
        }.exceptionOrNull()

        assertEquals(0, polls)
        assertTrue(error is GitHubDeviceFlowException)
    }

    @Test
    fun `cancelling wait stops device polling immediately`() = runBlocking {
        val poller = GitHubDeviceTokenPoller(
            elapsedRealtime = { 0L },
            wait = { throw CancellationException("dialog closed") }
        )

        val error = try {
            poller.awaitAccessToken(session(expiresAt = 10_000L, interval = 5L)) {
                Result.success(GitHubDeviceTokenPollResult.AuthorizationPending)
            }
            null
        } catch (cancelled: CancellationException) {
            cancelled
        }

        assertTrue(error is CancellationException)
    }

    private fun session(expiresAt: Long, interval: Long) = GitHubDeviceLoginSession(
        deviceCode = "device-code",
        userCode = "ABCD-EFGH",
        verificationUri = "https://github.com/login/device",
        expiresInSeconds = expiresAt / 1000L,
        expiresAtElapsedRealtimeMillis = expiresAt,
        pollingIntervalSeconds = interval
    )
}
