package com.ai.assistance.operit.ui.features.packages.screens

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.AppLogger
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val OPERIT_EDITOR_PACKAGE_NAME = "operit_editor"
private val SANDBOX_PACKAGE_DEV_INSTALL_SCRIPT_URLS =
    listOf(
        "https://cdn.jsdelivr.net/gh/kernelx30/Mira@main/tools/sandboxpackage_dev_install_or_update.js",
        "https://cdn.jsdelivr.net/gh/AAswordman/Operit@main/tools/sandboxpackage_dev_install_or_update.js"
    )
private const val SANDBOX_PACKAGE_DEV_SCRIPT_RELATIVE_PATH =
    "Download/Operit/skills/SandboxPackage_DEV/scripts/install_or_update.js"

internal fun runQuickPluginCreatorSetup(
    context: Context,
    packageManager: PackageManager,
    toolHandler: AIToolHandler
): ToolResult {
    return try {
        val scriptFile = downloadSandboxPackageDevInstallScript()
        val enableMessage = packageManager.enablePackage(OPERIT_EDITOR_PACKAGE_NAME)
        if (enableMessage.startsWith("Package not found", ignoreCase = true)) {
            return ToolResult(
                toolName = "$OPERIT_EDITOR_PACKAGE_NAME:debug_run_sandbox_script",
                success = false,
                result = StringResultData(""),
                error = enableMessage
            )
        }

        val result =
            toolHandler.executeTool(
                AITool(
                    name = "$OPERIT_EDITOR_PACKAGE_NAME:debug_run_sandbox_script",
                    parameters =
                        listOf(
                            ToolParameter(
                                name = "source_path",
                                value = scriptFile.absolutePath
                            )
                        )
                )
            )

        if (!result.success) {
            ToolResult(
                toolName = result.toolName,
                success = false,
                result = StringResultData(""),
                error = result.error ?: context.getString(R.string.quick_plugin_creator_setup_failed)
            )
        } else {
            ToolResult(
                toolName = result.toolName,
                success = true,
                result = StringResultData(context.getString(R.string.quick_plugin_creator_setup_success))
            )
        }
    } catch (e: Exception) {
        AppLogger.e("QuickPluginCreatorSetup", "Failed to run quick plugin creator setup", e)
        ToolResult(
            toolName = "$OPERIT_EDITOR_PACKAGE_NAME:debug_run_sandbox_script",
            success = false,
            result = StringResultData(""),
            error = e.message ?: e.javaClass.simpleName
        )
    }
}

private fun downloadSandboxPackageDevInstallScript(): File {
    val rootDir = Environment.getExternalStorageDirectory()
    val scriptFile = File(rootDir, SANDBOX_PACKAGE_DEV_SCRIPT_RELATIVE_PATH)
    scriptFile.parentFile?.mkdirs()

    var lastError: Throwable? = null
    SANDBOX_PACKAGE_DEV_INSTALL_SCRIPT_URLS.forEach { sourceUrl ->
        runCatching { downloadScriptFrom(sourceUrl, scriptFile) }
            .onSuccess { return scriptFile }
            .onFailure { error -> lastError = error }
    }
    throw IllegalStateException(
        "Failed to download Mira plugin development support",
        lastError
    )
}

private fun downloadScriptFrom(sourceUrl: String, scriptFile: File) {
    val connection =
        (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            doInput = true
            setRequestProperty("User-Agent", "Mira-QuickPluginCreator/1.0")
        }
    try {
        connection.connect()
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("HTTP ${connection.responseCode} from $sourceUrl")
        }

        BufferedInputStream(connection.inputStream).use { input ->
            FileOutputStream(scriptFile).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
    } finally {
        connection.disconnect()
    }
}
