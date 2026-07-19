package com.ai.assistance.operit.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubDeviceFlowResponseTest {

    @Test
    fun `decodes granted token without exposing response details`() {
        val result = decodeGitHubDeviceTokenResponse(
            """{"access_token":"temporary-token","token_type":"bearer","scope":"public_repo read:user"}"""
        )

        assertTrue(result is GitHubDeviceTokenPollResult.Granted)
        val granted = result as GitHubDeviceTokenPollResult.Granted
        assertEquals("temporary-token", granted.token.access_token)
        assertEquals("public_repo read:user", granted.token.scope)
    }

    @Test
    fun `maps all device flow protocol states`() {
        assertEquals(
            GitHubDeviceTokenPollResult.AuthorizationPending,
            decodeGitHubDeviceTokenResponse("""{"error":"authorization_pending"}""")
        )
        assertEquals(
            GitHubDeviceTokenPollResult.SlowDown,
            decodeGitHubDeviceTokenResponse("""{"error":"slow_down"}""")
        )
        assertEquals(
            GitHubDeviceTokenPollResult.Expired,
            decodeGitHubDeviceTokenResponse("""{"error":"expired_token"}""")
        )
        assertEquals(
            GitHubDeviceTokenPollResult.AccessDenied,
            decodeGitHubDeviceTokenResponse("""{"error":"access_denied"}""")
        )
    }

    @Test
    fun `preserves unknown protocol error as rejected state`() {
        val result = decodeGitHubDeviceTokenResponse(
            """{"error":"incorrect_client_credentials","error_description":"bad client"}"""
        )

        assertTrue(result is GitHubDeviceTokenPollResult.Rejected)
        val rejected = result as GitHubDeviceTokenPollResult.Rejected
        assertEquals("incorrect_client_credentials", rejected.error)
        assertEquals("bad client", rejected.description)
    }

    @Test
    fun `rejects token response without token type`() {
        val result = decodeGitHubDeviceTokenResponse(
            """{"access_token":"temporary-token","scope":"public_repo"}"""
        )

        assertTrue(result is GitHubDeviceTokenPollResult.Rejected)
        assertEquals("invalid_response", (result as GitHubDeviceTokenPollResult.Rejected).error)
    }
}
