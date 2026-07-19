package com.ai.assistance.operit.integrations.intent

import com.ai.assistance.operit.integrations.externalchat.ExternalChatRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalChatIntentPolicyTest {
    @Test
    fun acceptsAuthenticatedConfirmedBoundedRequest() {
        val result =
            ExternalChatIntentPolicy.validate(
                validPayload(),
                expectedToken = TOKEN,
            )

        assertTrue(result is ExternalChatIntentValidation.Accepted)
        result as ExternalChatIntentValidation.Accepted
        assertEquals("WINDOW", result.request.initialMode)
        assertEquals("client.app", result.replyPackage)
    }

    @Test
    fun rejectsWrongTokenAndMissingConfirmation() {
        assertTrue(
            ExternalChatIntentPolicy.validate(validPayload(authToken = "wrong"), TOKEN) is
                ExternalChatIntentValidation.Rejected,
        )
        assertTrue(
            ExternalChatIntentPolicy.validate(
                validPayload(confirmed = false),
                TOKEN,
            ) is ExternalChatIntentValidation.Rejected,
        )
    }

    @Test
    fun rejectsUnknownExtrasAndOutOfRangeParameters() {
        assertEquals(
            "unexpected",
            ExternalChatIntentPolicy.validateExtraKeys(
                ExternalChatIntentPolicy.allowedExtraKeys + "unexpected",
            ),
        )
        assertTrue(
            ExternalChatIntentPolicy.validate(
                validPayload(
                    request =
                        ExternalChatRequest(
                            message = "hello",
                            initialMode = "not-a-mode",
                            timeoutMs = ExternalChatIntentPolicy.MAX_TIMEOUT_MS + 1,
                        ),
                ),
                TOKEN,
            ) is ExternalChatIntentValidation.Rejected,
        )
    }

    private fun validPayload(
        authToken: String = TOKEN,
        confirmed: Boolean = true,
        request: ExternalChatRequest =
            ExternalChatRequest(
                requestId = "request-1",
                message = "hello",
                initialMode = "window",
            ),
    ) =
        ExternalChatIntentPayload(
            authToken = authToken,
            confirmedSensitiveOperation = confirmed,
            replyAction = ExternalChatReceiver.ACTION_EXTERNAL_CHAT_RESULT,
            replyPackage = "client.app",
            request = request,
        )

    companion object {
        private const val TOKEN = "0123456789abcdef0123456789abcdef"
    }
}
