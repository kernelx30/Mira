package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager
import com.ai.assistance.operit.core.chat.ChatHistoryExportIntentDetector
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.converter.ExportFormat
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.runBlocking

/** Exports the active conversation, or every conversation when the user explicitly asks for all. */
class ChatHistoryExportToolExecutor(private val context: Context) : ToolExecutor {
    override fun invoke(tool: AITool): ToolResult =
        runBlocking {
            try {
                val chatId = ToolExecutionManager.currentToolRuntimeContext()?.callerChatId.orEmpty()
                if (chatId.isBlank()) return@runBlocking error(tool, "Missing active chat context")
                val latestUserMessage =
                    AppDatabase.getDatabase(context.applicationContext)
                        .messageDao()
                        .getMessagesForChatDesc(chatId, RECENT_MESSAGE_LIMIT)
                        .firstOrNull { it.sender == "user" && it.content.isNotBlank() }
                        ?.content
                        .orEmpty()
                if (!ChatHistoryExportIntentDetector.isExplicitRequest(latestUserMessage)) {
                    return@runBlocking ignored(tool, "latest_user_message_is_not_an_explicit_chat_export_request")
                }

                val format =
                    ChatHistoryExportIntentDetector.detectFormat(latestUserMessage)
                        ?: parseFormat(parameter(tool, "format"))
                        ?: ExportFormat.MARKDOWN
                val exportAll = ChatHistoryExportIntentDetector.requestsAllChats(latestUserMessage)
                val manager = ChatHistoryManager.getInstance(context.applicationContext)
                val path =
                    if (exportAll) {
                        manager.exportChatHistoriesToDownloads(format)
                    } else {
                        manager.exportChatToDownloads(chatId, format)
                    } ?: return@runBlocking error(tool, "Chat history export did not create a file")

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                        FileOperationData(
                            operation = "export_chat_history",
                            path = path,
                            successful = true,
                            details =
                                "status=exported\nscope=${if (exportAll) "all" else "current"}\n" +
                                    "format=${format.name}\nChat history exported to $path",
                        ),
                )
            } catch (exception: Exception) {
                AppLogger.e(TAG, "Failed to export chat history", exception)
                error(tool, "Failed to export chat history: ${exception.message}")
            }
        }

    override fun validateParameters(tool: AITool): ToolValidationResult = ToolValidationResult(valid = true)

    private fun parameter(tool: AITool, name: String): String =
        tool.parameters.firstOrNull { it.name == name }?.value?.trim().orEmpty()

    private fun parseFormat(value: String): ExportFormat? =
        when (value.trim().uppercase()) {
            "JSON" -> ExportFormat.JSON
            "TXT", "TEXT", "PLAIN_TEXT" -> ExportFormat.TXT
            "HTML", "HTM" -> ExportFormat.HTML
            "MARKDOWN", "MD" -> ExportFormat.MARKDOWN
            else -> null
        }

    private fun error(tool: AITool, message: String): ToolResult =
        ToolResult(
            toolName = tool.name,
            success = false,
            result = StringResultData(""),
            error = message,
        )

    private fun ignored(tool: AITool, reason: String): ToolResult =
        ToolResult(
            toolName = tool.name,
            success = true,
            result =
                StringResultData(
                    "status=ignored\nreason=$reason\nNo chat file was created. Continue without claiming an export.",
                ),
        )

    private companion object {
        const val TAG = "ChatHistoryExport"
        const val RECENT_MESSAGE_LIMIT = 8
    }
}
