package com.ai.assistance.operit.ui.features.settings.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.companion.CompanionReminderScheduler
import com.ai.assistance.operit.data.model.ImportStrategy
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.backup.OperitBackupDirs
import com.ai.assistance.operit.data.backup.OperitChatImportSource
import com.ai.assistance.operit.data.backup.MiraMemoryArchiveManager
import com.ai.assistance.operit.data.backup.RawSnapshotBackupManager
import com.ai.assistance.operit.data.backup.RoomDatabaseBackupManager
import com.ai.assistance.operit.data.backup.RoomDatabaseBackupPreferences
import com.ai.assistance.operit.data.backup.RoomDatabaseBackupScheduler
import com.ai.assistance.operit.data.backup.RoomDatabaseRestoreManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.repository.ChatImportResult
import com.ai.assistance.operit.data.converter.ExportFormat
import com.ai.assistance.operit.data.converter.ChatFormat
import com.ai.assistance.operit.ui.features.settings.components.BackupFilesStatisticsCard
import com.ai.assistance.operit.ui.features.settings.components.CharacterCardManagementCard
import com.ai.assistance.operit.ui.features.settings.components.ChatHistoryOperation
import com.ai.assistance.operit.ui.features.settings.components.DataManagementCard
import com.ai.assistance.operit.ui.features.settings.components.DeleteConfirmationDialog
import com.ai.assistance.operit.ui.features.settings.components.ExportFormatDialog
import com.ai.assistance.operit.ui.features.settings.components.FaqCard
import com.ai.assistance.operit.ui.features.settings.components.ImportFormatDialog
import com.ai.assistance.operit.ui.features.settings.components.MemoryImportStrategyDialog
import com.ai.assistance.operit.ui.features.settings.components.MemoryManagementCard
import com.ai.assistance.operit.ui.features.settings.components.MemoryOperation
import com.ai.assistance.operit.ui.features.settings.components.ModelConfigExportWarningDialog
import com.ai.assistance.operit.ui.features.settings.components.ModelConfigManagementCard
import com.ai.assistance.operit.ui.features.settings.components.ModelConfigOperation
import com.ai.assistance.operit.ui.features.settings.components.ManagementButton
import com.ai.assistance.operit.ui.features.settings.components.OperationProgressView
import com.ai.assistance.operit.ui.features.settings.components.OperationResultCard
import com.ai.assistance.operit.ui.features.settings.components.OverviewCard
import com.ai.assistance.operit.ui.features.settings.components.ProfileSelectionDialog
import com.ai.assistance.operit.ui.features.settings.components.RoomDbBackupListItem
import com.ai.assistance.operit.ui.features.settings.components.SectionHeader
import com.ai.assistance.operit.ui.features.settings.components.CharacterCardOperation
import com.ai.assistance.operit.util.AppProcessRestarter
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RoomDatabaseBackupOperation {
    IDLE,
    BACKING_UP,
    SUCCESS,
    FAILED
}

enum class RoomDatabaseRestoreOperation {
    IDLE,
    RESTORING,
    SUCCESS,
    FAILED
}

enum class RawSnapshotOperation {
    IDLE,
    BACKING_UP,
    BACKUP_SUCCESS,
    RESTORING,
    RESTORE_SUCCESS,
    FAILED
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatBackupSettingsScreen() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    val chatHistoryManager = remember { ChatHistoryManager.getInstance(context) }
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val modelConfigManager = remember { ModelConfigManager(context) }
    val activeProfileId by userPreferencesManager.activeProfileIdFlow.collectAsState(initial = "default")
    var totalChatCount by remember { mutableStateOf(0) }
    var totalCharacterCardCount by remember { mutableStateOf(0) }
    var totalMemoryCount by remember { mutableStateOf(0) }
    var totalMemoryLinkCount by remember { mutableStateOf(0) }
    var totalModelConfigCount by remember { mutableStateOf(0) }
    var operationState by remember { mutableStateOf(ChatHistoryOperation.IDLE) }
    var operationMessage by remember { mutableStateOf("") }
    var characterCardOperationState by remember { mutableStateOf(CharacterCardOperation.IDLE) }
    var characterCardOperationMessage by remember { mutableStateOf("") }
    var memoryOperationState by remember { mutableStateOf(MemoryOperation.IDLE) }
    var memoryOperationMessage by remember { mutableStateOf("") }
    var modelConfigOperationState by remember { mutableStateOf(ModelConfigOperation.IDLE) }
    var modelConfigOperationMessage by remember { mutableStateOf("") }
    var roomDbBackupOperationState by remember { mutableStateOf(RoomDatabaseBackupOperation.IDLE) }
    var roomDbBackupOperationMessage by remember { mutableStateOf("") }
    var roomDbRestoreOperationState by remember { mutableStateOf(RoomDatabaseRestoreOperation.IDLE) }
    var roomDbRestoreOperationMessage by remember { mutableStateOf("") }
    var rawSnapshotOperationState by remember { mutableStateOf(RawSnapshotOperation.IDLE) }
    var rawSnapshotOperationMessage by remember { mutableStateOf("") }
    var pendingRawSnapshotRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRawSnapshotRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showRawSnapshotRestoreRestartDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showMemoryImportStrategyDialog by remember { mutableStateOf(false) }
    var pendingMemoryImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRoomDbRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRoomDbRestoreFile by remember { mutableStateOf<File?>(null) }
    var showRoomDbRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showRoomDbRestoreRestartDialog by remember { mutableStateOf(false) }

    // 模型配置导出安全警告
    var showModelConfigExportWarning by remember { mutableStateOf(false) }
    var exportedModelConfigPath by remember { mutableStateOf("") }

    // Operit 目录备份文件统计
    var chatBackupFileCount by remember { mutableStateOf(0) }
    var characterCardBackupFileCount by remember { mutableStateOf(0) }
    var memoryBackupFileCount by remember { mutableStateOf(0) }
    var modelConfigBackupFileCount by remember { mutableStateOf(0) }
    var roomDbBackupFileCount by remember { mutableStateOf(0) }
    var recentRoomDbBackups by remember { mutableStateOf<List<File>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    val roomDbBackupPreferences = remember { RoomDatabaseBackupPreferences.getInstance(context) }
    val isRoomDbDailyBackupEnabled by roomDbBackupPreferences.enableDailyBackupFlow.collectAsState(initial = true)
    val roomDbLastSuccessTime by roomDbBackupPreferences.lastSuccessTimeFlow.collectAsState(initial = 0L)
    val roomDbLastError by roomDbBackupPreferences.lastErrorFlow.collectAsState(initial = "")
    val roomDbMaxBackupCount by roomDbBackupPreferences.maxBackupCountFlow.collectAsState(
        initial = RoomDatabaseBackupPreferences.DEFAULT_MAX_BACKUP_COUNT
    )

    val profileIds by userPreferencesManager.profileListFlow.collectAsState(initial = listOf("default"))
    var allProfiles by remember { mutableStateOf<List<PreferenceProfile>>(emptyList()) }
    var selectedExportProfileId by remember { mutableStateOf(activeProfileId) }
    var selectedImportProfileId by remember { mutableStateOf(activeProfileId) }
    var showExportProfileDialog by remember { mutableStateOf(false) }
    var showImportProfileDialog by remember { mutableStateOf(false) }

    // 导出格式选择
    var showExportFormatDialog by remember { mutableStateOf(false) }
    var selectedExportFormat by remember { mutableStateOf(ExportFormat.JSON) }

    // 导入格式选择
    var showImportFormatDialog by remember { mutableStateOf(false) }
    var selectedImportFormat by remember { mutableStateOf(ChatFormat.OPERIT) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showOperitChatBackupNotFoundDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeProfileId) {
        selectedExportProfileId = activeProfileId
        selectedImportProfileId = activeProfileId
        try {
            val stats = MiraMemoryArchiveManager.getStats(context, activeProfileId)
            totalMemoryCount = stats.totalMemories
            totalMemoryLinkCount = stats.totalLinks
        } catch (error: Exception) {
            AppLogger.e("ChatBackupSettings", "读取记忆备份统计失败", error)
            memoryOperationState = MemoryOperation.FAILED
            memoryOperationMessage =
                context.getString(
                    R.string.backup_memory_stats_failed,
                    error.localizedMessage ?: error.toString(),
                )
        }
    }

    LaunchedEffect(profileIds) {
        val profiles = profileIds.mapNotNull { profileId ->
            try {
                userPreferencesManager.getUserPreferencesFlow(profileId).first()
            } catch (_: Exception) {
                null
            }
        }
        allProfiles = profiles
    }

    LaunchedEffect(Unit) {
        chatHistoryManager.chatHistoriesFlow.collect { chatHistories ->
            totalChatCount = chatHistories.size
        }
    }

    LaunchedEffect(Unit) {
        characterCardManager.characterCardListFlow.collect { cardIds ->
            totalCharacterCardCount = cardIds.size
        }
    }

    LaunchedEffect(Unit) {
        modelConfigManager.configListFlow.collect { configList ->
            totalModelConfigCount = configList.size
        }
    }

    fun startOperitChatImport(importer: suspend () -> ChatImportResult) {
        scope.launch {
            operationState = ChatHistoryOperation.IMPORTING
            operationMessage = ""
            try {
                val importResult = importer()
                if (importResult.total > 0 || importResult.skipped > 0) {
                    operationState = ChatHistoryOperation.IMPORTED
                    val skippedText = if (importResult.skipped > 0) {
                        resources.getString(R.string.backup_import_result_skipped, importResult.skipped)
                    } else {
                        ""
                    }
                    operationMessage = resources.getString(
                        R.string.backup_import_result_success,
                        resources.getString(R.string.backup_format_operit),
                        importResult.new,
                        importResult.updated,
                        skippedText
                    )
                } else {
                    operationState = ChatHistoryOperation.FAILED
                    operationMessage = resources.getString(R.string.backup_import_result_failed)
                }
            } catch (e: Exception) {
                operationState = ChatHistoryOperation.FAILED
                operationMessage = resources.getString(
                    R.string.backup_import_failed_with_reason,
                    e.localizedMessage ?: e.toString()
                )
            }
        }
    }

    fun importLatestOperitChatBackupOrChooseFile() {
        scope.launch {
            val latestBackup = withContext(Dispatchers.IO) {
                OperitChatImportSource.findLatestBackup(OperitBackupDirs.operitRootDir())
            }
            if (latestBackup != null) {
                startOperitChatImport {
                    chatHistoryManager.importOperitChatHistoriesFromFile(latestBackup)
                }
            } else {
                showOperitChatBackupNotFoundDialog = true
            }
        }
    }

    // 扫描 Operit 目录中的备份文件
    LaunchedEffect(Unit) {
        scope.launch {
            isScanning = true
            try {
                val legacyDir = OperitBackupDirs.operitRootDir()
                val legacyFiles = legacyDir.listFiles()?.toList() ?: emptyList()

                fun mergedFiles(newDir: File): List<File> {
                    val newFiles = newDir.listFiles()?.toList() ?: emptyList()
                    return (newFiles + legacyFiles)
                        .filter { it.isFile }
                        .distinctBy { it.name }
                }

                val chatFiles = mergedFiles(OperitBackupDirs.chatDir())
                val characterCardFiles = mergedFiles(OperitBackupDirs.characterCardsDir())
                val memoryFiles = mergedFiles(OperitBackupDirs.memoryDir())
                val modelConfigFiles = mergedFiles(OperitBackupDirs.modelConfigDir())
                val roomDbFiles = mergedFiles(OperitBackupDirs.roomDbDir())

                chatBackupFileCount = chatFiles.count { file ->
                    file.name.startsWith("chat_backup_") && file.extension == "json" ||
                        file.name.startsWith("chat_export_") && file.extension in listOf("json", "md", "html", "txt")
                }

                characterCardBackupFileCount = characterCardFiles.count { file ->
                    file.name.startsWith("character_cards_backup_") && file.extension == "json"
                }

                memoryBackupFileCount = memoryFiles.count { file ->
                    file.name.startsWith("memory_backup_") && file.extension == "json"
                }

                modelConfigBackupFileCount = modelConfigFiles.count { file ->
                    file.name.startsWith("model_config_backup_") && file.extension == "json"
                }

                roomDbBackupFileCount = roomDbFiles.count { file ->
                    RoomDatabaseRestoreManager.isRoomDatabaseBackupFile(file.name)
                }

                recentRoomDbBackups = RoomDatabaseRestoreManager.listRecentBackups(context, limit = 3)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isScanning = false
            }
        }
    }

    val characterCardFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                scope.launch {
                    characterCardOperationState = CharacterCardOperation.IMPORTING
                    characterCardOperationMessage = ""
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val jsonContent = inputStream?.bufferedReader()?.use { it.readText() }
                        if (jsonContent != null) {
                            val importResult = characterCardManager.importAllCharacterCardsFromBackupContent(jsonContent)
                            if (importResult.total > 0) {
                                characterCardOperationState = CharacterCardOperation.IMPORTED
                                val skippedText = if (importResult.skipped > 0) {
                                    context.getString(
                                        R.string.backup_character_cards_import_result_skipped,
                                        importResult.skipped
                                    )
                                } else {
                                    ""
                                }
                                characterCardOperationMessage = context.getString(
                                    R.string.backup_character_cards_import_result_success,
                                    importResult.new,
                                    importResult.updated,
                                    skippedText
                                )
                            } else {
                                characterCardOperationState = CharacterCardOperation.FAILED
                                characterCardOperationMessage =
                                    context.getString(R.string.backup_character_cards_import_result_failed)
                            }
                        } else {
                            characterCardOperationState = CharacterCardOperation.FAILED
                            characterCardOperationMessage =
                                context.getString(R.string.backup_import_failed_unreadable_file)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        characterCardOperationState = CharacterCardOperation.FAILED
                        characterCardOperationMessage = context.getString(
                            R.string.backup_import_failed_with_reason,
                            e.localizedMessage ?: e.toString()
                        )
                    }
                }
            }
        }
    }

    val chatFilePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    // 保存URI，显示格式选择对话框
                    pendingImportUri = uri
                    showImportFormatDialog = true
                }
            }
        }

    val operitChatFilePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    startOperitChatImport {
                        chatHistoryManager.importChatHistoriesFromUri(uri, ChatFormat.OPERIT)
                    }
                }
            }
        }

    val roomDbRestoreFilePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    pendingRoomDbRestoreUri = uri
                    pendingRoomDbRestoreFile = null
                    showRoomDbRestoreConfirmDialog = true
                }
            }
        }

    val rawSnapshotRestoreFilePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    pendingRawSnapshotRestoreUri = uri
                    showRawSnapshotRestoreConfirmDialog = true
                }
            }
        }

    val memoryFilePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    pendingMemoryImportUri = uri
                    showImportProfileDialog = true
                }
            }
        }

    val modelConfigFilePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    scope.launch {
                        modelConfigOperationState = ModelConfigOperation.IMPORTING
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val jsonContent = inputStream?.bufferedReader()?.use { it.readText() }
                            if (jsonContent != null) {
                                val (newCount, updatedCount, skippedCount) =
                                    modelConfigManager.importConfigs(jsonContent)
                                modelConfigOperationState = ModelConfigOperation.IMPORTED
                                val skippedText = if (skippedCount > 0) {
                                    context.getString(
                                        R.string.backup_model_config_import_result_skipped,
                                        skippedCount
                                    )
                                } else {
                                    ""
                                }
                                modelConfigOperationMessage = context.getString(
                                    R.string.backup_model_config_import_result_success,
                                    newCount,
                                    updatedCount,
                                    skippedText
                                )
                            } else {
                                modelConfigOperationState = ModelConfigOperation.FAILED
                                modelConfigOperationMessage =
                                    context.getString(R.string.backup_import_failed_unreadable_file)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            modelConfigOperationState = ModelConfigOperation.FAILED
                            modelConfigOperationMessage = context.getString(
                                R.string.backup_import_failed_with_reason,
                                e.localizedMessage ?: e.toString()
                            )
                        }
                    }
                }
            }
        }

    val activeProfileName =
        allProfiles.find { it.id == activeProfileId }?.name
            ?: stringResource(R.string.default_profile_name)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OverviewCard(
                totalChatCount = totalChatCount,
                totalCharacterCardCount = totalCharacterCardCount,
                totalMemoryCount = totalMemoryCount,
                totalLinkCount = totalMemoryLinkCount,
                activeProfileName = activeProfileName
            )
        }
        item {
            BackupFilesStatisticsCard(
                chatBackupCount = chatBackupFileCount,
                characterCardBackupCount = characterCardBackupFileCount,
                memoryBackupCount = memoryBackupFileCount,
                modelConfigBackupCount = modelConfigBackupFileCount,
                roomDbBackupCount = roomDbBackupFileCount,
                isScanning = isScanning,
                onRefresh = {
                    scope.launch {
                        isScanning = true
                        try {
                            val legacyDir = OperitBackupDirs.operitRootDir()
                            val legacyFiles = legacyDir.listFiles()?.toList() ?: emptyList()

                            fun mergedFiles(newDir: File): List<File> {
                                val newFiles = newDir.listFiles()?.toList() ?: emptyList()
                                return (newFiles + legacyFiles)
                                    .filter { it.isFile }
                                    .distinctBy { it.name }
                            }

                            val chatFiles = mergedFiles(OperitBackupDirs.chatDir())
                            val characterCardFiles = mergedFiles(OperitBackupDirs.characterCardsDir())
                            val memoryFiles = mergedFiles(OperitBackupDirs.memoryDir())
                            val modelConfigFiles = mergedFiles(OperitBackupDirs.modelConfigDir())
                            val roomDbFiles = mergedFiles(OperitBackupDirs.roomDbDir())

                            chatBackupFileCount = chatFiles.count { file ->
                                file.name.startsWith("chat_backup_") && file.extension == "json" ||
                                    file.name.startsWith("chat_export_") && file.extension in listOf("json", "md", "html", "txt")
                            }

                            characterCardBackupFileCount = characterCardFiles.count { file ->
                                file.name.startsWith("character_cards_backup_") && file.extension == "json"
                            }

                            memoryBackupFileCount = memoryFiles.count { file ->
                                file.name.startsWith("memory_backup_") && file.extension == "json"
                            }

                            modelConfigBackupFileCount = modelConfigFiles.count { file ->
                                file.name.startsWith("model_config_backup_") && file.extension == "json"
                            }

                            roomDbBackupFileCount = roomDbFiles.count { file ->
                                RoomDatabaseRestoreManager.isRoomDatabaseBackupFile(file.name)
                            }

                            recentRoomDbBackups = RoomDatabaseRestoreManager.listRecentBackups(context, limit = 3)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isScanning = false
                        }
                    }
                }
            )
        }
        item {
            DataManagementCard(
                totalChatCount = totalChatCount,
                operationState = operationState,
                operationMessage = operationMessage,
                onExport = {
                    // 显示格式选择对话框
                    showExportFormatDialog = true
                },
                onImport = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"  // 接受所有类型
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                            "application/json",
                            "text/markdown",
                            "text/plain",
                            "text/csv"
                        ))
                    }
                    chatFilePickerLauncher.launch(intent)
                },
                onImportFromOperit = ::importLatestOperitChatBackupOrChooseFile,
                onDelete = { showDeleteConfirmDialog = true }
            )
        }
        item {
            CharacterCardManagementCard(
                totalCharacterCardCount = totalCharacterCardCount,
                operationState = characterCardOperationState,
                operationMessage = characterCardOperationMessage,
                onExport = {
                    scope.launch {
                        characterCardOperationState = CharacterCardOperation.EXPORTING
                        characterCardOperationMessage = ""
                        try {
                            val filePath = characterCardManager.exportAllCharacterCardsToBackupFile()
                            if (filePath != null) {
                                characterCardOperationState = CharacterCardOperation.EXPORTED
                                characterCardOperationMessage = context.getString(
                                    R.string.backup_character_cards_export_result_success,
                                    totalCharacterCardCount,
                                    filePath
                                )
                            } else {
                                characterCardOperationState = CharacterCardOperation.FAILED
                                characterCardOperationMessage =
                                    context.getString(R.string.backup_export_failed_create_file)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            characterCardOperationState = CharacterCardOperation.FAILED
                            characterCardOperationMessage = context.getString(
                                R.string.backup_export_failed_with_reason,
                                e.localizedMessage ?: e.toString()
                            )
                        }
                    }
                },
                onImport = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                    }
                    characterCardFilePickerLauncher.launch(intent)
                }
            )
        }
        item {
            MemoryManagementCard(
                totalMemoryCount = totalMemoryCount,
                totalLinkCount = totalMemoryLinkCount,
                operationState = memoryOperationState,
                operationMessage = memoryOperationMessage,
                onExport = { showExportProfileDialog = true },
                onImport = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                    }
                    memoryFilePickerLauncher.launch(intent)
                }
            )
        }
        item {
            ModelConfigManagementCard(
                totalConfigCount = totalModelConfigCount,
                operationState = modelConfigOperationState,
                operationMessage = modelConfigOperationMessage,
                onExport = {
                    scope.launch {
                        modelConfigOperationState = ModelConfigOperation.EXPORTING
                        try {
                            val jsonContent = modelConfigManager.exportAllConfigs()
                            val exportDir = OperitBackupDirs.modelConfigDir()
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                            val timestamp = dateFormat.format(Date())
                            val exportFile = File(exportDir, "model_config_backup_$timestamp.json")
                            exportFile.writeText(jsonContent)

                            // 导出成功，显示安全警告对话框
                            exportedModelConfigPath = exportFile.absolutePath
                            showModelConfigExportWarning = true
                            modelConfigOperationState = ModelConfigOperation.EXPORTED
                        } catch (e: Exception) {
                            e.printStackTrace()
                            modelConfigOperationState = ModelConfigOperation.FAILED
                            modelConfigOperationMessage = context.getString(
                                R.string.backup_export_failed_with_reason,
                                e.localizedMessage ?: e.toString()
                            )
                        }
                    }
                },
                onImport = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                    }
                    modelConfigFilePickerLauncher.launch(intent)
                }
            )
        }
        item {
            FaqCard()
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SectionHeader(
                        title = stringResource(R.string.backup_room_db_title),
                        subtitle = stringResource(R.string.backup_room_db_subtitle, roomDbMaxBackupCount),
                        icon = Icons.Default.Storage
                    )

                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.backup_room_db_low_level_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.backup_room_db_enable_daily),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.backup_room_db_enable_daily_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isRoomDbDailyBackupEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    roomDbBackupPreferences.setDailyBackupEnabled(enabled)
                                    if (enabled) {
                                        RoomDatabaseBackupScheduler.ensureScheduled(context)
                                    } else {
                                        RoomDatabaseBackupScheduler.cancelScheduled(context)
                                    }
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.backup_room_db_max_backup_count),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.backup_room_db_max_backup_count_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    val next = (roomDbMaxBackupCount - 1).coerceAtLeast(1)
                                    scope.launch {
                                        roomDbBackupPreferences.setMaxBackupCount(next)
                                        RoomDatabaseBackupManager.pruneExcessBackups(context)
                                        isScanning = true
                                        try {
                                            val legacyDir = OperitBackupDirs.operitRootDir()
                                            val legacyFiles = legacyDir.listFiles()?.toList() ?: emptyList()
                                            val newFiles = OperitBackupDirs.roomDbDir().listFiles()?.toList() ?: emptyList()
                                            val roomDbFiles = (newFiles + legacyFiles)
                                                .filter { it.isFile }
                                                .distinctBy { it.name }
                                            roomDbBackupFileCount = roomDbFiles.count { file ->
                                                RoomDatabaseRestoreManager.isRoomDatabaseBackupFile(file.name)
                                            }
                                            recentRoomDbBackups = RoomDatabaseRestoreManager.listRecentBackups(context, limit = 3)
                                        } finally {
                                            isScanning = false
                                        }
                                    }
                                },
                                enabled = roomDbMaxBackupCount > 1
                            ) { Text("-") }

                            Text(text = roomDbMaxBackupCount.toString())

                            TextButton(
                                onClick = {
                                    val next = (roomDbMaxBackupCount + 1).coerceAtMost(100)
                                    scope.launch {
                                        roomDbBackupPreferences.setMaxBackupCount(next)
                                    }
                                },
                                enabled = roomDbMaxBackupCount < 100
                            ) { Text("+") }
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ManagementButton(
                            text = stringResource(R.string.backup_room_db_backup_now),
                            icon = Icons.Default.CloudDownload,
                            onClick = {
                                scope.launch {
                                    roomDbBackupOperationState = RoomDatabaseBackupOperation.BACKING_UP
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            RoomDatabaseBackupManager.backupIfNeeded(context, force = true)
                                        }
                                        roomDbBackupOperationState = RoomDatabaseBackupOperation.SUCCESS
                                        roomDbBackupOperationMessage =
                                            result.backupFile?.absolutePath
                                                ?: context.getString(R.string.backup_operation_failed)

                                        isScanning = true
                                        try {
                                            val legacyDir = OperitBackupDirs.operitRootDir()
                                            val legacyFiles = legacyDir.listFiles()?.toList() ?: emptyList()
                                            val newFiles = OperitBackupDirs.roomDbDir().listFiles()?.toList() ?: emptyList()
                                            val roomDbFiles = (newFiles + legacyFiles)
                                                .filter { it.isFile }
                                                .distinctBy { it.name }

                                            roomDbBackupFileCount = roomDbFiles.count { file ->
                                                RoomDatabaseRestoreManager.isRoomDatabaseBackupFile(file.name)
                                            }

                                            recentRoomDbBackups = RoomDatabaseRestoreManager.listRecentBackups(context, limit = 3)
                                        } finally {
                                            isScanning = false
                                        }
                                    } catch (e: Exception) {
                                        roomDbBackupOperationState = RoomDatabaseBackupOperation.FAILED
                                        roomDbBackupOperationMessage = e.localizedMessage ?: e.toString()
                                        try {
                                            roomDbBackupPreferences.markFailure(roomDbBackupOperationMessage)
                                        } catch (_: Exception) {

                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        ManagementButton(
                            text = stringResource(R.string.backup_room_db_restore_from_file),
                            icon = Icons.Default.FileOpen,
                            onClick = {
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip"))
                                }
                                roomDbRestoreFilePickerLauncher.launch(intent)
                            },
                            modifier = Modifier.weight(1f, fill = false),
                            isWarning = true
                        )
                    }

                    AnimatedVisibility(visible = recentRoomDbBackups.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.backup_room_db_recent_auto_backups),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            recentRoomDbBackups.forEach { file ->
                                RoomDbBackupListItem(
                                    file = file,
                                    onRestoreClick = {
                                        pendingRoomDbRestoreFile = file
                                        pendingRoomDbRestoreUri = null
                                        showRoomDbRestoreConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = roomDbBackupOperationState != RoomDatabaseBackupOperation.IDLE) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            when (roomDbBackupOperationState) {
                                RoomDatabaseBackupOperation.BACKING_UP ->
                                    OperationProgressView(message = stringResource(R.string.backup_room_db_backing_up))
                                RoomDatabaseBackupOperation.SUCCESS ->
                                    OperationResultCard(
                                        title = stringResource(R.string.backup_export_success),
                                        message = roomDbBackupOperationMessage,
                                        icon = Icons.Default.CloudDownload
                                    )
                                RoomDatabaseBackupOperation.FAILED ->
                                    OperationResultCard(
                                        title = stringResource(R.string.backup_operation_failed),
                                        message = roomDbBackupOperationMessage,
                                        icon = Icons.Default.Info,
                                        isError = true
                                    )
                                else -> {}
                            }
                        }
                    }

                    AnimatedVisibility(visible = roomDbRestoreOperationState != RoomDatabaseRestoreOperation.IDLE) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            when (roomDbRestoreOperationState) {
                                RoomDatabaseRestoreOperation.RESTORING ->
                                    OperationProgressView(message = stringResource(R.string.backup_room_db_restoring))
                                RoomDatabaseRestoreOperation.SUCCESS ->
                                    OperationResultCard(
                                        title = stringResource(R.string.backup_room_db_restore_success),
                                        message = roomDbRestoreOperationMessage,
                                        icon = Icons.Default.Restore
                                    )
                                RoomDatabaseRestoreOperation.FAILED ->
                                    OperationResultCard(
                                        title = stringResource(R.string.backup_room_db_restore_failed),
                                        message = roomDbRestoreOperationMessage,
                                        icon = Icons.Default.Info,
                                        isError = true
                                    )
                                else -> {}
                            }
                        }
                    }

                    val timeText = remember(roomDbLastSuccessTime) {
                        if (roomDbLastSuccessTime <= 0L) {
                            "-"
                        } else {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(roomDbLastSuccessTime))
                        }
                    }
                    Text(
                        text = stringResource(R.string.backup_room_db_last_success, timeText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (roomDbLastError.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.backup_room_db_last_error, roomDbLastError),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SectionHeader(
                        title = stringResource(R.string.backup_raw_snapshot_title),
                        subtitle = stringResource(R.string.backup_raw_snapshot_subtitle),
                        icon = Icons.Default.Storage
                    )

                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.backup_raw_snapshot_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ManagementButton(
                            text = stringResource(R.string.backup_raw_snapshot_backup_now),
                            icon = Icons.Default.CloudDownload,
                            onClick = {
                                scope.launch {
                                    rawSnapshotOperationState = RawSnapshotOperation.BACKING_UP
                                    rawSnapshotOperationMessage = context.getString(R.string.backup_raw_snapshot_progress_preparing)
                                    try {
                                        val outFile = RawSnapshotBackupManager.exportToBackupDir(
                                            context = context,
                                            onProgress = { progress ->
                                                val suffix = progress.percent?.let { " ${it}%" } ?: ""
                                                rawSnapshotOperationMessage = when (progress.stage) {
                                                    RawSnapshotBackupManager.ExportProgress.PREPARING ->
                                                        context.getString(R.string.backup_raw_snapshot_progress_preparing)

                                                    RawSnapshotBackupManager.ExportProgress.SCANNING_FILES ->
                                                        progress.scannedFiles?.let { scanned ->
                                                            context.getString(
                                                                R.string.backup_raw_snapshot_progress_scanning_files_with_count,
                                                                scanned
                                                            )
                                                        } ?: context.getString(R.string.backup_raw_snapshot_progress_scanning_files)

                                                    RawSnapshotBackupManager.ExportProgress.ZIPPING_FILES ->
                                                        context.getString(R.string.backup_raw_snapshot_progress_zipping_files) + suffix

                                                    RawSnapshotBackupManager.ExportProgress.ZIPPING_EXTERNAL_FILES ->
                                                        context.getString(R.string.backup_raw_snapshot_progress_zipping_external_files) + suffix

                                                    RawSnapshotBackupManager.ExportProgress.ZIPPING_SHARED_PREFS ->
                                                        context.getString(R.string.backup_raw_snapshot_progress_zipping_shared_prefs)

                                                    RawSnapshotBackupManager.ExportProgress.ZIPPING_DATASTORE ->
                                                        context.getString(R.string.backup_raw_snapshot_progress_zipping_datastore)

                                                    RawSnapshotBackupManager.ExportProgress.ZIPPING_DATABASES ->
                                                        context.getString(R.string.backup_raw_snapshot_progress_zipping_databases)

                                                    RawSnapshotBackupManager.ExportProgress.FINALIZING ->
                                                        context.getString(R.string.backup_raw_snapshot_progress_finalizing)
                                                }
                                            }
                                        )
                                        rawSnapshotOperationState = RawSnapshotOperation.BACKUP_SUCCESS
                                        rawSnapshotOperationMessage = outFile.absolutePath
                                    } catch (e: Exception) {
                                        rawSnapshotOperationState = RawSnapshotOperation.FAILED
                                        rawSnapshotOperationMessage = e.localizedMessage ?: e.toString()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        ManagementButton(
                            text = stringResource(R.string.backup_raw_snapshot_restore_from_file),
                            icon = Icons.Default.FileOpen,
                            onClick = {
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip"))
                                }
                                rawSnapshotRestoreFilePickerLauncher.launch(intent)
                            },
                            modifier = Modifier.weight(1f, fill = false),
                            isWarning = true
                        )
                    }

                    AnimatedVisibility(visible = rawSnapshotOperationState != RawSnapshotOperation.IDLE) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            when (rawSnapshotOperationState) {
                                RawSnapshotOperation.BACKING_UP ->
                                    OperationProgressView(
                                        message = rawSnapshotOperationMessage.ifBlank {
                                            stringResource(R.string.backup_raw_snapshot_backing_up)
                                        }
                                    )
                                RawSnapshotOperation.RESTORING ->
                                    OperationProgressView(
                                        message = rawSnapshotOperationMessage.ifBlank {
                                            stringResource(R.string.backup_raw_snapshot_restoring)
                                        }
                                    )
                                RawSnapshotOperation.BACKUP_SUCCESS ->
                                    OperationResultCard(
                                        title = stringResource(R.string.backup_export_success),
                                        message = rawSnapshotOperationMessage,
                                        icon = Icons.Default.CloudDownload
                                    )
                                RawSnapshotOperation.RESTORE_SUCCESS ->
                                    OperationResultCard(
                                        title = stringResource(R.string.backup_import_success),
                                        message = rawSnapshotOperationMessage,
                                        icon = Icons.Default.Restore
                                    )
                                RawSnapshotOperation.FAILED ->
                                    OperationResultCard(
                                        title = stringResource(R.string.backup_operation_failed),
                                        message = rawSnapshotOperationMessage,
                                        icon = Icons.Default.Info,
                                        isError = true
                                    )
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        DeleteConfirmationDialog(
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = {
                showDeleteConfirmDialog = false
                scope.launch {
                    operationState = ChatHistoryOperation.DELETING
                    try {
                        val result = deleteAllChatHistories(context)
                        operationState = ChatHistoryOperation.DELETED
                        val skippedText = if (result.skippedLockedCount > 0) {
                            context.getString(
                                R.string.backup_delete_skipped_locked,
                                result.skippedLockedCount
                            )
                        } else {
                            ""
                        }
                        operationMessage =
                            context.getString(
                                R.string.backup_delete_result_success,
                                result.deletedCount
                            ) + skippedText
                    } catch (e: Exception) {
                        operationState = ChatHistoryOperation.FAILED
                        operationMessage = context.getString(
                            R.string.backup_clear_failed_with_reason,
                            e.localizedMessage ?: e.toString()
                        )
                    }
                }
            }
        )
    }

    if (showMemoryImportStrategyDialog) {
        MemoryImportStrategyDialog(
            onDismiss = {
                showMemoryImportStrategyDialog = false
                pendingMemoryImportUri = null
            },
            onConfirm = { strategy ->
                showMemoryImportStrategyDialog = false
                val uri = pendingMemoryImportUri
                pendingMemoryImportUri = null

                if (uri != null) {
                    scope.launch {
                        memoryOperationState = MemoryOperation.IMPORTING
                        try {
                            val result =
                                importMemoriesFromUri(
                                    context = context,
                                    targetProfileId = selectedImportProfileId,
                                    uri = uri,
                                    strategy = strategy,
                                )
                            memoryOperationState = MemoryOperation.IMPORTED
                            val profileName = allProfiles.find { it.id == selectedImportProfileId }?.name
                                ?: selectedImportProfileId
                            memoryOperationMessage = context.getString(
                                R.string.backup_memory_import_result_success,
                                profileName,
                                result.newMemories,
                                result.updatedMemories,
                                result.skippedMemories,
                                result.newLinks,
                                result.importedEvidence,
                                result.importedEpisodes,
                                result.importedGrants,
                            )

                            if (selectedImportProfileId == activeProfileId) {
                                val stats =
                                    MiraMemoryArchiveManager.getStats(context, activeProfileId)
                                totalMemoryCount = stats.totalMemories
                                totalMemoryLinkCount = stats.totalLinks
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            memoryOperationState = MemoryOperation.FAILED
                            memoryOperationMessage = context.getString(
                                R.string.backup_import_failed_with_reason,
                                e.localizedMessage ?: e.toString()
                            )
                        }
                    }
                }
            }
        )
    }

    if (showExportProfileDialog) {
        ProfileSelectionDialog(
            title = stringResource(R.string.select_export_profile),
            profiles = allProfiles,
            selectedProfileId = selectedExportProfileId,
            onProfileSelected = { selectedExportProfileId = it },
            onDismiss = { showExportProfileDialog = false },
            onConfirm = {
                showExportProfileDialog = false
                scope.launch {
                    memoryOperationState = MemoryOperation.EXPORTING
                    try {
                        val result =
                            MiraMemoryArchiveManager.exportToBackupDir(
                                context = context,
                                profileId = selectedExportProfileId,
                            )
                        memoryOperationState = MemoryOperation.EXPORTED
                        val profileName = allProfiles.find { it.id == selectedExportProfileId }?.name
                            ?: selectedExportProfileId
                        memoryOperationMessage = context.getString(
                            R.string.backup_memory_export_result_success,
                            profileName,
                            result.stats.totalMemories,
                            result.stats.totalLinks,
                            result.stats.totalEvidence,
                            result.stats.episodes,
                            result.stats.grants,
                            result.file.absolutePath,
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        memoryOperationState = MemoryOperation.FAILED
                        memoryOperationMessage = context.getString(
                            R.string.backup_export_failed_with_reason,
                            e.localizedMessage ?: e.toString()
                        )
                    }
                }
            }
        )
    }

    if (showImportProfileDialog) {
        ProfileSelectionDialog(
            title = stringResource(R.string.select_import_profile),
            profiles = allProfiles,
            selectedProfileId = selectedImportProfileId,
            onProfileSelected = { selectedImportProfileId = it },
            onDismiss = {
                showImportProfileDialog = false
                pendingMemoryImportUri = null
            },
            onConfirm = {
                showImportProfileDialog = false
                showMemoryImportStrategyDialog = true
            }
        )
    }

    if (showExportFormatDialog) {
        ExportFormatDialog(
            selectedFormat = selectedExportFormat,
            onFormatSelected = { selectedExportFormat = it },
            onDismiss = { showExportFormatDialog = false },
            onConfirm = {
                showExportFormatDialog = false
                scope.launch {
                    operationState = ChatHistoryOperation.EXPORTING
                    try {
                        val filePath = chatHistoryManager.exportChatHistoriesToDownloads(selectedExportFormat)
                        if (filePath != null) {
                            operationState = ChatHistoryOperation.EXPORTED
                            val chatCount = chatHistoryManager.chatHistoriesFlow.first().size
                            val formatName = when (selectedExportFormat) {
                                ExportFormat.JSON -> context.getString(R.string.backup_format_json)
                                ExportFormat.MARKDOWN -> context.getString(R.string.backup_format_markdown)
                                ExportFormat.HTML -> context.getString(R.string.backup_format_html)
                                ExportFormat.TXT -> context.getString(R.string.backup_format_txt)
                                ExportFormat.CSV -> "CSV"
                            }
                            operationMessage = context.getString(
                                R.string.backup_chat_export_result_success,
                                chatCount,
                                formatName,
                                filePath
                            )
                        } else {
                            operationState = ChatHistoryOperation.FAILED
                            operationMessage =
                                context.getString(R.string.backup_export_failed_create_file)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        operationState = ChatHistoryOperation.FAILED
                        operationMessage = context.getString(
                            R.string.backup_export_failed_with_reason,
                            e.localizedMessage ?: e.toString()
                        )
                    }
                }
            }
        )
    }

    if (showOperitChatBackupNotFoundDialog) {
        AlertDialog(
            onDismissRequest = { showOperitChatBackupNotFoundDialog = false },
            title = { Text(stringResource(R.string.backup_import_from_operit_no_backup_title)) },
            text = { Text(stringResource(R.string.backup_import_from_operit_no_backup_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOperitChatBackupNotFoundDialog = false
                        operitChatFilePickerLauncher.launch(
                            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/json"
                                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/json", "text/plain"))
                            }
                        )
                    }
                ) {
                    Text(stringResource(R.string.backup_import_from_operit_choose_file))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOperitChatBackupNotFoundDialog = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }

    if (showImportFormatDialog) {
        ImportFormatDialog(
            selectedFormat = selectedImportFormat,
            onFormatSelected = { selectedImportFormat = it },
            onDismiss = {
                showImportFormatDialog = false
                pendingImportUri = null
            },
            onConfirm = {
                showImportFormatDialog = false
                pendingImportUri?.let { uri ->
                    scope.launch {
                        operationState = ChatHistoryOperation.IMPORTING
                        try {
                            val importResult = chatHistoryManager.importChatHistoriesFromUri(uri, selectedImportFormat)
                            operationMessage = if (importResult.total > 0) {
                                operationState = ChatHistoryOperation.IMPORTED
                                val formatName = when (selectedImportFormat) {
                                    ChatFormat.OPERIT -> context.getString(R.string.backup_format_operit)
                                    ChatFormat.CHATGPT -> context.getString(R.string.backup_format_chatgpt)
                                    ChatFormat.CHATBOX -> context.getString(R.string.backup_format_chatbox)
                                    ChatFormat.MARKDOWN -> context.getString(R.string.backup_format_markdown)
                                    ChatFormat.GENERIC_JSON -> context.getString(R.string.backup_format_generic_json)
                                    ChatFormat.CLAUDE -> context.getString(R.string.backup_format_claude)
                                    else -> context.getString(R.string.backup_format_unknown)
                                }
                                val skippedText = if (importResult.skipped > 0) {
                                    context.getString(
                                        R.string.backup_import_result_skipped,
                                        importResult.skipped
                                    )
                                } else {
                                    ""
                                }
                                context.getString(
                                    R.string.backup_import_result_success,
                                    formatName,
                                    importResult.new,
                                    importResult.updated,
                                    skippedText
                                )
                            } else {
                                operationState = ChatHistoryOperation.FAILED
                                context.getString(R.string.backup_import_result_failed)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            operationState = ChatHistoryOperation.FAILED
                            operationMessage = context.getString(
                                R.string.backup_import_failed_with_reason,
                                e.localizedMessage ?: e.toString()
                            )
                        } finally {
                            pendingImportUri = null
                        }
                    }
                }
            }
        )
    }

    // 模型配置导出安全警告对话框
    if (showModelConfigExportWarning) {
        ModelConfigExportWarningDialog(
            exportPath = exportedModelConfigPath,
            onDismiss = {
                showModelConfigExportWarning = false
                modelConfigOperationMessage = context.getString(
                    R.string.backup_export_result_success,
                    exportedModelConfigPath
                )
            }
        )
    }

    if (showRoomDbRestoreConfirmDialog) {
        val targetName = pendingRoomDbRestoreFile?.name
            ?: pendingRoomDbRestoreUri?.lastPathSegment
            ?: "-"

        AlertDialog(
            onDismissRequest = {
                showRoomDbRestoreConfirmDialog = false
                pendingRoomDbRestoreUri = null
                pendingRoomDbRestoreFile = null
            },
            title = { Text(stringResource(R.string.backup_room_db_restore_confirm_title)) },
            text = { Text(stringResource(R.string.backup_room_db_restore_confirm_message, targetName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRoomDbRestoreConfirmDialog = false
                        val uri = pendingRoomDbRestoreUri
                        val file = pendingRoomDbRestoreFile
                        pendingRoomDbRestoreUri = null
                        pendingRoomDbRestoreFile = null

                        scope.launch {
                            roomDbRestoreOperationState = RoomDatabaseRestoreOperation.RESTORING
                            roomDbRestoreOperationMessage = ""
                            try {
                                withContext(Dispatchers.IO) {
                                    if (file != null) {
                                        RoomDatabaseRestoreManager.restoreFromBackupFile(context, file)
                                    } else if (uri != null) {
                                        try {
                                            context.contentResolver.takePersistableUriPermission(
                                                uri,
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            )
                                        } catch (_: Exception) {
                                        }
                                        RoomDatabaseRestoreManager.restoreFromBackupUri(context, uri)
                                    } else {
                                        throw IllegalStateException("No restore target")
                                    }
                                }

                                roomDbRestoreOperationState = RoomDatabaseRestoreOperation.SUCCESS
                                roomDbRestoreOperationMessage = targetName
                                showRoomDbRestoreRestartDialog = true
                            } catch (e: Exception) {
                                roomDbRestoreOperationState = RoomDatabaseRestoreOperation.FAILED
                                roomDbRestoreOperationMessage = e.localizedMessage ?: e.toString()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.backup_room_db_restore_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRoomDbRestoreConfirmDialog = false
                        pendingRoomDbRestoreUri = null
                        pendingRoomDbRestoreFile = null
                    }
                ) {
                    Text(stringResource(R.string.backup_room_db_restore_cancel_action))
                }
            }
        )
    }

    if (showRawSnapshotRestoreConfirmDialog) {
        val targetName = pendingRawSnapshotRestoreUri?.lastPathSegment ?: "-"

        AlertDialog(
            onDismissRequest = {
                showRawSnapshotRestoreConfirmDialog = false
                pendingRawSnapshotRestoreUri = null
            },
            title = { Text(stringResource(R.string.backup_raw_snapshot_restore_confirm_title)) },
            text = { Text(stringResource(R.string.backup_raw_snapshot_restore_confirm_message, targetName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRawSnapshotRestoreConfirmDialog = false
                        val uri = pendingRawSnapshotRestoreUri
                        pendingRawSnapshotRestoreUri = null
                        if (uri != null) {
                            scope.launch {
                                rawSnapshotOperationState = RawSnapshotOperation.RESTORING
                                rawSnapshotOperationMessage = context.getString(R.string.backup_raw_snapshot_progress_preparing)
                                try {
                                    try {
                                        context.contentResolver.takePersistableUriPermission(
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        )
                                    } catch (_: Exception) {
                                    }
                                    RawSnapshotBackupManager.restoreFromBackupUri(
                                        context = context,
                                        uri = uri,
                                        onProgress = { progress ->
                                            rawSnapshotOperationMessage = when (progress) {
                                                RawSnapshotBackupManager.RestoreProgress.PREPARING ->
                                                    context.getString(R.string.backup_raw_snapshot_progress_preparing)

                                                RawSnapshotBackupManager.RestoreProgress.READING_ZIP ->
                                                    context.getString(R.string.backup_raw_snapshot_progress_reading_zip)

                                                RawSnapshotBackupManager.RestoreProgress.EXTRACTING ->
                                                    context.getString(R.string.backup_raw_snapshot_progress_extracting)

                                                RawSnapshotBackupManager.RestoreProgress.REPLACING_FILES ->
                                                    context.getString(R.string.backup_raw_snapshot_progress_replacing_files)

                                                RawSnapshotBackupManager.RestoreProgress.REPLACING_EXTERNAL_FILES ->
                                                    context.getString(R.string.backup_raw_snapshot_progress_replacing_external_files)

                                                RawSnapshotBackupManager.RestoreProgress.REPLACING_SHARED_PREFS ->
                                                    context.getString(R.string.backup_raw_snapshot_progress_replacing_shared_prefs)

                                                RawSnapshotBackupManager.RestoreProgress.REPLACING_DATASTORE ->
                                                    context.getString(R.string.backup_raw_snapshot_progress_replacing_datastore)

                                                RawSnapshotBackupManager.RestoreProgress.REPLACING_DATABASES ->
                                                    context.getString(R.string.backup_raw_snapshot_progress_replacing_databases)

                                                RawSnapshotBackupManager.RestoreProgress.FINALIZING ->
                                                    context.getString(R.string.backup_raw_snapshot_progress_finalizing)
                                            }
                                        }
                                    )
                                    rawSnapshotOperationState = RawSnapshotOperation.RESTORE_SUCCESS
                                    rawSnapshotOperationMessage = targetName
                                    showRawSnapshotRestoreRestartDialog = true
                                } catch (e: Exception) {
                                    rawSnapshotOperationState = RawSnapshotOperation.FAILED
                                    rawSnapshotOperationMessage = e.localizedMessage ?: e.toString()
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.backup_raw_snapshot_restore_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRawSnapshotRestoreConfirmDialog = false
                        pendingRawSnapshotRestoreUri = null
                    }
                ) {
                    Text(stringResource(R.string.backup_raw_snapshot_restore_cancel_action))
                }
            }
        )
    }

    if (showRoomDbRestoreRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRoomDbRestoreRestartDialog = false },
            title = { Text(stringResource(R.string.backup_room_db_restart_title)) },
            text = { Text(stringResource(R.string.backup_room_db_restart_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRoomDbRestoreRestartDialog = false
                        if (!AppProcessRestarter.scheduleAndExit(context)) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.backup_restart_schedule_failed),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.backup_room_db_restart_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRoomDbRestoreRestartDialog = false }) {
                    Text(stringResource(R.string.backup_room_db_restart_later))
                }
            }
        )
    }

    if (showRawSnapshotRestoreRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRawSnapshotRestoreRestartDialog = false },
            title = { Text(stringResource(R.string.backup_raw_snapshot_restart_title)) },
            text = { Text(stringResource(R.string.backup_raw_snapshot_restart_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRawSnapshotRestoreRestartDialog = false
                        if (!AppProcessRestarter.scheduleAndExit(context)) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.backup_restart_schedule_failed),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.backup_raw_snapshot_restart_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRawSnapshotRestoreRestartDialog = false }) {
                    Text(stringResource(R.string.backup_raw_snapshot_restart_later))
                }
            }
        )
    }
}

private data class DeleteAllChatsResult(
    val deletedCount: Int,
    val skippedLockedCount: Int
)

private suspend fun deleteAllChatHistories(context: Context): DeleteAllChatsResult =
    withContext(Dispatchers.IO) {
        try {
            val chatHistoryManager = ChatHistoryManager.getInstance(context)
            val chatHistories = chatHistoryManager.chatHistoriesFlow.first()
            var deletedCount = 0
            var skippedLockedCount = 0

            for (chatHistory in chatHistories) {
                val deleted = chatHistoryManager.deleteChatHistory(chatHistory.id)
                if (deleted) {
                    deletedCount++
                } else {
                    skippedLockedCount++
                }
            }

            return@withContext DeleteAllChatsResult(
                deletedCount = deletedCount,
                skippedLockedCount = skippedLockedCount
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

private suspend fun importMemoriesFromUri(
    context: Context,
    targetProfileId: String,
    uri: Uri,
    strategy: ImportStrategy
) = withContext(Dispatchers.IO) {
    val jsonString =
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw Exception(context.getString(R.string.backup_open_file_failed))

    if (jsonString.isBlank()) {
        throw Exception(context.getString(R.string.backup_import_file_empty))
    }

    val result =
        MiraMemoryArchiveManager.importFromJson(
            context = context,
            targetProfileId = targetProfileId,
            jsonString = jsonString,
            strategy = strategy,
        )
    CompanionReminderScheduler.getInstance(context).syncAllProfiles()
    result
}
