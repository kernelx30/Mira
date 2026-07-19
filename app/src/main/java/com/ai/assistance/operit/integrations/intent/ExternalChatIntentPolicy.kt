package com.ai.assistance.operit.integrations.intent

import com.ai.assistance.operit.integrations.externalchat.ExternalChatRequest
import java.security.MessageDigest

internal data class ExternalChatIntentPayload(
    val authToken: String?,
    val confirmedSensitiveOperation: Boolean,
    val replyAction: String?,
    val replyPackage: String?,
    val request: ExternalChatRequest,
)

internal sealed interface ExternalChatIntentValidation {
    data class Accepted(
        val request: ExternalChatRequest,
        val replyAction: String,
        val replyPackage: String,
    ) : ExternalChatIntentValidation

    data class Rejected(val reason: String) : ExternalChatIntentValidation
}

internal object ExternalChatIntentPolicy {
    const val MAX_MESSAGE_LENGTH = 64 * 1024
    const val MAX_IDENTIFIER_LENGTH = 256
    const val MAX_TIMEOUT_MS = 10 * 60 * 1000L

    val allowedExtraKeys =
        setOf(
            ExternalChatReceiver.EXTRA_AUTH_TOKEN,
            ExternalChatReceiver.EXTRA_CONFIRM_SENSITIVE,
            ExternalChatReceiver.EXTRA_REQUEST_ID,
            ExternalChatReceiver.EXTRA_MESSAGE,
            ExternalChatReceiver.EXTRA_GROUP,
            ExternalChatReceiver.EXTRA_CREATE_NEW_CHAT,
            ExternalChatReceiver.EXTRA_CHAT_ID,
            ExternalChatReceiver.EXTRA_CREATE_IF_NONE,
            ExternalChatReceiver.EXTRA_SHOW_FLOATING,
            ExternalChatReceiver.EXTRA_RETURN_TOOL_STATUS,
            ExternalChatReceiver.EXTRA_INITIAL_MODE,
            ExternalChatReceiver.EXTRA_AUTO_EXIT_AFTER_MS,
            ExternalChatReceiver.EXTRA_TIMEOUT_MS,
            ExternalChatReceiver.EXTRA_STOP_AFTER,
            ExternalChatReceiver.EXTRA_REPLY_ACTION,
            ExternalChatReceiver.EXTRA_REPLY_PACKAGE,
        )

    private val componentNamePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+$")
    private val allowedInitialModes =
        setOf("WINDOW", "BALL", "VOICE_BALL", "FULLSCREEN", "RESULT_DISPLAY", "SCREEN_OCR")

    fun validateExtraKeys(keys: Set<String>): String? {
        val unknown = keys - allowedExtraKeys
        return unknown.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(", ")
    }

    fun validate(
        payload: ExternalChatIntentPayload,
        expectedToken: String,
    ): ExternalChatIntentValidation {
        if (expectedToken.isBlank() || !tokensMatch(payload.authToken, expectedToken)) {
            return ExternalChatIntentValidation.Rejected("External chat authentication failed")
        }
        if (!payload.confirmedSensitiveOperation) {
            return ExternalChatIntentValidation.Rejected(
                "Sensitive external chat operation requires confirm_sensitive=true",
            )
        }

        val replyPackage = payload.replyPackage?.trim().orEmpty()
        if (!isComponentName(replyPackage)) {
            return ExternalChatIntentValidation.Rejected("reply_package is missing or invalid")
        }
        val replyAction =
            payload.replyAction?.trim()?.takeIf { it.isNotEmpty() }
                ?: ExternalChatReceiver.ACTION_EXTERNAL_CHAT_RESULT
        if (!isComponentName(replyAction)) {
            return ExternalChatIntentValidation.Rejected("reply_action is invalid")
        }

        val request = payload.request
        val message = request.message?.trim().orEmpty()
        if (message.isEmpty() || message.length > MAX_MESSAGE_LENGTH) {
            return ExternalChatIntentValidation.Rejected(
                "message must contain 1..$MAX_MESSAGE_LENGTH characters",
            )
        }
        val boundedFields =
            listOf(
                "request_id" to request.requestId,
                "group" to request.group,
                "chat_id" to request.chatId,
            )
        boundedFields.firstOrNull { (_, value) -> value != null && value.length > MAX_IDENTIFIER_LENGTH }
            ?.let { (name, _) ->
                return ExternalChatIntentValidation.Rejected("$name is too long")
            }

        val initialMode = request.initialMode?.trim()?.uppercase()
        if (initialMode != null && initialMode !in allowedInitialModes) {
            return ExternalChatIntentValidation.Rejected("initial_mode is invalid")
        }
        if (!isAllowedTimeout(request.autoExitAfterMs) || !isAllowedTimeout(request.timeoutMs)) {
            return ExternalChatIntentValidation.Rejected(
                "timeout values must be -1 or between 1 and $MAX_TIMEOUT_MS milliseconds",
            )
        }

        return ExternalChatIntentValidation.Accepted(
            request = request.copy(message = message, initialMode = initialMode),
            replyAction = replyAction,
            replyPackage = replyPackage,
        )
    }

    fun isComponentName(value: String): Boolean =
        value.length <= MAX_IDENTIFIER_LENGTH && componentNamePattern.matches(value)

    fun tokensMatch(candidate: String?, expected: String): Boolean {
        if (candidate == null) return false
        return MessageDigest.isEqual(
            candidate.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8),
        )
    }

    private fun isAllowedTimeout(value: Long): Boolean = value == -1L || value in 1..MAX_TIMEOUT_MS
}
