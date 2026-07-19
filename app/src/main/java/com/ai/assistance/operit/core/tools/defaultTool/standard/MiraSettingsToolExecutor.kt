package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager
import com.ai.assistance.operit.core.settings.MiraSettingsCatalog
import com.ai.assistance.operit.core.settings.MiraSettingsRegistry
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.util.AppLogger
import java.util.Locale
import kotlinx.coroutines.runBlocking

class MiraSettingsToolExecutor(context: Context) : ToolExecutor {
    private val registry = MiraSettingsRegistry(context.applicationContext)

    override fun invoke(tool: AITool): ToolResult =
        runBlocking {
            try {
                val runtime = ToolExecutionManager.currentToolRuntimeContext()
                when (parameter(tool, "action").uppercase(Locale.ROOT)) {
                    ACTION_SEARCH -> search(tool)
                    ACTION_GET -> get(tool, runtime?.callerChatId.orEmpty(), runtime?.callerCardId.orEmpty())
                    ACTION_SET -> set(tool, runtime?.callerChatId.orEmpty(), runtime?.callerCardId.orEmpty())
                    else -> error(tool, "action must be SEARCH, GET, or SET")
                }
            } catch (error: Exception) {
                AppLogger.e(TAG, "Mira setting operation failed", error)
                error(tool, error.message ?: "Mira setting operation failed")
            }
        }

    private fun search(tool: AITool): ToolResult {
        val query = parameter(tool, "query")
        val matches = MiraSettingsCatalog.search(query)
        val body =
            if (matches.isEmpty()) {
                "No Mira settings matched: $query"
            } else {
                matches.joinToString("\n") { definition ->
                    "${definition.id} | ${definition.label} | ${definition.valueHint} | " +
                        "scope=${definition.scope.name} | ${definition.description}"
                }
            }
        return success(tool, "status=found\ncount=${matches.size}\n$body")
    }

    private suspend fun get(tool: AITool, callerChatId: String, callerCardId: String): ToolResult {
        val settingId = requiredParameter(tool, "setting_id")
        val snapshot = registry.read(settingId, callerChatId, callerCardId)
        return success(
            tool,
            "status=current\nsetting_id=${snapshot.definition.id}\n" +
                "label=${snapshot.definition.label}\nvalue=${snapshot.value}\n" +
                "scope=${snapshot.definition.scope.name}\nvalue_hint=${snapshot.definition.valueHint}",
        )
    }

    private suspend fun set(tool: AITool, callerChatId: String, callerCardId: String): ToolResult {
        val settingId = requiredParameter(tool, "setting_id")
        val value = requiredParameter(tool, "value")
        val change = registry.write(settingId, value, callerChatId, callerCardId)
        return success(
            tool,
            "status=updated\nsetting_id=${change.definition.id}\n" +
                "label=${change.definition.label}\nprevious_value=${change.previousValue}\n" +
                "current_value=${change.currentValue}\nchanged=${change.changed}\n" +
                "scope=${change.definition.scope.name}\neffective=immediate",
        )
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        val action = parameter(tool, "action").uppercase(Locale.ROOT)
        if (action !in setOf(ACTION_SEARCH, ACTION_GET, ACTION_SET)) {
            return ToolValidationResult(false, "action must be SEARCH, GET, or SET")
        }
        if (action in setOf(ACTION_GET, ACTION_SET) && parameter(tool, "setting_id").isBlank()) {
            return ToolValidationResult(false, "setting_id is required for $action")
        }
        if (action == ACTION_SET && parameter(tool, "value").isBlank()) {
            return ToolValidationResult(false, "value is required for SET")
        }
        return ToolValidationResult(true)
    }

    private fun parameter(tool: AITool, name: String): String =
        tool.parameters.firstOrNull { it.name == name }?.value?.trim().orEmpty()

    private fun requiredParameter(tool: AITool, name: String): String =
        parameter(tool, name).takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("$name is required")

    private fun success(tool: AITool, value: String): ToolResult =
        ToolResult(toolName = tool.name, success = true, result = StringResultData(value))

    private fun error(tool: AITool, message: String): ToolResult =
        ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = message)

    private companion object {
        const val TAG = "MiraSettingsTool"
        const val ACTION_SEARCH = "SEARCH"
        const val ACTION_GET = "GET"
        const val ACTION_SET = "SET"
    }
}
