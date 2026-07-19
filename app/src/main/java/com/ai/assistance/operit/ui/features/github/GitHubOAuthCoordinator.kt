package com.ai.assistance.operit.ui.features.github

import android.content.Context
import android.os.SystemClock
import com.ai.assistance.operit.data.api.GitHubAccessTokenResponse
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.GitHubDeviceTokenPollResult
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class GitHubDeviceLoginSession(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Long,
    val expiresAtElapsedRealtimeMillis: Long,
    val pollingIntervalSeconds: Long
)

enum class GitHubDeviceFlowFailure {
    CLIENT_ID_MISSING,
    AUTHORIZATION_EXPIRED,
    ACCESS_DENIED,
    TOKEN_REJECTED,
    TOKEN_VERIFICATION_FAILED,
    INSUFFICIENT_SCOPE
}

class GitHubDeviceFlowException(
    val failure: GitHubDeviceFlowFailure,
    message: String
) : Exception(message)

internal class GitHubDeviceTokenPoller(
    private val elapsedRealtime: () -> Long,
    private val wait: suspend (Long) -> Unit
) {
    suspend fun awaitAccessToken(
        session: GitHubDeviceLoginSession,
        poll: suspend (String) -> Result<GitHubDeviceTokenPollResult>
    ): GitHubAccessTokenResponse {
        var intervalSeconds = session.pollingIntervalSeconds.coerceAtLeast(MINIMUM_INTERVAL_SECONDS)

        while (true) {
            val remainingMillis = session.expiresAtElapsedRealtimeMillis - elapsedRealtime()
            if (remainingMillis <= 0L) {
                throw GitHubDeviceFlowException(
                    GitHubDeviceFlowFailure.AUTHORIZATION_EXPIRED,
                    "GitHub device authorization expired"
                )
            }

            wait(minOf(intervalSeconds * 1000L, remainingMillis))
            if (elapsedRealtime() >= session.expiresAtElapsedRealtimeMillis) {
                throw GitHubDeviceFlowException(
                    GitHubDeviceFlowFailure.AUTHORIZATION_EXPIRED,
                    "GitHub device authorization expired"
                )
            }

            when (val result = poll(session.deviceCode).getOrThrow()) {
                is GitHubDeviceTokenPollResult.Granted -> return result.token
                GitHubDeviceTokenPollResult.AuthorizationPending -> Unit
                GitHubDeviceTokenPollResult.SlowDown -> intervalSeconds += SLOW_DOWN_SECONDS
                GitHubDeviceTokenPollResult.Expired -> throw GitHubDeviceFlowException(
                    GitHubDeviceFlowFailure.AUTHORIZATION_EXPIRED,
                    "GitHub device authorization expired"
                )
                GitHubDeviceTokenPollResult.AccessDenied -> throw GitHubDeviceFlowException(
                    GitHubDeviceFlowFailure.ACCESS_DENIED,
                    "GitHub authorization was denied"
                )
                is GitHubDeviceTokenPollResult.Rejected -> throw GitHubDeviceFlowException(
                    GitHubDeviceFlowFailure.TOKEN_REJECTED,
                    result.description?.takeIf { it.isNotBlank() }
                        ?: "GitHub rejected the device authorization (${result.error})"
                )
            }
        }
    }

    companion object {
        private const val MINIMUM_INTERVAL_SECONDS = 5L
        private const val SLOW_DOWN_SECONDS = 5L
    }
}

class GitHubOAuthCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val githubAuth = GitHubAuthPreferences.getInstance(appContext)
    private val githubApiService = GitHubApiService(appContext)
    private val tokenPoller = GitHubDeviceTokenPoller(
        elapsedRealtime = SystemClock::elapsedRealtime,
        wait = { delay(it) }
    )

    suspend fun startDeviceLogin(): Result<GitHubDeviceLoginSession> {
        return try {
            val clientId = configuredClientId()
            val authorization = githubApiService.requestDeviceAuthorization(
                clientId = clientId,
                scope = GitHubAuthPreferences.GITHUB_SCOPE
            ).getOrThrow()
            Result.success(
                GitHubDeviceLoginSession(
                    deviceCode = authorization.device_code,
                    userCode = authorization.user_code,
                    verificationUri = authorization.verification_uri,
                    expiresInSeconds = authorization.expires_in,
                    expiresAtElapsedRealtimeMillis =
                        SystemClock.elapsedRealtime() + authorization.expires_in * 1000L,
                    pollingIntervalSeconds = authorization.interval
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun completeDeviceLogin(session: GitHubDeviceLoginSession): Result<GitHubUser> {
        return try {
            val clientId = configuredClientId()
            val tokenResponse = tokenPoller.awaitAccessToken(session) { deviceCode ->
                githubApiService.pollDeviceAccessToken(
                    clientId = clientId,
                    deviceCode = deviceCode
                )
            }

            if (!githubAuth.hasRequiredScopes(tokenResponse.scope)) {
                throw GitHubDeviceFlowException(
                    GitHubDeviceFlowFailure.INSUFFICIENT_SCOPE,
                    "GitHub did not grant all required permissions"
                )
            }

            val user = githubApiService
                .getCurrentUserWithAccessToken(tokenResponse.access_token)
                .getOrElse {
                    throw GitHubDeviceFlowException(
                        GitHubDeviceFlowFailure.TOKEN_VERIFICATION_FAILED,
                        "GitHub token verification failed"
                    )
                }

            // Keep the existing account untouched until both token and identity are verified.
            githubAuth.saveAuthInfo(
                accessToken = tokenResponse.access_token,
                tokenType = tokenResponse.token_type,
                userInfo = user,
                grantedScope = tokenResponse.scope
            )
            Result.success(user)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun configuredClientId(): String {
        val clientId = GitHubAuthPreferences.GITHUB_CLIENT_ID.trim()
        if (clientId.isBlank()) {
            throw GitHubDeviceFlowException(
                GitHubDeviceFlowFailure.CLIENT_ID_MISSING,
                "GitHub OAuth client ID is not configured"
            )
        }
        return clientId
    }
}
