package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager
import com.ai.assistance.operit.core.chat.CompanionMemoryExportIntentDetector
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.backup.MiraMemoryArchiveManager
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Creates a complete, portable memory archive only after an explicit user request. */
class CompanionMemoryExportToolExecutor(private val context: Context) : ToolExecutor {
    override fun invoke(tool: AITool): ToolResult =
        runBlocking {
            try {
                val runtime = ToolExecutionManager.currentToolRuntimeContext()
                val chatId = runtime?.callerChatId.orEmpty()
                if (chatId.isBlank()) return@runBlocking error(tool, "Missing active chat context")
                val latestUserMessage =
                    AppDatabase.getDatabase(context.applicationContext)
                        .messageDao()
                        .getMessagesForChatDesc(chatId, 8)
                        .firstOrNull { it.sender == "user" && it.content.isNotBlank() }
                        ?.content
                        .orEmpty()
                if (!CompanionMemoryExportIntentDetector.isExplicitRequest(latestUserMessage)) {
                    return@runBlocking ignored(tool, "latest_user_message_is_not_an_explicit_memory_export_request")
                }
                val profileId = resolveProfileId(runtime?.callerCardId)
                val exported =
                    MiraMemoryArchiveManager.exportToBackupDir(
                        context = context.applicationContext,
                        profileId = profileId,
                    )
                val stats = exported.stats
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result =
                        FileOperationData(
                            operation = "export_companion_memory",
                            path = exported.file.absolutePath,
                            successful = true,
                            details =
                                "Mira memory archive exported to ${exported.file.absolutePath}\n" +
                                    "Memories: ${stats.totalMemories} " +
                                    "(structured=${stats.structuredRecords}, legacy=${stats.legacyMemories}); " +
                                    "links=${stats.totalLinks}; evidence=${stats.totalEvidence}; " +
                                    "episodes=${stats.episodes}; grants=${stats.grants}. " +
                                    "This JSON archive is portable and can be imported from Mira backup settings.",
                        ),
                )
            } catch (error: Exception) {
                AppLogger.e(TAG, "Failed to export Mira memory archive", error)
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to export Mira memory archive: ${error.message}",
                )
            }
        }

    override fun validateParameters(tool: AITool): ToolValidationResult =
        ToolValidationResult(valid = true)

    private suspend fun resolveProfileId(callerCardId: String?): String {
        val cardId = callerCardId?.trim().takeIf { !it.isNullOrBlank() }
        if (cardId != null) {
            val card = CharacterCardManager.getInstance(context).getCharacterCard(cardId)
            val fixedProfileId = card.memoryProfileId?.trim().takeIf { !it.isNullOrBlank() }
            if (
                CharacterCardMemoryProfileBindingMode.normalize(card.memoryProfileBindingMode) ==
                    CharacterCardMemoryProfileBindingMode.FIXED_PROFILE && fixedProfileId != null
            ) {
                return fixedProfileId
            }
        }
        return preferencesManager.activeProfileIdFlow.first()
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
                    "status=ignored\nreason=$reason\nNo memory archive was created. Continue the conversation without claiming an export.",
                ),
        )

    private companion object {
        const val TAG = "CompanionMemoryExport"
    }
}
