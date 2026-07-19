package com.ai.assistance.operit.integrations.intent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ai.assistance.operit.integrations.externalchat.ExternalChatRequest
import com.ai.assistance.operit.integrations.externalchat.ExternalChatRequestExecutor
import com.ai.assistance.operit.integrations.externalchat.ExternalChatResult
import com.ai.assistance.operit.data.preferences.ExternalHttpApiPreferences
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExternalChatReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ExternalChatReceiver"

        const val ACTION_EXTERNAL_CHAT = "com.ai.assistance.operit.EXTERNAL_CHAT"
        const val ACTION_EXTERNAL_CHAT_RESULT = "com.ai.assistance.operit.EXTERNAL_CHAT_RESULT"

        const val EXTRA_AUTH_TOKEN = "auth_token"
        const val EXTRA_CONFIRM_SENSITIVE = "confirm_sensitive"

        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_GROUP = "group"
        const val EXTRA_CREATE_NEW_CHAT = "create_new_chat"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CREATE_IF_NONE = "create_if_none"

        const val EXTRA_SHOW_FLOATING = "show_floating"
        const val EXTRA_RETURN_TOOL_STATUS = "return_tool_status"
        const val EXTRA_INITIAL_MODE = "initial_mode"
        const val EXTRA_AUTO_EXIT_AFTER_MS = "auto_exit_after_ms"
        const val EXTRA_TIMEOUT_MS = "timeout_ms"
        const val EXTRA_STOP_AFTER = "stop_after"

        const val EXTRA_REPLY_ACTION = "reply_action"
        const val EXTRA_REPLY_PACKAGE = "reply_package"

        const val EXTRA_RESULT_SUCCESS = "success"
        const val EXTRA_RESULT_CHAT_ID = "chat_id"
        const val EXTRA_RESULT_AI_RESPONSE = "ai_response"
        const val EXTRA_RESULT_ERROR = "error"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EXTERNAL_CHAT) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val unknownKeys =
                    ExternalChatIntentPolicy.validateExtraKeys(intent.extras?.keySet().orEmpty())
                if (unknownKeys != null) {
                    reject(context, intent, "Unknown external chat extras: $unknownKeys")
                    return@launch
                }

                val payload = intent.toExternalChatPayload()
                val expectedToken =
                    ExternalHttpApiPreferences.getInstance(context).getConfig().bearerToken
                when (val validation = ExternalChatIntentPolicy.validate(payload, expectedToken)) {
                    is ExternalChatIntentValidation.Rejected -> {
                        reject(context, intent, validation.reason)
                    }
                    is ExternalChatIntentValidation.Accepted -> {
                        if (!isInstalledPackage(context, validation.replyPackage)) {
                            AppLogger.w(TAG, "Rejected external chat reply package: ${validation.replyPackage}")
                            return@launch
                        }
                        if (!isConsistentSender(validation.replyPackage)) {
                            reject(context, intent, "reply_package does not match the broadcast sender")
                            return@launch
                        }
                        val result =
                            ExternalChatRequestExecutor(context.applicationContext)
                                .execute(validation.request)
                        sendResultBroadcast(
                            context = context,
                            action = validation.replyAction,
                            packageName = validation.replyPackage,
                            result = result,
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to handle external chat", e)
                reject(context, intent, e.message ?: "Unknown error")
            } finally {
                pending.finish()
            }
        }
    }

    private fun Intent.toExternalChatPayload(): ExternalChatIntentPayload =
        ExternalChatIntentPayload(
            authToken = getStringExtra(EXTRA_AUTH_TOKEN),
            confirmedSensitiveOperation = getBooleanExtra(EXTRA_CONFIRM_SENSITIVE, false),
            replyAction = getStringExtra(EXTRA_REPLY_ACTION),
            replyPackage = getStringExtra(EXTRA_REPLY_PACKAGE),
            request =
                ExternalChatRequest(
                    requestId = getStringExtra(EXTRA_REQUEST_ID),
                    message = getStringExtra(EXTRA_MESSAGE),
                    group = getStringExtra(EXTRA_GROUP),
                    createNewChat = getBooleanExtra(EXTRA_CREATE_NEW_CHAT, false),
                    chatId = getStringExtra(EXTRA_CHAT_ID),
                    createIfNone = getBooleanExtra(EXTRA_CREATE_IF_NONE, true),
                    showFloating = getBooleanExtra(EXTRA_SHOW_FLOATING, false),
                    returnToolStatus = getBooleanExtra(EXTRA_RETURN_TOOL_STATUS, true),
                    initialMode = getStringExtra(EXTRA_INITIAL_MODE),
                    autoExitAfterMs = getLongExtra(EXTRA_AUTO_EXIT_AFTER_MS, -1L),
                    timeoutMs = getLongExtra(EXTRA_TIMEOUT_MS, -1L),
                    stopAfter = getBooleanExtra(EXTRA_STOP_AFTER, false),
                ),
        )

    private fun reject(context: Context, intent: Intent, reason: String) {
        AppLogger.w(TAG, "Rejected external chat request: $reason")
        val replyPackage = intent.getStringExtra(EXTRA_REPLY_PACKAGE)?.trim().orEmpty()
        if (!ExternalChatIntentPolicy.isComponentName(replyPackage) ||
            !isInstalledPackage(context, replyPackage)
        ) {
            return
        }
        val replyAction =
            intent.getStringExtra(EXTRA_REPLY_ACTION)?.trim().orEmpty()
                .takeIf(ExternalChatIntentPolicy::isComponentName)
                ?: ACTION_EXTERNAL_CHAT_RESULT
        sendResultBroadcast(
            context = context,
            action = replyAction,
            packageName = replyPackage,
            result =
                ExternalChatResult(
                    requestId = intent.getStringExtra(EXTRA_REQUEST_ID),
                    success = false,
                    error = reason,
                ),
        )
    }

    private fun isConsistentSender(replyPackage: String): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val senderPackage = sentFromPackage ?: return true
        return senderPackage == replyPackage || senderPackage == "com.android.shell"
    }

    private fun isInstalledPackage(context: Context, packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    private fun sendResultBroadcast(
        context: Context,
        action: String,
        packageName: String,
        result: ExternalChatResult
    ) {
        val out = Intent(action)
        out.`package` = packageName
        if (!result.requestId.isNullOrBlank()) {
            out.putExtra(EXTRA_REQUEST_ID, result.requestId)
        }
        out.putExtra(EXTRA_RESULT_SUCCESS, result.success)
        if (!result.chatId.isNullOrBlank()) {
            out.putExtra(EXTRA_RESULT_CHAT_ID, result.chatId)
        }
        if (!result.aiResponse.isNullOrBlank()) {
            out.putExtra(EXTRA_RESULT_AI_RESPONSE, result.aiResponse)
        }
        if (!result.error.isNullOrBlank()) {
            out.putExtra(EXTRA_RESULT_ERROR, result.error)
        }
        context.sendBroadcast(out)
    }
}
