package com.ai.assistance.operit.ui.features.chat.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.util.AppLogger
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.CompanionReminderIntentDetector
import com.ai.assistance.operit.core.chat.CompanionMemoryReceipt
import com.ai.assistance.operit.core.chat.CompanionMemoryReceiptKind
import com.ai.assistance.operit.core.companion.CompanionReminderScheduler
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.CompanionMemoryType
import com.ai.assistance.operit.data.model.CompanionRecordScope
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PendingQueueMessageItem
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.CompanionReminderIntensity
import com.ai.assistance.operit.data.preferences.CompanionReminderPreferences
import com.ai.assistance.operit.data.preferences.CompanionReminderTarget
import com.ai.assistance.operit.data.preferences.CompanionReminderTargetType
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.data.repository.CompanionMemoryRepository
import com.ai.assistance.operit.ui.components.ErrorDialog
import com.ai.assistance.operit.ui.features.chat.components.*
import com.ai.assistance.operit.ui.features.chat.components.style.input.agent.AgentChatInputSection
import com.ai.assistance.operit.ui.features.chat.components.style.input.classic.ClassicChatInputSection
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ChatInputEvents
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ChatInputHookContext
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ChatInputHookRegistry
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.ChatInputSubmitActions
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.BubbleImageStyleConfig
import com.ai.assistance.operit.ui.features.chat.components.AndroidExportDialog
import com.ai.assistance.operit.ui.features.chat.components.ExportCompleteDialog
import com.ai.assistance.operit.ui.features.chat.components.ExportPlatformDialog
import com.ai.assistance.operit.ui.features.chat.components.ExportProgressDialog
import com.ai.assistance.operit.ui.features.chat.components.WindowsExportDialog
import com.ai.assistance.operit.ui.features.chat.webview.MentionSuggestionPanelStyle
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceScreen
import com.ai.assistance.operit.ui.features.chat.webview.MentionSuggestionPanel
import com.ai.assistance.operit.ui.features.chat.webview.computer.ComputerScreen
import com.ai.assistance.operit.ui.features.chat.util.ConfigurationStateHolder
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.CompanionMemoryEditorDialog
import com.ai.assistance.operit.ui.main.PendingChatDraftHandler
import com.ai.assistance.operit.ui.main.PendingChatOpenHandler
import com.ai.assistance.operit.ui.main.screens.GestureStateHolder
import com.ai.assistance.operit.ui.main.SharedFileHandler
import java.io.File
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.flowOf
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.ui.main.components.LocalSetScreenSoftInputMode
import com.ai.assistance.operit.ui.main.components.LocalSetUseScreenImePadding
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatHistoryDisplayMode
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import com.ai.assistance.operit.plugins.chatview.ChatViewEvent
import com.ai.assistance.operit.plugins.chatview.ChatViewHookParams
import com.ai.assistance.operit.plugins.chatview.ChatViewHookPluginRegistry
import java.util.UUID

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(showBackground = true)
fun AIChatScreen(
        padding: PaddingValues = PaddingValues(),
        viewModel: ChatViewModel? = null,
        isFloatingMode: Boolean = false,
        onLoading: (Boolean) -> Unit = {},
        onError: (String) -> Unit = {},
        hasBackgroundImage: Boolean = false,
        onNavigateToTokenConfig: () -> Unit = {},
        onNavigateToSettings: () -> Unit = {},
        onNavigateToUserPreferences: () -> Unit = {},
        onNavigateToModelConfig: () -> Unit = {},
        onNavigateToModelPrompts: () -> Unit = {},
        onNavigateToPackageManager: () -> Unit = {},
        onNavigateToPersona: () -> Unit = {},
        onNavigateToMemory: () -> Unit = {},
        onNavigateToVoiceSettings: () -> Unit = {},
        onNavigateToCapabilities: () -> Unit = {},
        onGestureConsumed: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    val isCurrentScreen = LocalIsCurrentScreen.current
// Correctly initialize ViewModel using the viewModel() composable function
val actualViewModel: ChatViewModel = viewModel ?: viewModel { ChatViewModel(context.applicationContext) }

    // 设置权限系统的颜色方案
    LaunchedEffect(colorScheme) { actualViewModel.setPermissionSystemColorScheme(colorScheme) }

    // Monitor shared files from external apps
    val sharedFiles by SharedFileHandler.sharedFiles.collectAsState()
    val sharedFileText by SharedFileHandler.sharedFileText.collectAsState()
    val sharedText by SharedFileHandler.sharedText.collectAsState()

    // 添加麦克风权限请求启动器
    val requestMicrophonePermissionLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    // This launcher is now used inside the ViewModel's permission check flow
                    // It's kept here because it's tied to the composable lifecycle.
                    // The actual logic is now triggered from within the ViewModel after the check.
                } else {
                    // 权限被拒绝
                    android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.microphone_permission_denied),
                                    android.widget.Toast.LENGTH_SHORT
                            )
                            .show()
                }
            }

    // Get background image state
    val preferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val displayPreferencesManager = remember { DisplayPreferencesManager.getInstance(context) }
    val useBackgroundImage by preferencesManager.useBackgroundImage.collectAsState(initial = false)
    val backgroundImageUri by preferencesManager.backgroundImageUri.collectAsState(initial = null)
    val chatHeaderTransparent by preferencesManager.chatHeaderTransparent.collectAsState(initial = false)
    val chatInputTransparent by preferencesManager.chatInputTransparent.collectAsState(initial = true)
    val chatInputFloating by preferencesManager.chatInputFloating.collectAsState(initial = true)
    val chatInputLiquidGlassRaw by
        preferencesManager.chatInputLiquidGlass.collectAsState(initial = true)
    val chatInputWaterGlass by
        preferencesManager.chatInputWaterGlass.collectAsState(initial = false)
    val chatInputLiquidGlass = chatInputLiquidGlassRaw && !chatInputWaterGlass
    val chatHeaderHistoryIconColor by preferencesManager.chatHeaderHistoryIconColor.collectAsState(
            initial = null
    )
    val chatHeaderPipIconColor by preferencesManager.chatHeaderPipIconColor.collectAsState(initial = null)
    val chatHeaderOverlayMode by preferencesManager.chatHeaderOverlayMode.collectAsState(initial = false)
    val showInputProcessingStatus by preferencesManager.showInputProcessingStatus.collectAsState(initial = true)
    val enableEnterToSend by displayPreferencesManager.enableEnterToSend.collectAsState(initial = false)
    val showChatFloatingDotsAnimation by
        preferencesManager.showChatFloatingDotsAnimation.collectAsState(initial = true)
    val hasBackgroundImageFromPrefs = useBackgroundImage && backgroundImageUri != null
    val effectiveHasBackgroundImage = hasBackgroundImage || hasBackgroundImageFromPrefs

    // Collect chat style from preferences
    val chatStyleSetting by preferencesManager.chatStyle.collectAsState(initial = UserPreferencesManager.DEFAULT_CHAT_STYLE)
    val chatStyle = remember(chatStyleSetting) {
        when (chatStyleSetting) {
            UserPreferencesManager.CHAT_STYLE_BUBBLE -> ChatStyle.BUBBLE
            else -> ChatStyle.CURSOR
        }
    }
    val inputStyle by
        preferencesManager.inputStyle.collectAsState(
            initial = UserPreferencesManager.DEFAULT_INPUT_STYLE,
        )
    val cursorUserBubbleFollowTheme by
        preferencesManager.cursorUserBubbleFollowTheme.collectAsState(initial = true)
    val cursorUserBubbleLiquidGlassRaw by
        preferencesManager.cursorUserBubbleLiquidGlass.collectAsState(initial = false)
    val cursorUserBubbleWaterGlass by
        preferencesManager.cursorUserBubbleWaterGlass.collectAsState(initial = false)
    val cursorUserBubbleLiquidGlass = cursorUserBubbleLiquidGlassRaw && !cursorUserBubbleWaterGlass
    val bubbleUserBubbleLiquidGlassRaw by
        preferencesManager.bubbleUserBubbleLiquidGlass.collectAsState(initial = false)
    val bubbleUserBubbleWaterGlass by
        preferencesManager.bubbleUserBubbleWaterGlass.collectAsState(initial = false)
    val bubbleUserBubbleLiquidGlass =
        bubbleUserBubbleLiquidGlassRaw && !bubbleUserBubbleWaterGlass
    val bubbleAiBubbleLiquidGlassRaw by
        preferencesManager.bubbleAiBubbleLiquidGlass.collectAsState(initial = false)
    val bubbleAiBubbleWaterGlass by
        preferencesManager.bubbleAiBubbleWaterGlass.collectAsState(initial = false)
    val bubbleAiBubbleLiquidGlass =
        bubbleAiBubbleLiquidGlassRaw && !bubbleAiBubbleWaterGlass
    val cursorUserBubbleColorValue by
        preferencesManager.cursorUserBubbleColor.collectAsState(initial = null)
    val bubbleUserBubbleColorValue by
        preferencesManager.bubbleUserBubbleColor.collectAsState(initial = null)
    val bubbleAiBubbleColorValue by
        preferencesManager.bubbleAiBubbleColor.collectAsState(initial = null)
    val bubbleUserTextColorValue by
        preferencesManager.bubbleUserTextColor.collectAsState(initial = null)
    val bubbleAiTextColorValue by
        preferencesManager.bubbleAiTextColor.collectAsState(initial = null)
    val bubbleUserUseImage by
        preferencesManager.bubbleUserUseImage.collectAsState(initial = false)
    val bubbleAiUseImage by
        preferencesManager.bubbleAiUseImage.collectAsState(initial = false)
    val bubbleUserImageUri by preferencesManager.bubbleUserImageUri.collectAsState(initial = null)
    val bubbleAiImageUri by preferencesManager.bubbleAiImageUri.collectAsState(initial = null)
    val bubbleUserImageCropLeft by
        preferencesManager.bubbleUserImageCropLeft.collectAsState(initial = 0f)
    val bubbleUserImageCropTop by
        preferencesManager.bubbleUserImageCropTop.collectAsState(initial = 0f)
    val bubbleUserImageCropRight by
        preferencesManager.bubbleUserImageCropRight.collectAsState(initial = 0f)
    val bubbleUserImageCropBottom by
        preferencesManager.bubbleUserImageCropBottom.collectAsState(initial = 0f)
    val bubbleUserImageRepeatStart by
        preferencesManager.bubbleUserImageRepeatStart.collectAsState(initial = 0.35f)
    val bubbleUserImageRepeatEnd by
        preferencesManager.bubbleUserImageRepeatEnd.collectAsState(initial = 0.65f)
    val bubbleUserImageRepeatYStart by
        preferencesManager.bubbleUserImageRepeatYStart.collectAsState(initial = 0.35f)
    val bubbleUserImageRepeatYEnd by
        preferencesManager.bubbleUserImageRepeatYEnd.collectAsState(initial = 0.65f)
    val bubbleUserImageScale by
        preferencesManager.bubbleUserImageScale.collectAsState(initial = 1f)
    val bubbleAiImageCropLeft by
        preferencesManager.bubbleAiImageCropLeft.collectAsState(initial = 0f)
    val bubbleAiImageCropTop by
        preferencesManager.bubbleAiImageCropTop.collectAsState(initial = 0f)
    val bubbleAiImageCropRight by
        preferencesManager.bubbleAiImageCropRight.collectAsState(initial = 0f)
    val bubbleAiImageCropBottom by
        preferencesManager.bubbleAiImageCropBottom.collectAsState(initial = 0f)
    val bubbleAiImageRepeatStart by
        preferencesManager.bubbleAiImageRepeatStart.collectAsState(initial = 0.35f)
    val bubbleAiImageRepeatEnd by
        preferencesManager.bubbleAiImageRepeatEnd.collectAsState(initial = 0.65f)
    val bubbleAiImageRepeatYStart by
        preferencesManager.bubbleAiImageRepeatYStart.collectAsState(initial = 0.35f)
    val bubbleAiImageRepeatYEnd by
        preferencesManager.bubbleAiImageRepeatYEnd.collectAsState(initial = 0.65f)
    val bubbleAiImageScale by
        preferencesManager.bubbleAiImageScale.collectAsState(initial = 1f)
    val bubbleImageRenderMode by
        preferencesManager.bubbleImageRenderMode.collectAsState(
            initial = UserPreferencesManager.BUBBLE_IMAGE_RENDER_MODE_TILED_NINE_SLICE,
        )
    val bubbleUserRoundedCornersEnabled by
        preferencesManager.bubbleUserRoundedCornersEnabled.collectAsState(initial = true)
    val bubbleAiRoundedCornersEnabled by
        preferencesManager.bubbleAiRoundedCornersEnabled.collectAsState(initial = true)
    val bubbleUserContentPaddingLeft by
        preferencesManager.bubbleUserContentPaddingLeft.collectAsState(initial = 12f)
    val bubbleUserContentPaddingRight by
        preferencesManager.bubbleUserContentPaddingRight.collectAsState(initial = 12f)
    val bubbleAiContentPaddingLeft by
        preferencesManager.bubbleAiContentPaddingLeft.collectAsState(initial = 12f)
    val bubbleAiContentPaddingRight by
        preferencesManager.bubbleAiContentPaddingRight.collectAsState(initial = 12f)
    // Collect chat area horizontal padding from preferences
    val chatAreaHorizontalPadding by preferencesManager.chatAreaHorizontalPadding.collectAsState(initial = 16f)

    // 添加编辑按钮和编辑状态
    val editingMessageIndex = remember { mutableStateOf<Int?>(null) }
    val editingMessageContent = remember { mutableStateOf("") }

    // Collect state from ViewModel
    val apiKey by actualViewModel.apiKey.collectAsState()
    val apiEndpoint by actualViewModel.apiEndpoint.collectAsState()
    val modelName by actualViewModel.modelName.collectAsState()
    val apiProviderType by actualViewModel.apiProviderType.collectAsState()
    val isConfigured by actualViewModel.isConfigured.collectAsState()
    val chatHistory by actualViewModel.chatHistory.collectAsState()
    // 仅对当前会话显示处理中状态（影响“停止/发送”按钮）
    val isLoading by actualViewModel.currentChatIsLoading.collectAsState()
    val errorMessage by actualViewModel.errorMessage.collectAsState()
    // 按会话隔离的输入处理状态（用于进度条文案）
    val inputProcessingState by actualViewModel.currentChatInputProcessingState.collectAsState()

    val featureStates by actualViewModel.featureToggles.collectAsState()
    val enableThinkingMode by actualViewModel.enableThinkingMode.collectAsState() // 收集思考模式状态
    val thinkingQualityLevel by actualViewModel.thinkingQualityLevel.collectAsState()
    val enableMemoryAutoUpdate by actualViewModel.enableMemoryAutoUpdate.collectAsState()
    val enableMaxContextMode by actualViewModel.enableMaxContextMode.collectAsState()
    val enableTools by actualViewModel.enableTools.collectAsState()
    val toolPromptVisibility by actualViewModel.toolPromptVisibility.collectAsState()
    val toolPromptOrder by actualViewModel.toolPromptOrder.collectAsState()
    val disableStreamOutput by actualViewModel.disableStreamOutput.collectAsState()
    val disableUserPreferenceDescription by
            actualViewModel.disableUserPreferenceDescription.collectAsState()
    val isAutoReadEnabled by actualViewModel.isAutoReadEnabled.collectAsState()
    val showChatHistorySelector by actualViewModel.showChatHistorySelector.collectAsState()
    val chatHistories by actualViewModel.chatHistories.collectAsState()
    val currentChatId by actualViewModel.currentChatId.collectAsState()
    val companionMemoryReceiptEvents = actualViewModel.companionMemoryReceiptEvents
    val companionMemoryRepository = remember(context) { CompanionMemoryRepository(context) }
    var memoryReceiptForCorrection by remember { mutableStateOf<CompanionMemoryReceipt?>(null) }
    var memoryCorrectionRecord by remember { mutableStateOf<CompanionMemoryRecordEntity?>(null) }
    var isSavingMemoryCorrection by remember { mutableStateOf(false) }
    var memoryCorrectionError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentChatId) {
        editingMessageIndex.value = null
        editingMessageContent.value = ""
        if (!isSavingMemoryCorrection) {
            memoryCorrectionRecord = null
            memoryReceiptForCorrection = null
            memoryCorrectionError = null
        }
        actualViewModel.clearReplyToMessage()
    }
    val hasNewerDisplayHistory by actualViewModel.hasNewerDisplayHistory.collectAsState()
    val isLoadingDisplayWindow by actualViewModel.isLoadingDisplayWindow.collectAsState()
    val popupMessage by actualViewModel.popupMessage.collectAsState()
    val chatViewRuntime = if (isFloatingMode) "floating" else "main"
    val chatViewId = rememberSaveable { UUID.randomUUID().toString() }
    val currentChatView = remember(chatHistories, currentChatId) {
        chatHistories.find { it.id == currentChatId }
    }
    val latestChatViewParams by rememberUpdatedState(
        ChatViewHookParams(
            context = context,
            viewId = chatViewId,
            chatId = currentChatId,
            workspacePath = currentChatView?.workspace,
            workspaceEnv = currentChatView?.workspaceEnv,
            runtime = chatViewRuntime,
            title = currentChatView?.title
        )
    )
    var hasDispatchedChatViewOpen by remember(chatViewId) { mutableStateOf(false) }
    LaunchedEffect(
        chatViewId,
        currentChatId,
        currentChatView?.workspace,
        currentChatView?.workspaceEnv,
        currentChatView?.title,
        chatViewRuntime
    ) {
        val event =
            if (hasDispatchedChatViewOpen) {
                ChatViewEvent.VIEW_UPDATED
            } else {
                hasDispatchedChatViewOpen = true
                ChatViewEvent.VIEW_OPENED
            }
        ChatViewHookPluginRegistry.dispatchAsync(event, latestChatViewParams)
    }
    DisposableEffect(chatViewId) {
        onDispose {
            ChatViewHookPluginRegistry.dispatchAsync(
                ChatViewEvent.VIEW_CLOSED,
                latestChatViewParams
            )
        }
    }
    // 收集滚动事件
    val scrollToBottomEvent = actualViewModel.scrollToBottomEvent
    // 从ViewModel收集新的状态
    val shouldShowConfigDialog by actualViewModel.shouldShowConfigDialog.collectAsState()
    val isWorkspaceOpen by actualViewModel.isWorkspaceOpen.collectAsState()
    val isWorkspacePreparing by actualViewModel.isWorkspacePreparing.collectAsState()

    // 添加模型建议对话框状态
    var showModelSuggestionDialog by remember { mutableStateOf(false) }
    
    // 添加记忆文件夹选择对话框状态
    var showMemoryFolderDialog by remember { mutableStateOf(false) }

    // 当模型名称加载后，检查是否为建议更换的模型
    LaunchedEffect(modelName) {
        if (modelName.isNotBlank() && modelName.contains("deepseek-r1-0528-qwen3-8b:free", ignoreCase = true)) {
            showModelSuggestionDialog = true
        }
    }

    // 模型建议对话框
    if (showModelSuggestionDialog) {
        AlertDialog(
            onDismissRequest = { showModelSuggestionDialog = false },
            title = { Text(stringResource(R.string.model_suggestion_title)) },
            text = { Text(stringResource(R.string.model_suggestion_message)) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        showModelSuggestionDialog = false
                        // 如果用户已输入token，直接保存配置
                        actualViewModel.updateApiKey("")
                        actualViewModel.updateApiEndpoint(ApiPreferences.DEFAULT_API_ENDPOINT)
                        actualViewModel.updateModelName(ApiPreferences.DEFAULT_MODEL_NAME)
                        actualViewModel.updateApiProviderType(ApiProviderType.DEEPSEEK)
                        actualViewModel.saveApiSettings()

                        // 新增：重置状态以重新显示配置界面
                        ConfigurationStateHolder.hasConfirmedDefaultInSession = false
                        actualViewModel.showConfigurationScreen()
                        
                    }) {
                        Text(stringResource(R.string.change_model))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showModelSuggestionDialog = false }) {
                    Text(stringResource(R.string.ignore))
                }
            }
        )
    }

    SharedIncomingContentHandler(
        sharedFiles = sharedFiles,
        sharedFileText = sharedFileText,
        sharedText = sharedText,
        chatHistories = chatHistories,
        currentChatId = currentChatId,
        onHandleSharedFiles = actualViewModel::handleSharedFiles,
        onHandleSharedText = actualViewModel::handleSharedText,
        onClearSharedFiles = SharedFileHandler::clearSharedFiles,
        onClearSharedText = SharedFileHandler::clearSharedText
    )

    val pendingChatDraft by PendingChatDraftHandler.pendingDraft.collectAsState()
    LaunchedEffect(pendingChatDraft, isCurrentScreen) {
        if (!isCurrentScreen) return@LaunchedEffect
        val draft = pendingChatDraft?.trim().orEmpty()
        if (draft.isBlank()) return@LaunchedEffect

        actualViewModel.createNewChatWithDraft(draft)
        PendingChatDraftHandler.clearPendingDraft()
    }

    val pendingChatId by PendingChatOpenHandler.pendingChatId.collectAsState()
    LaunchedEffect(pendingChatId, isCurrentScreen) {
        if (!isCurrentScreen) return@LaunchedEffect
        val targetChatId = pendingChatId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        actualViewModel.switchChat(targetChatId)
        PendingChatOpenHandler.clear()
    }


    // 添加WebView刷新相关状态
    val webViewRefreshCounter by actualViewModel.webViewRefreshCounter.collectAsState()

    // Floating window mode state
    val isFloatingMode by actualViewModel.isFloatingMode.collectAsState()
    val canDrawOverlays = remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // UI state
    val scrollState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val characterGroupCardManager = remember { CharacterGroupCardManager.getInstance(context) }
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val activePrompt by activePromptManager.activePromptFlow.collectAsState(
        initial = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
    )
    val activeCharacterCard by remember(activePrompt) {
        when (val prompt = activePrompt) {
            is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCardFlow(prompt.id)
            is ActivePrompt.CharacterGroup -> flowOf(null)
        }
    }.collectAsState(initial = null)
    val activeCharacterGroup by remember(activePrompt) {
        when (val prompt = activePrompt) {
            is ActivePrompt.CharacterGroup -> characterGroupCardManager.getCharacterGroupCardFlow(prompt.id)
            is ActivePrompt.CharacterCard -> flowOf(null)
        }
    }.collectAsState(initial = null)
    var historyDisplayMode by rememberLocal(
        "chat_history_display_mode",
        ChatHistoryDisplayMode.BY_FOLDER
    )
    var autoSwitchCharacterCard by rememberLocal(
        "chat_history_auto_switch_character_card",
        false
    )
    var autoSwitchChatOnCharacterSelect by rememberLocal(
        "chat_history_auto_switch_chat_on_character_select",
        false
    )
    val displayedChatHistories = remember(
        chatHistories,
        activePrompt,
        activeCharacterCard,
        activeCharacterGroup,
        historyDisplayMode
    ) {
        when (historyDisplayMode) {
            ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY -> {
                when (activePrompt) {
                    is ActivePrompt.CharacterGroup -> {
                        val group = activeCharacterGroup ?: return@remember emptyList()
                        chatHistories.filter { history ->
                            history.characterGroupId == group.id
                        }
                    }
                    is ActivePrompt.CharacterCard -> {
                        val activeCard = activeCharacterCard ?: return@remember emptyList()
                        chatHistories.filter { history ->
                            val historyCard = history.characterCardName
                            if (activeCard.isDefault) {
                                historyCard == null || historyCard == activeCard.name
                            } else {
                                historyCard == activeCard.name
                            }
                        }
                    }
                }
            }
            else -> chatHistories
        }
    }
    LaunchedEffect(autoSwitchCharacterCard) {
        actualViewModel.setAutoSwitchCharacterCard(autoSwitchCharacterCard)
    }
    LaunchedEffect(autoSwitchChatOnCharacterSelect) {
        actualViewModel.setAutoSwitchChatOnCharacterSelect(autoSwitchChatOnCharacterSelect)
    }
    LaunchedEffect(
        activePrompt,
        activeCharacterCard,
        activeCharacterGroup,
        displayedChatHistories,
        currentChatId,
        chatHistories,
        historyDisplayMode
    ) {
        if (historyDisplayMode != ChatHistoryDisplayMode.CURRENT_CHARACTER_ONLY) {
            return@LaunchedEffect
        }
        val hasActiveTarget = when (activePrompt) {
            is ActivePrompt.CharacterGroup -> activeCharacterGroup != null
            is ActivePrompt.CharacterCard -> activeCharacterCard != null
        }
        if (!hasActiveTarget || displayedChatHistories.isEmpty()) {
            return@LaunchedEffect
        }
        val currentId = currentChatId ?: ""
        if (currentId.isBlank()) {
            actualViewModel.switchChat(displayedChatHistories.first().id)
        }
    }
    val characterCardBoundChatModelConfigId =
        activeCharacterCard
            ?.takeIf {
                activePrompt is ActivePrompt.CharacterCard &&
                    CharacterCardChatModelBindingMode.normalize(it.chatModelBindingMode) ==
                    CharacterCardChatModelBindingMode.FIXED_CONFIG &&
                    !it.chatModelConfigId.isNullOrBlank()
            }
            ?.chatModelConfigId
    val characterCardBoundChatModelIndex =
        activeCharacterCard?.chatModelIndex?.coerceAtLeast(0) ?: 0
    val characterCardBoundMemoryProfileId =
        activeCharacterCard
            ?.takeIf {
                activePrompt is ActivePrompt.CharacterCard &&
                    CharacterCardMemoryProfileBindingMode.normalize(it.memoryProfileBindingMode) ==
                    CharacterCardMemoryProfileBindingMode.FIXED_PROFILE &&
                    !it.memoryProfileId.isNullOrBlank()
            }
            ?.memoryProfileId
    


    // 确保每次应用启动时正确处理配置界面的显示逻辑
    LaunchedEffect(apiKey) {
        // 只有当apiKey有效值时才执行逻辑，防止初始化阶段的不正确判断
        if (apiKey.isNotBlank()) {
            // 如果使用的是自定义配置，标记为已确认，不显示配置界面
            ConfigurationStateHolder.hasConfirmedDefaultInSession = true
        }
    }

    val defaultUserMessageColor = MaterialTheme.colorScheme.primaryContainer
    val defaultAiMessageColor = MaterialTheme.colorScheme.surface
    val cursorCustomUserMessageColor = cursorUserBubbleColorValue?.let(::Color)
    val bubbleCustomUserMessageColor = bubbleUserBubbleColorValue?.let(::Color)
    val bubbleCustomAiMessageColor = bubbleAiBubbleColorValue?.let(::Color)
    val bubbleCustomUserTextColor = bubbleUserTextColorValue?.let(::Color)
    val bubbleCustomAiTextColor = bubbleAiTextColorValue?.let(::Color)

    val userMessageColor =
        when (chatStyle) {
            ChatStyle.CURSOR -> {
                if (cursorUserBubbleFollowTheme) {
                    defaultUserMessageColor
                } else {
                    cursorCustomUserMessageColor ?: defaultUserMessageColor
                }
            }

            ChatStyle.BUBBLE -> bubbleCustomUserMessageColor ?: defaultUserMessageColor
        }
    val aiMessageColor =
        when (chatStyle) {
            ChatStyle.BUBBLE -> bubbleCustomAiMessageColor ?: defaultAiMessageColor
            ChatStyle.CURSOR -> defaultAiMessageColor
        }
    val userTextColor =
        when {
            chatStyle == ChatStyle.CURSOR && cursorUserBubbleFollowTheme ->
                MaterialTheme.colorScheme.onPrimaryContainer
            chatStyle == ChatStyle.BUBBLE && bubbleCustomUserTextColor != null ->
                bubbleCustomUserTextColor
            else -> getTextColorForBackground(userMessageColor.copy(alpha = 1f))
        }
    val aiTextColor =
        when {
            chatStyle == ChatStyle.BUBBLE && bubbleCustomAiTextColor != null ->
                bubbleCustomAiTextColor
            chatStyle == ChatStyle.BUBBLE ->
                getTextColorForBackground(aiMessageColor.copy(alpha = 1f))
            else -> MaterialTheme.colorScheme.onSurface
        }
    val systemMessageColor = MaterialTheme.colorScheme.surfaceVariant
    val systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val bubbleUserImageStyle =
        remember(
            chatStyle,
            bubbleUserBubbleLiquidGlass,
            bubbleUserBubbleWaterGlass,
            bubbleUserUseImage,
            bubbleUserImageUri,
            bubbleUserImageCropLeft,
            bubbleUserImageCropTop,
            bubbleUserImageCropRight,
            bubbleUserImageCropBottom,
            bubbleUserImageRepeatStart,
            bubbleUserImageRepeatEnd,
            bubbleUserImageRepeatYStart,
            bubbleUserImageRepeatYEnd,
            bubbleUserImageScale,
            bubbleImageRenderMode,
        ) {
            val imageUri = bubbleUserImageUri
            if (
                chatStyle == ChatStyle.BUBBLE &&
                    !bubbleUserBubbleLiquidGlass &&
                    !bubbleUserBubbleWaterGlass &&
                    bubbleUserUseImage &&
                    !imageUri.isNullOrBlank()
            ) {
                BubbleImageStyleConfig(
                    imageUri = imageUri,
                    cropLeftRatio = bubbleUserImageCropLeft,
                    cropTopRatio = bubbleUserImageCropTop,
                    cropRightRatio = bubbleUserImageCropRight,
                    cropBottomRatio = bubbleUserImageCropBottom,
                    repeatXStartRatio = bubbleUserImageRepeatStart,
                    repeatXEndRatio = bubbleUserImageRepeatEnd,
                    repeatYStartRatio = bubbleUserImageRepeatYStart,
                    repeatYEndRatio = bubbleUserImageRepeatYEnd,
                    imageScale = bubbleUserImageScale,
                    renderMode = bubbleImageRenderMode,
                )
            } else {
                null
            }
        }

    val bubbleAiImageStyle =
        remember(
            chatStyle,
            bubbleAiUseImage,
            bubbleAiBubbleLiquidGlass,
            bubbleAiBubbleWaterGlass,
            bubbleAiImageUri,
            bubbleAiImageCropLeft,
            bubbleAiImageCropTop,
            bubbleAiImageCropRight,
            bubbleAiImageCropBottom,
            bubbleAiImageRepeatStart,
            bubbleAiImageRepeatEnd,
            bubbleAiImageRepeatYStart,
            bubbleAiImageRepeatYEnd,
            bubbleAiImageScale,
            bubbleImageRenderMode,
        ) {
            val imageUri = bubbleAiImageUri
            if (
                chatStyle == ChatStyle.BUBBLE &&
                    !bubbleAiBubbleLiquidGlass &&
                    !bubbleAiBubbleWaterGlass &&
                    bubbleAiUseImage &&
                    !imageUri.isNullOrBlank()
            ) {
                BubbleImageStyleConfig(
                    imageUri = imageUri,
                    cropLeftRatio = bubbleAiImageCropLeft,
                    cropTopRatio = bubbleAiImageCropTop,
                    cropRightRatio = bubbleAiImageCropRight,
                    cropBottomRatio = bubbleAiImageCropBottom,
                    repeatXStartRatio = bubbleAiImageRepeatStart,
                    repeatXEndRatio = bubbleAiImageRepeatEnd,
                    repeatYStartRatio = bubbleAiImageRepeatYStart,
                    repeatYEndRatio = bubbleAiImageRepeatYEnd,
                    imageScale = bubbleAiImageScale,
                    renderMode = bubbleImageRenderMode,
                )
            } else {
                null
            }
        }

    // 滚动状态
    var autoScrollToBottom by remember { mutableStateOf(true) }
    val onAutoScrollToBottomChange = remember { { it: Boolean -> autoScrollToBottom = it } }
    val requestAutoScrollToBottom = remember { { autoScrollToBottom = true } }
    val latestChatHistory by rememberUpdatedState(chatHistory)
    val latestAutoScrollToBottom by rememberUpdatedState(autoScrollToBottom)
    val latestHasNewerDisplayHistory by rememberUpdatedState(hasNewerDisplayHistory)
    val latestIsLoadingDisplayWindow by rememberUpdatedState(isLoadingDisplayWindow)

    LaunchedEffect(currentChatId) {
        autoScrollToBottom = true
    }

    // 处理来自ViewModel的滚动事件（流式输出时）
    LaunchedEffect(Unit) {
        scrollToBottomEvent.collect {
            if (
                latestAutoScrollToBottom &&
                    !latestHasNewerDisplayHistory &&
                    !latestIsLoadingDisplayWindow
            ) {
                try {
                    if (latestChatHistory.isNotEmpty()) {
                        val lastItemIndex = scrollState.layoutInfo.totalItemsCount - 1
                        if (lastItemIndex >= 0) {
                            scrollState.animateScrollToItem(lastItemIndex)
                        }
                    }
                } catch (e: Exception) {
                    // AppLogger.e("AIChatScreen", "自动滚动失败", e)
                }
            }
        }
    }

    // 移除原有的 snackbar 错误处理
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(
        companionMemoryReceiptEvents,
        snackbarHostState,
        isCurrentScreen,
        currentChatId,
    ) {
        if (!isCurrentScreen) return@LaunchedEffect
        companionMemoryReceiptEvents.collectLatest { receipt ->
            if (receipt.conversationId != currentChatId) return@collectLatest
            if (receipt.kind == CompanionMemoryReceiptKind.DELETED) {
                snackbarHostState.showSnackbar(
                    message =
                        resources.getString(
                            R.string.mira_memory_deleted_receipt,
                            receipt.displayLabel ?: receipt.summary,
                        ),
                    duration = SnackbarDuration.Short,
                )
                return@collectLatest
            }
            val result =
                snackbarHostState.showSnackbar(
                    message =
                        resources.getString(
                            R.string.mira_memory_saved_receipt,
                            receipt.displayLabel ?: receipt.summary,
                        ),
                    actionLabel = resources.getString(R.string.mira_memory_receipt_correct_action),
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) {
                val record = companionMemoryRepository.getRecordById(receipt.recordId)
                if (record == null) {
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.mira_memory_receipt_not_found),
                    )
                } else {
                    memoryReceiptForCorrection = receipt
                    memoryCorrectionRecord = record
                    memoryCorrectionError = null
                }
            }
        }
    }

    memoryCorrectionRecord?.let { record ->
        val recordScope =
            CompanionRecordScope.entries.firstOrNull { it.name == record.scope }
                ?: CompanionRecordScope.USER
        val recordType =
            CompanionMemoryType.entries.firstOrNull { it.name == record.type }
                ?: CompanionMemoryType.FACT
        CompanionMemoryEditorDialog(
            record = record,
            scope = recordScope,
            defaultType = recordType,
            allowedTypes = listOf(recordType),
            activeTargetName = stringResource(R.string.mate_memory_current_character),
            isSaving = isSavingMemoryCorrection,
            errorMessage = memoryCorrectionError,
            evidenceQuote =
                memoryReceiptForCorrection
                    ?.takeIf { it.recordId == record.id }
                    ?.evidenceQuote,
            onDismiss = {
                if (!isSavingMemoryCorrection) {
                    memoryCorrectionRecord = null
                    memoryReceiptForCorrection = null
                    memoryCorrectionError = null
                }
            },
            onSave = { type, displayLabel, value ->
                coroutineScope.launch {
                    isSavingMemoryCorrection = true
                    memoryCorrectionError = null
                    try {
                        val savedId =
                            companionMemoryRepository.saveManualRecord(
                                profileId = record.profileId,
                                companionId = record.companionId,
                                scope = recordScope,
                                type = type,
                                displayLabel = displayLabel,
                                value = value,
                                existingRecord = record,
                                conversationId = record.conversationId,
                            )
                        if (savedId == null) {
                            memoryCorrectionError =
                                resources.getString(R.string.mate_memory_manual_save_failed)
                        } else {
                            memoryCorrectionRecord = null
                            memoryReceiptForCorrection = null
                            snackbarHostState.showSnackbar(
                                resources.getString(R.string.mira_memory_receipt_correction_saved),
                            )
                        }
                    } catch (error: Exception) {
                        AppLogger.e("AIChatScreen", "Failed to correct companion memory", error)
                        memoryCorrectionError =
                            resources.getString(R.string.mate_memory_manual_save_failed)
                    } finally {
                        isSavingMemoryCorrection = false
                    }
                }
            },
        )
    }

    // 用新的错误弹窗替换原有的错误显示逻辑
    errorMessage?.let { message ->
        ErrorDialog(errorMessage = message, onDismiss = { actualViewModel.dismissErrorDialog() })
    }

    val toastEvent by actualViewModel.toastEvent.collectAsState()

    // Save chat on app exit
    DisposableEffect(Unit) {
        onDispose {
            // This is handled by the ViewModel
        }
    }

    // 确定是否显示配置界面的最终逻辑
    val showConfig = shouldShowConfigDialog && !ConfigurationStateHolder.hasConfirmedDefaultInSession

    val currentOnGestureConsumed by rememberUpdatedState(onGestureConsumed)
    val onChatScreenGestureConsumedChange = remember {
        { consumed: Boolean ->
            GestureStateHolder.isChatScreenGestureConsumed = consumed
            currentOnGestureConsumed(consumed)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            GestureStateHolder.isChatScreenGestureConsumed = false
            currentOnGestureConsumed(false)
        }
    }
    val onSwitchCharacter = remember(actualViewModel) {
        { target: CharacterSelectorTarget ->
            actualViewModel.switchActiveCharacterTarget(target)
        }
    }

    // 收集WebView显示状态
    val showWebView by actualViewModel.showWebView.collectAsState()
    // 收集AI电脑显示状态
    val showAiComputer by actualViewModel.showAiComputer.collectAsState()
    BackHandler(enabled = showWebView && !showAiComputer) {
        actualViewModel.onWorkspaceButtonClick()
    }
    BackHandler(enabled = showAiComputer) {
        actualViewModel.onAiComputerButtonClick()
    }
    // WorkspaceManager owns its IME inset. AI Computer has no local inset handling.
    val shouldUseOuterImePadding = showAiComputer
    var hasEverShownWebView by remember { mutableStateOf(false) }
    LaunchedEffect(showWebView, isWorkspacePreparing) {
        if (showWebView || isWorkspacePreparing) {
            hasEverShownWebView = true
        }
    }
    // 处理文件选择器请求
    val fileChooserRequest by actualViewModel.uiStateDelegate.fileChooserRequest.collectAsState()
    val fileChooserLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                // 处理文件选择结果
                actualViewModel.handleFileChooserResult(result.resultCode, result.data)
                // 清除请求
                actualViewModel.uiStateDelegate.clearFileChooserRequest()
            }

    // 启动文件选择器
    LaunchedEffect(fileChooserRequest) {
        fileChooserRequest?.let { fileChooserLauncher.launch(it) }
    }

    // 从CompositionLocal获取设置TopBar Actions的函数
    val setScreenSoftInputMode = LocalSetScreenSoftInputMode.current
    val setUseScreenImePadding = LocalSetUseScreenImePadding.current
    val requestedSoftInputMode = android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    SideEffect {
        if (isCurrentScreen) {
            setScreenSoftInputMode(requestedSoftInputMode)
            setUseScreenImePadding(shouldUseOuterImePadding)
        }
    }


    // 导出相关状态
    var showExportPlatformDialog by remember(currentChatId) { mutableStateOf(false) }
    var showAndroidExportDialog by remember(currentChatId) { mutableStateOf(false) }
    var showWindowsExportDialog by remember(currentChatId) { mutableStateOf(false) }
    var showExportProgressDialog by remember(currentChatId) { mutableStateOf(false) }
    var showExportCompleteDialog by remember(currentChatId) { mutableStateOf(false) }
    var exportProgress by remember(currentChatId) { mutableStateOf(0f) }
    var exportStatus by remember(currentChatId) { mutableStateOf("") }
    var exportSuccess by remember(currentChatId) { mutableStateOf(false) }
    var exportFilePath by remember(currentChatId) { mutableStateOf<String?>(null) }
    var exportErrorMessage by remember(currentChatId) { mutableStateOf<String?>(null) }
    var webContentDir by remember(currentChatId) { mutableStateOf<File?>(null) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(currentChatId) {
        onDispose { exportJob?.cancel() }
    }
    var showCharacterSelector by remember { mutableStateOf(false) }

    var bottomBarHeightPx by remember { mutableStateOf(0) }
    val bottomBarHeightDp = with(density) { bottomBarHeightPx.toDp() }
    // Keep the composer margin stable through the IME animation. Switching from 12dp to 24dp
    // when the keyboard reports hidden caused a visible second upward jump after it had landed.
    val chatInputBottomPadding =
        if (chatViewRuntime == "floating" || !chatInputFloating) 12.dp else 24.dp
    Box(modifier = Modifier.fillMaxSize()) {
        CustomScaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            // 根据前面的逻辑条件决定是否显示配置界面
            if (showConfig) {
                ConfigurationScreen(
                        apiEndpoint = apiEndpoint,
                        apiKey = apiKey,
                        modelName = modelName,
                        onApiEndpointChange = { actualViewModel.updateApiEndpoint(it) },
                        onApiKeyChange = { actualViewModel.updateApiKey(it) },
                        onModelNameChange = { actualViewModel.updateModelName(it) },
                        onApiProviderTypeChange = { actualViewModel.updateApiProviderType(it) },
                        onSaveConfig = {
                            actualViewModel.saveApiSettings()
                            // 保存配置后导航到聊天界面
                            ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                            actualViewModel.onConfigDialogConfirmed()
                        },
                        onError = { error -> actualViewModel.showErrorMessage(error) },
                        coroutineScope = coroutineScope,
                        // 新增：使用默认配置的回调
                        onUseDefault = {
                            actualViewModel.useDefaultConfig()
                            // 确认使用默认配置后导航到聊天界面
                            ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                            actualViewModel.onConfigDialogConfirmed()
                        },
                        // 标识是否在使用默认配置
                        isUsingDefault = true, // 当显示此屏幕时，总是因为使用了默认值
                        // 添加导航到聊天界面的回调
                        onNavigateToChat = {
                            // 当用户设置了自己的配置后保存
                            actualViewModel.saveApiSettings()
                            // 确认后导航到聊天界面
                            ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                            actualViewModel.onConfigDialogConfirmed()
                        },
                        // 添加导航到Token配置页面的回调
                        onNavigateToTokenConfig = onNavigateToTokenConfig,
                        // 添加导航到Settings页面的回调
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToModelConfig = onNavigateToModelConfig
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .clipToBounds()
                ) {
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                    ) {
                        ChatScreenContent(
                                modifier = Modifier.fillMaxSize(),
                                paddingValues =
                                        PaddingValues(), // Padding is already handled by the parent Box
                                bottomInset = bottomBarHeightDp,
                                actualViewModel = actualViewModel,
                                enableMessageDialogs = !isFloatingMode,
                                showChatHistorySelector = showChatHistorySelector,
                                chatHistory = chatHistory,
                                isLoading = isLoading,
                                userMessageColor = userMessageColor,
                                aiMessageColor = aiMessageColor,
                                userTextColor = userTextColor,
                                aiTextColor = aiTextColor,
                                systemMessageColor = systemMessageColor,
                                systemTextColor = systemTextColor,
                                thinkingBackgroundColor = thinkingBackgroundColor,
                                thinkingTextColor = thinkingTextColor,
                                hasBackgroundImage = effectiveHasBackgroundImage,
                                editingMessageIndex = editingMessageIndex,
                                editingMessageContent = editingMessageContent,
                                onChatScreenGestureConsumed = onChatScreenGestureConsumedChange,
                                scrollState = scrollState,
                                autoScrollToBottom = autoScrollToBottom,
                                onAutoScrollToBottomChange = onAutoScrollToBottomChange,
                                coroutineScope = coroutineScope,
                                chatHistories = chatHistories,
                                currentChatId = currentChatId ?: "",
                                chatHeaderTransparent = chatHeaderTransparent,
                                chatHeaderHistoryIconColor = chatHeaderHistoryIconColor,
                                chatHeaderPipIconColor = chatHeaderPipIconColor,
                                chatHeaderOverlayMode = chatHeaderOverlayMode,
                                chatStyle = chatStyle, // Pass chat style
                                cursorUserBubbleLiquidGlass = cursorUserBubbleLiquidGlass,
                                cursorUserBubbleWaterGlass = cursorUserBubbleWaterGlass,
                                bubbleUserBubbleLiquidGlass = bubbleUserBubbleLiquidGlass,
                                bubbleUserBubbleWaterGlass = bubbleUserBubbleWaterGlass,
                                bubbleAiBubbleLiquidGlass = bubbleAiBubbleLiquidGlass,
                                bubbleAiBubbleWaterGlass = bubbleAiBubbleWaterGlass,
                                historyListState = historyListState,
                                showCharacterSelector = showCharacterSelector,
                                onShowCharacterSelectorChange = { showCharacterSelector = it },
                                onSwitchCharacter = onSwitchCharacter,
                                onOpenCharacterSettings = onNavigateToPersona,
                                onOpenVoiceSettings = onNavigateToVoiceSettings,
                                 onOpenCapabilities = onNavigateToCapabilities,
                                 onOpenSettings = onNavigateToSettings,
                                 onOpenModelSettings = onNavigateToModelConfig,
                                chatAreaHorizontalPadding = chatAreaHorizontalPadding,
                                bubbleUserImageStyle = bubbleUserImageStyle,
                                bubbleAiImageStyle = bubbleAiImageStyle,
                                bubbleUserRoundedCornersEnabled = bubbleUserRoundedCornersEnabled,
                                bubbleAiRoundedCornersEnabled = bubbleAiRoundedCornersEnabled,
                                bubbleUserContentPaddingLeft = bubbleUserContentPaddingLeft,
                                bubbleUserContentPaddingRight = bubbleUserContentPaddingRight,
                                bubbleAiContentPaddingLeft = bubbleAiContentPaddingLeft,
                                bubbleAiContentPaddingRight = bubbleAiContentPaddingRight,
                                showChatFloatingDotsAnimation = showChatFloatingDotsAnimation,
                        )

                    }

                    val adaptiveMetrics = com.ai.assistance.operit.ui.adaptive.LocalAdaptiveWindowMetrics.current
                    com.ai.assistance.operit.ui.features.chat.components.MiraChatEdgeFade(
                        edge = com.ai.assistance.operit.ui.features.chat.components.MiraChatFadeEdge.BOTTOM,
                        height = 128.dp,
                        hasBackgroundImage = effectiveHasBackgroundImage,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .imePadding(),
                    )
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .widthIn(max = adaptiveMetrics.chatContentMaxWidthDp.dp)
                                .onSizeChanged {
                                    bottomBarHeightPx = it.height
                                }
                    ) {
                        ChatInputBottomBar(
                                 modifier =
                                     Modifier
                                         .navigationBarsPadding()
                                         .imePadding()
                                         .padding(bottom = chatInputBottomPadding),
                                actualViewModel = actualViewModel,
                                inputStyle = inputStyle,
                                currentChatId = currentChatId,
                                inputMenuRuntime = chatViewRuntime,
                                enableEnterToSend = enableEnterToSend,
                                isLoading = isLoading,
                                inputState = inputProcessingState,
                                hasBackgroundImage = effectiveHasBackgroundImage,
                                chatInputTransparent = chatInputTransparent,
                                chatInputFloating = chatInputFloating,
                                chatInputLiquidGlass = chatInputLiquidGlass,
                                chatInputWaterGlass = chatInputWaterGlass,
                                showInputProcessingStatus = showInputProcessingStatus,
                                enableTools = enableTools,
                                isWorkspaceOpen = isWorkspaceOpen,
                                enableThinkingMode = enableThinkingMode,
                                thinkingQualityLevel = thinkingQualityLevel,
                                enableMaxContextMode = enableMaxContextMode,
                                featureStates = featureStates,
                                enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                                isAutoReadEnabled = isAutoReadEnabled,
                                disableStreamOutput = disableStreamOutput,
                                disableUserPreferenceDescription =
                                        disableUserPreferenceDescription,
                                onNavigateToUserPreferences = onNavigateToUserPreferences,
                                onNavigateToPackageManager = onNavigateToPackageManager,
                                toolPromptVisibility = toolPromptVisibility,
                                toolPromptOrder = toolPromptOrder,
                                onSaveToolOrder = { order ->
                                    actualViewModel.saveToolPromptOrder(order)
                                },
                                onNavigateToModelConfig = onNavigateToModelConfig,
                                characterCardBoundChatModelConfigId =
                                        characterCardBoundChatModelConfigId,
                                characterCardBoundChatModelIndex =
                                        characterCardBoundChatModelIndex,
                                characterCardBoundMemoryProfileId =
                                        characterCardBoundMemoryProfileId,
                                onShowMemoryFolderDialog = {
                                    showMemoryFolderDialog = true
                                },
                                onRequestAutoScrollToBottom = requestAutoScrollToBottom,
                        )
                    }

                    CharacterSelectorPanel(
                        isVisible = showCharacterSelector,
                        onDismiss = { showCharacterSelector = false },
                        onSelectCharacter = onSwitchCharacter,
                        onOpenCharacterSettings = onNavigateToModelPrompts
                    )

                    AnimatedVisibility(
                        visible = showChatHistorySelector,
                        enter = fadeIn(animationSpec = tween(300)),
                        exit = fadeOut(animationSpec = tween(300)),
                        modifier = Modifier.matchParentSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    actualViewModel.toggleChatHistorySelector()
                                }
                        )
                    }

                    AnimatedVisibility(
                        visible = showChatHistorySelector,
                        enter = androidx.compose.animation.slideInHorizontally(
                            initialOffsetX = { -it },
                            animationSpec = tween(300)
                        ),
                        exit = androidx.compose.animation.slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = tween(300)
                        ),
                        modifier = Modifier.matchParentSize()
                    ) {
                        val chatHistorySearchQuery by actualViewModel.chatHistorySearchQuery.collectAsState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.align(Alignment.TopStart)) {
                                ChatHistorySelectorPanel(
                                    actualViewModel = actualViewModel,
                                    chatHistories = displayedChatHistories,
                                    currentChatId = currentChatId ?: "",
                                    showChatHistorySelector = showChatHistorySelector,
                                    historyListState = historyListState,
                                    onChatScreenGestureConsumed = onChatScreenGestureConsumedChange,
                                    searchQuery = chatHistorySearchQuery,
                                    onSearchQueryChange = actualViewModel::onChatHistorySearchQueryChange,
                                    activePrompt = activePrompt,
                                    historyDisplayMode = historyDisplayMode,
                                    onDisplayModeChange = { historyDisplayMode = it },
                                    autoSwitchCharacterCard = autoSwitchCharacterCard,
                                    onAutoSwitchCharacterCardChange = { autoSwitchCharacterCard = it },
                                    autoSwitchChatOnCharacterSelect = autoSwitchChatOnCharacterSelect,
                                    onAutoSwitchChatOnCharacterSelectChange = {
                                        autoSwitchChatOnCharacterSelect = it
                                    },
                                    onOpenMemory = onNavigateToMemory,
                                    onOpenSettings = onNavigateToSettings,
                                )
                            }
                        }
                    }
                }
            }
        }

        MentionSuggestionOverlay(
            actualViewModel = actualViewModel,
            bottomBarHeightPx = bottomBarHeightPx,
            panelStyle =
                MentionSuggestionPanelStyle(
                    hasBackgroundImage = effectiveHasBackgroundImage,
                    chatInputTransparent = chatInputTransparent,
                    chatInputFloating = chatInputFloating,
                    chatInputLiquidGlass = chatInputLiquidGlass,
                    chatInputWaterGlass = chatInputWaterGlass,
                ),
        )

        val workspaceOverlayModifier =
            if (showWebView) {
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
            } else {
                Modifier
                    .size(0.dp)
                    .clearAndSetSemantics {}
            }

        // Web开发模式作为浮层，现在位于Scaffold外部，可以覆盖整个屏幕
        Layout(
            modifier = workspaceOverlayModifier,
            content = {
                // The content is composed unconditionally, keeping it "alive"
                if (hasEverShownWebView && currentChatView != null) {
                    WorkspaceScreen(
                        actualViewModel = actualViewModel,
                        currentChat = currentChatView,
                        isVisible = showWebView, // Pass visibility state
                        onExportClick = { workDir ->
                            webContentDir = workDir
                            AppLogger.d(
                                "AIChatScreen",
                                "正在导出工作区: ${workDir.absolutePath}, 聊天ID: $currentChatId"
                            )
                            showExportPlatformDialog = true
                        },
                        onClose = actualViewModel::onWorkspaceButtonClick,
                    )
                }
            }
        ) { measurables, constraints ->
            if (measurables.isEmpty()) {
                layout(0, 0) {}
            } else {
                if (showWebView) {
                    val placeable = measurables.first().measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(0, 0)
                    }
                } else {
                    // 当不可见时，我们让布局大小为0，并且完全不测量或放置子项。
                    // 这可以跳过子项昂贵的测量/布局过程，从而解决性能问题，
                    // 同时由于它仍在组合中，因此可以保持其状态。
                    layout(0, 0) {}
                }
            }
        }

        // AI电脑模式作为浮层：关闭时完全移出组合，确保 SurfaceView 被释放，避免机型相关残影
        if (showAiComputer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                ComputerScreen(onClose = actualViewModel::onAiComputerButtonClick)
            }
        }

        AnimatedVisibility(
            visible = isWorkspacePreparing,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(120))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                contentAlignment = Alignment.Center
            ) {
                ElevatedCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.workspace_opening),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.workspace_opening_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 导出平台选择对话框
        if (showExportPlatformDialog) {
            ExportPlatformDialog(
                    onDismiss = { showExportPlatformDialog = false },
                    onSelectAndroid = {
                        showExportPlatformDialog = false
                        showAndroidExportDialog = true
                    },
                    onSelectWindows = {
                        showExportPlatformDialog = false
                        showWindowsExportDialog = true
                    }
            )
        }

        // Android导出设置对话框
        if (showAndroidExportDialog && webContentDir != null) {
            AndroidExportDialog(
                    workDir = webContentDir!!,
                    onDismiss = { showAndroidExportDialog = false },
                    onExport = { packageName, appName, iconUri, versionName, versionCode ->
                        showAndroidExportDialog = false
                        showExportProgressDialog = true
                        exportProgress = 0f
                        exportStatus = context.getString(R.string.export_starting)

                        // 启动导出过程
                        exportJob?.cancel()
                        exportJob = coroutineScope.launch {
                            exportAndroidApp(
                                    context = context,
                                    packageName = packageName,
                                    appName = appName,
                                    versionName = versionName,
                                    versionCode = versionCode,
                                    iconUri = iconUri,
                                    webContentDir = webContentDir!!,
                                    onProgress = { progress, status ->
                                        exportProgress = progress
                                        exportStatus = status
                                    },
                                    onComplete = { success, filePath, errorMessage ->
                                        exportJob = null
                                        showExportProgressDialog = false
                                        exportSuccess = success
                                        exportFilePath = filePath
                                        exportErrorMessage = errorMessage
                                        showExportCompleteDialog = true
                                    }
                            )
                        }
                    }
            )
        }

        // Windows导出设置对话框
        if (showWindowsExportDialog && webContentDir != null) {
            WindowsExportDialog(
                    workDir = webContentDir!!,
                    onDismiss = { showWindowsExportDialog = false },
                    onExport = { appName, iconUri ->
                        showWindowsExportDialog = false
                        showExportProgressDialog = true
                        exportProgress = 0f
                        exportStatus = context.getString(R.string.export_starting)

                        // 启动导出过程
                        exportJob?.cancel()
                        exportJob = coroutineScope.launch {
                            exportWindowsApp(
                                    context = context,
                                    appName = appName,
                                    iconUri = iconUri,
                                    webContentDir = webContentDir!!,
                                    onProgress = { progress, status ->
                                        exportProgress = progress
                                        exportStatus = status
                                    },
                                    onComplete = { success, filePath, errorMessage ->
                                        exportJob = null
                                        showExportProgressDialog = false
                                        exportSuccess = success
                                        exportFilePath = filePath
                                        exportErrorMessage = errorMessage
                                        showExportCompleteDialog = true
                                    }
                            )
                        }
                    }
            )
        }

        // 导出进度对话框
        if (showExportProgressDialog) {
            ExportProgressDialog(
                    progress = exportProgress,
                    status = exportStatus,
                    onCancel = {
                        exportJob?.cancel()
                        exportJob = null
                        showExportProgressDialog = false
                    }
            )
        }

        // 导出完成对话框
        if (showExportCompleteDialog) {
            ExportCompleteDialog(
                    success = exportSuccess,
                    filePath = exportFilePath,
                    errorMessage = exportErrorMessage,
                    onDismiss = { showExportCompleteDialog = false },
                    onOpenFile = { path ->
                        val tool = AITool(
                            name = if (path.endsWith(".apk", ignoreCase = true)) "install_app" else "open_file",
                            parameters = listOf(ToolParameter("path", path))
                        )
                        AIToolHandler.getInstance(context).executeTool(tool)
                    }
            )
        }

        ChatToastHost(
            message = toastEvent,
            onDismiss = { actualViewModel.clearToastEvent() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(
                    start = 16.dp,
                    top = padding.calculateTopPadding() + 50.dp,
                    end = 16.dp
                ),
            maxHeight = 280.dp
        )
    }

    // Show popup message dialog when needed
    popupMessage?.let { message ->
        AlertDialog(
                onDismissRequest = { actualViewModel.clearPopupMessage() },
                title = { Text(stringResource(R.string.dialog_title_prompt)) },
                text = { Text(message ?: "") },
                confirmButton = {
                    TextButton(onClick = { actualViewModel.clearPopupMessage() }) { Text(stringResource(R.string.ok)) }
                }
        )
    }

    // Check for overlay permission on resume
    LaunchedEffect(Unit) {
        canDrawOverlays.value = Settings.canDrawOverlays(context)

        // If floating mode is on but no permission, turn it off
        if (isFloatingMode && !canDrawOverlays.value) {
            actualViewModel.toggleFloatingMode()
            Toast.makeText(
                            context,
                            context.getString(R.string.floating_window_permission_denied),
                            Toast.LENGTH_SHORT
                    )
                    .show()
        }
    }
    
    // 记忆文件夹选择对话框
    MemoryFolderSelectionDialog(
        visible = showMemoryFolderDialog,
        onDismiss = { showMemoryFolderDialog = false },
        onConfirm = { selectedFolders ->
            actualViewModel.captureMemoryFolders(selectedFolders)
        }
    )
}

@Composable
private fun ChatInputBottomBar(
    modifier: Modifier = Modifier,
    actualViewModel: ChatViewModel,
    inputStyle: String,
    currentChatId: String?,
    inputMenuRuntime: String,
    enableEnterToSend: Boolean,
    isLoading: Boolean,
    inputState: InputProcessingState,
    hasBackgroundImage: Boolean,
    chatInputTransparent: Boolean,
    chatInputFloating: Boolean,
    chatInputLiquidGlass: Boolean,
    chatInputWaterGlass: Boolean,
    showInputProcessingStatus: Boolean,
    enableTools: Boolean,
    isWorkspaceOpen: Boolean,
    enableThinkingMode: Boolean,
    thinkingQualityLevel: Int,
    enableMaxContextMode: Boolean,
    featureStates: Map<String, Boolean>,
    enableMemoryAutoUpdate: Boolean,
    isAutoReadEnabled: Boolean,
    disableStreamOutput: Boolean,
    disableUserPreferenceDescription: Boolean,
    onNavigateToUserPreferences: () -> Unit,
    onNavigateToPackageManager: () -> Unit,
    toolPromptVisibility: Map<String, Boolean>,
    toolPromptOrder: List<String> = emptyList(),
    onSaveToolOrder: (List<String>) -> Unit = {},
    onNavigateToModelConfig: () -> Unit,
    characterCardBoundChatModelConfigId: String?,
    characterCardBoundChatModelIndex: Int,
    characterCardBoundMemoryProfileId: String?,
    onShowMemoryFolderDialog: () -> Unit,
    onRequestAutoScrollToBottom: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val waifuPreferences = remember(context) { WaifuPreferences.getInstance(context) }
    val reminderScheduler = remember(context) { CompanionReminderScheduler.getInstance(context) }
    val reminderPreferences = remember(context) { CompanionReminderPreferences.getInstance(context) }
    val activePromptManager = remember(context) { ActivePromptManager.getInstance(context) }

    suspend fun enableExplicitRemindersForActiveTarget() {
        val target =
            when (val prompt = activePromptManager.getActivePrompt()) {
                is ActivePrompt.CharacterCard ->
                    CompanionReminderTarget(CompanionReminderTargetType.CHARACTER, prompt.id)
                is ActivePrompt.CharacterGroup ->
                    CompanionReminderTarget(CompanionReminderTargetType.GROUP, prompt.id)
            }
        val settings = reminderPreferences.getSettings(target)
        if (!settings.enabled) {
            reminderPreferences.saveIntensity(target, CompanionReminderIntensity.EXPLICIT_ONLY)
            reminderPreferences.saveEnabled(target, true)
        }
        reminderScheduler.syncAllProfiles()
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                coroutineScope.launch { enableExplicitRemindersForActiveTarget() }
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.notification_permission_denied),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    fun requestReminderNotificationPermissionIfNeeded(text: String) {
        if (!CompanionReminderIntentDetector.isExplicitReminder(text)) return
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        ) {
            coroutineScope.launch { enableExplicitRemindersForActiveTarget() }
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val userMessage by actualViewModel.userMessage.collectAsState()
    val attachments by actualViewModel.attachments.collectAsState()
    val attachmentPanelState by actualViewModel.attachmentPanelState.collectAsState()
    val replyToMessage by actualViewModel.replyToMessage.collectAsState()
    val permissionLevel by actualViewModel.masterPermissionLevel.collectAsState()
    val isSummarizing by actualViewModel.isSummarizing.collectAsState()
    val isSendTriggeredSummarizing by actualViewModel.isSendTriggeredSummarizing.collectAsState()
    val pendingQueueMessagesByChatId by
        actualViewModel.pendingQueueMessagesByChatId.collectAsState()
    val isWaifuModeEnabled by waifuPreferences.enableWaifuModeFlow.collectAsState(initial = false)
    val isWaifuMergeSendEnabled by
        waifuPreferences.waifuEnableMergeSendFlow.collectAsState(initial = false)
    val waifuMergeSendDelayMs by
        waifuPreferences.waifuMergeSendDelayMsFlow.collectAsState(
            initial = WaifuPreferences.DEFAULT_WAIFU_MERGE_SEND_DELAY_MS
        )

    val isMessageProcessing =
        isLoading ||
            inputState is InputProcessingState.Connecting ||
            inputState is InputProcessingState.ExecutingTool ||
            inputState is InputProcessingState.ToolProgress ||
            inputState is InputProcessingState.Processing ||
            inputState is InputProcessingState.ProcessingToolResult ||
            inputState is InputProcessingState.Summarizing ||
            inputState is InputProcessingState.Receiving
    val isQueueBlocked = isMessageProcessing || isSummarizing || isSendTriggeredSummarizing

    val pendingQueueMessages =
        currentChatId?.let { pendingQueueMessagesByChatId[it] }.orEmpty()
    var isPendingQueueExpanded by rememberSaveable(currentChatId) { mutableStateOf(true) }
    var wasQueueBlocked by remember(currentChatId) { mutableStateOf(false) }
    var suppressNextAutoDequeue by remember(currentChatId) { mutableStateOf(false) }
    val latestQueueBlocked = rememberUpdatedState(isQueueBlocked)

    fun buildChatInputHookContext(
        eventName: String,
        targetChatId: String? = currentChatId,
        text: String = userMessage.text,
        selectionStart: Int = userMessage.selection.start,
        selectionEnd: Int = userMessage.selection.end,
        source: String = inputStyle,
        submitSource: String = ""
    ): ChatInputHookContext {
        val normalizedSelectionStart = selectionStart.coerceIn(0, text.length)
        val normalizedSelectionEnd = selectionEnd.coerceIn(0, text.length)
        return ChatInputHookContext(
            context = context,
            eventName = eventName,
            chatId = targetChatId,
            text = text,
            selectionStart = normalizedSelectionStart,
            selectionEnd = normalizedSelectionEnd,
            hasAttachments = attachments.isNotEmpty(),
            attachmentCount = attachments.size,
            isProcessing = isMessageProcessing,
            inputStyle = inputStyle,
            source = source,
            submitSource = submitSource
        )
    }

    fun handleUserMessageChange(value: TextFieldValue) {
        actualViewModel.updateUserMessage(value)
        ChatInputHookRegistry.dispatchNotification(
            buildChatInputHookContext(
                eventName = ChatInputEvents.INPUT_CHANGED,
                text = value.text,
                selectionStart = value.selection.start,
                selectionEnd = value.selection.end
            )
        )
    }

    fun showChatInputHookMessage(message: String?) {
        val normalizedMessage = message?.trim().orEmpty()
        if (normalizedMessage.isNotBlank()) {
            Toast.makeText(context, normalizedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    fun restorePendingQueueItem(item: PendingQueueMessageItem) {
        actualViewModel.restorePendingQueueMessage(item)
    }

    fun removePendingQueueMessageById(id: Long): PendingQueueMessageItem? {
        val chatId = currentChatId ?: return null
        return actualViewModel.removePendingQueueMessage(chatId, id)
    }

    val sendQueuedItemNow: (PendingQueueMessageItem, Boolean) -> Unit =
        { item, cancelCurrentConversation ->
            coroutineScope.launch {
                val targetChatId = item.chatId
                val submitDecision =
                    ChatInputHookRegistry.dispatchSubmitRequested(
                        buildChatInputHookContext(
                            eventName = ChatInputEvents.SUBMIT_REQUESTED,
                            targetChatId = targetChatId,
                            text = item.text,
                            selectionStart = item.text.length,
                            selectionEnd = item.text.length,
                            source = "queue",
                            submitSource = "queue"
                        )
                    )
                when (submitDecision.action) {
                    ChatInputSubmitActions.BLOCK -> {
                        restorePendingQueueItem(item)
                        showChatInputHookMessage(submitDecision.message)
                        return@launch
                    }
                    ChatInputSubmitActions.CONSUME -> {
                        showChatInputHookMessage(submitDecision.message)
                        return@launch
                    }
                }
                val finalText = submitDecision.text ?: item.text
                val shouldWaitForCancel =
                    cancelCurrentConversation && actualViewModel.isChatLoading(targetChatId)
                if (shouldWaitForCancel) {
                    suppressNextAutoDequeue = true
                }
                if (cancelCurrentConversation) {
                    actualViewModel.cancelMessage(targetChatId)
                }
                if (shouldWaitForCancel) {
                    val cancelled =
                        withTimeoutOrNull(10_000L) {
                            while (actualViewModel.isChatLoading(targetChatId)) {
                                delay(50L)
                            }
                            true
                        } == true
                    if (!cancelled) {
                        restorePendingQueueItem(item)
                        actualViewModel.showToast(context.getString(R.string.mira_send_state_stuck))
                        return@launch
                    }
                }

                focusManager.clearFocus()
                requestReminderNotificationPermissionIfNeeded(finalText)
                val accepted =
                    actualViewModel.sendTextMessage(
                        text = finalText,
                        chatIdOverride = targetChatId,
                    )
                if (!accepted) {
                    restorePendingQueueItem(item)
                    return@launch
                }
                onRequestAutoScrollToBottom()
                ChatInputHookRegistry.dispatchNotification(
                    buildChatInputHookContext(
                        eventName = ChatInputEvents.SUBMITTED,
                        targetChatId = targetChatId,
                        text = finalText,
                        selectionStart = finalText.length,
                        selectionEnd = finalText.length,
                        source = "queue",
                        submitSource = "queue"
                    )
                )
            }
        }

    LaunchedEffect(isQueueBlocked, pendingQueueMessages.size, currentChatId) {
        if (wasQueueBlocked && !isQueueBlocked) {
            if (suppressNextAutoDequeue) {
                suppressNextAutoDequeue = false
            } else if (pendingQueueMessages.isNotEmpty()) {
                delay(250)
                if (!latestQueueBlocked.value && pendingQueueMessages.isNotEmpty()) {
                    val nextMessage = pendingQueueMessages.first()
                    actualViewModel.removePendingQueueMessage(nextMessage.chatId, nextMessage.id)
                    sendQueuedItemNow(nextMessage, false)
                }
            }
        }
        wasQueueBlocked = isQueueBlocked
    }

    fun enqueueDraftToPendingQueue() {
        val draftText = userMessage.text.trim()
        if (draftText.isBlank()) return
        val chatId = currentChatId ?: return

        actualViewModel.enqueuePendingQueueMessage(chatId, draftText)
        isPendingQueueExpanded = true
        actualViewModel.updateUserMessage(TextFieldValue(""))
        actualViewModel.showToast(context.getString(R.string.chat_queue_added))
    }

    val sendMessage: (String?) -> Unit = { suggestedText ->
        val requestedText = suggestedText?.trim()?.takeIf { it.isNotEmpty() }
        coroutineScope.launch {
            val submittedText = requestedText ?: userMessage.text
            val submitDecision =
                ChatInputHookRegistry.dispatchSubmitRequested(
                    buildChatInputHookContext(
                        eventName = ChatInputEvents.SUBMIT_REQUESTED,
                        text = submittedText,
                        selectionStart = submittedText.length,
                        selectionEnd = submittedText.length,
                        submitSource = "send"
                    )
                )
            when (submitDecision.action) {
                ChatInputSubmitActions.BLOCK -> {
                    showChatInputHookMessage(submitDecision.message)
                    return@launch
                }
                ChatInputSubmitActions.CONSUME -> {
                    if (submitDecision.clearInput) {
                        actualViewModel.updateUserMessage(TextFieldValue(""))
                        actualViewModel.resetAttachmentPanelState()
                    }
                    showChatInputHookMessage(submitDecision.message)
                    return@launch
                }
            }

            val finalText = submitDecision.text ?: submittedText
            if (finalText != actualViewModel.userMessage.value.text) {
                actualViewModel.updateUserMessage(
                    TextFieldValue(
                        text = finalText,
                        selection = TextRange(finalText.length)
                    )
                )
            }
            val shouldUseWaifuMergeSend =
                isWaifuModeEnabled &&
                    isWaifuMergeSendEnabled &&
                    !currentChatId.isNullOrBlank() &&
                    attachments.isEmpty() &&
                    replyToMessage == null &&
                    finalText.isNotBlank()
            if (shouldUseWaifuMergeSend) {
                val visibleText = finalText.trim()
                val targetChatId = currentChatId ?: return@launch
                val visibleMessageTimestamp =
                    actualViewModel.addVisibleUserMessageToChat(targetChatId, visibleText)
                        ?: return@launch
                requestReminderNotificationPermissionIfNeeded(visibleText)
                actualViewModel.enqueueWaifuMergedMessage(
                    chatId = targetChatId,
                    text = visibleText,
                    visibleMessageTimestamp = visibleMessageTimestamp,
                    delayMs = waifuMergeSendDelayMs.toLong(),
                )
                ChatInputHookRegistry.dispatchNotification(
                    buildChatInputHookContext(
                        eventName = ChatInputEvents.SUBMITTED,
                        text = visibleText,
                        selectionStart = visibleText.length,
                        selectionEnd = visibleText.length,
                        source = "waifu_merge",
                        submitSource = "waifu_merge",
                    )
                )
                actualViewModel.updateUserMessage(TextFieldValue(""))
                actualViewModel.resetAttachmentPanelState()
                focusManager.clearFocus()
                onRequestAutoScrollToBottom()
                return@launch
            }
            val dispatchResolved = CompletableDeferred<Boolean>()
            val reserved =
                actualViewModel.sendUserMessage { accepted ->
                    dispatchResolved.complete(accepted)
                }
            if (!reserved) return@launch
            val accepted =
                withTimeoutOrNull(15_000L) { dispatchResolved.await() }
                    ?: run {
                        actualViewModel.showToast(context.getString(R.string.mira_send_state_stuck))
                        return@launch
                    }
            if (!accepted) return@launch
            focusManager.clearFocus()
            requestReminderNotificationPermissionIfNeeded(finalText)
            actualViewModel.resetAttachmentPanelState()
            onRequestAutoScrollToBottom()
            ChatInputHookRegistry.dispatchNotification(
                buildChatInputHookContext(
                    eventName = ChatInputEvents.SUBMITTED,
                    text = finalText,
                    selectionStart = finalText.length,
                    selectionEnd = finalText.length,
                    submitSource = "send"
                )
            )
        }
    }

    if (inputStyle == UserPreferencesManager.INPUT_STYLE_AGENT) {
        AgentChatInputSection(
                modifier = modifier,
                actualViewModel = actualViewModel,
                userMessage = userMessage,
                onUserMessageChange = { value -> handleUserMessageChange(value) },
                enableEnterToSend = enableEnterToSend,
                onSendMessage = { sendMessage(null) },
                onSendSuggestedMessage = { suggestion -> sendMessage(suggestion) },
                onQueueMessage = { enqueueDraftToPendingQueue() },
                onCancelMessage = actualViewModel::cancelCurrentMessage,
                isLoading = isLoading,
                inputState = inputState,
                allowTextInputWhileProcessing = true,
                onAttachmentRequest = actualViewModel::handleAttachment,
                attachments = attachments,
                onRemoveAttachment = actualViewModel::removeAttachment,
                onInsertAttachment = actualViewModel::insertAttachmentReference,
                onAttachScreenContent = actualViewModel::captureScreenContent,
                onAttachNotifications = actualViewModel::captureNotifications,
                onAttachLocation = actualViewModel::captureLocation,
                onAttachMemory = onShowMemoryFolderDialog,
                onAttachPackage = actualViewModel::attachPackage,
                onTakePhoto = actualViewModel::handleTakenPhoto,
                hasBackgroundImage = hasBackgroundImage,
                chatInputTransparent = chatInputTransparent,
                chatInputFloating = chatInputFloating,
                chatInputLiquidGlass = chatInputLiquidGlass,
                chatInputWaterGlass = chatInputWaterGlass,
                externalAttachmentPanelState = attachmentPanelState,
                onAttachmentPanelStateChange = actualViewModel::updateAttachmentPanelState,
                showInputProcessingStatus = showInputProcessingStatus,
                 enableTools = enableTools,
                 replyToMessage = replyToMessage,
                onClearReply = actualViewModel::clearReplyToMessage,
                isWorkspaceOpen = isWorkspaceOpen,
                enableThinkingMode = enableThinkingMode,
                onToggleThinkingMode = actualViewModel::toggleThinkingMode,
                thinkingQualityLevel = thinkingQualityLevel,
                onThinkingQualityLevelChange = actualViewModel::updateThinkingQualityLevel,
                enableMaxContextMode = enableMaxContextMode,
                onToggleEnableMaxContextMode = actualViewModel::toggleEnableMaxContextMode,
                currentChatId = currentChatId,
                featureStates = featureStates,
                onToggleFeature = actualViewModel::toggleFeature,
                inputMenuRuntime = inputMenuRuntime,
                permissionLevel = permissionLevel,
                onSetPermissionLevel = actualViewModel::setMasterPermissionLevel,
                enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                onToggleMemoryAutoUpdate = actualViewModel::toggleMemoryAutoUpdate,
                isAutoReadEnabled = isAutoReadEnabled,
                onToggleAutoRead = actualViewModel::toggleAutoRead,
                onToggleTools = actualViewModel::toggleTools,
                disableStreamOutput = disableStreamOutput,
                onToggleDisableStreamOutput = actualViewModel::toggleDisableStreamOutput,
                disableUserPreferenceDescription = disableUserPreferenceDescription,
                onToggleDisableUserPreferenceDescription =
                    actualViewModel::toggleDisableUserPreferenceDescription,
                onNavigateToUserPreferences = onNavigateToUserPreferences,
                onNavigateToPackageManager = onNavigateToPackageManager,
                toolPromptVisibility = toolPromptVisibility,
                onSaveToolPromptVisibilityMap = actualViewModel::saveToolPromptVisibilityMap,
                toolOrder = toolPromptOrder,
                onSaveToolOrder = actualViewModel::saveToolPromptOrder,
                onManualMemoryUpdate = actualViewModel::manuallyUpdateMemory,
                onNavigateToModelConfig = onNavigateToModelConfig,
                characterCardBoundChatModelConfigId = characterCardBoundChatModelConfigId,
                characterCardBoundChatModelIndex = characterCardBoundChatModelIndex,
                characterCardBoundMemoryProfileId = characterCardBoundMemoryProfileId,
                pendingQueueMessages = pendingQueueMessages,
                isPendingQueueExpanded = isPendingQueueExpanded,
                onPendingQueueExpandedChange = { isPendingQueueExpanded = it },
                onDeletePendingQueueMessage = { id ->
                    removePendingQueueMessageById(id)
                },
                onEditPendingQueueMessage = { id ->
                    removePendingQueueMessageById(id)?.let { queueItem ->
                        val text = queueItem.text
                        actualViewModel.updateUserMessage(
                            TextFieldValue(
                                text = text,
                                selection = TextRange(text.length),
                            ),
                        )
                    }
                },
                onSendPendingQueueMessage = { id ->
                    removePendingQueueMessageById(id)?.let { queueItem ->
                        sendQueuedItemNow(queueItem, true)
                    }
                },
        )
    } else {
        ClassicChatInputSection(
                modifier = modifier,
                actualViewModel = actualViewModel,
                userMessage = userMessage,
                onUserMessageChange = { value -> handleUserMessageChange(value) },
                enableEnterToSend = enableEnterToSend,
                onSendMessage = { sendMessage(null) },
                onSendSuggestedMessage = { suggestion -> sendMessage(suggestion) },
                onQueueMessage = { enqueueDraftToPendingQueue() },
                onCancelMessage = actualViewModel::cancelCurrentMessage,
                isLoading = isLoading,
                inputState = inputState,
                allowTextInputWhileProcessing = true,
                onAttachmentRequest = actualViewModel::handleAttachment,
                attachments = attachments,
                onRemoveAttachment = actualViewModel::removeAttachment,
                onInsertAttachment = actualViewModel::insertAttachmentReference,
                onAttachScreenContent = actualViewModel::captureScreenContent,
                onAttachNotifications = actualViewModel::captureNotifications,
                onAttachLocation = actualViewModel::captureLocation,
                onAttachMemory = onShowMemoryFolderDialog,
                onAttachPackage = actualViewModel::attachPackage,
                onTakePhoto = actualViewModel::handleTakenPhoto,
                hasBackgroundImage = hasBackgroundImage,
                chatInputTransparent = chatInputTransparent,
                chatInputFloating = chatInputFloating,
                chatInputLiquidGlass = chatInputLiquidGlass,
                chatInputWaterGlass = chatInputWaterGlass,
                externalAttachmentPanelState = attachmentPanelState,
                onAttachmentPanelStateChange = actualViewModel::updateAttachmentPanelState,
                showInputProcessingStatus = showInputProcessingStatus,
                enableTools = enableTools,
                onToggleTools = actualViewModel::toggleTools,
                onNavigateToModelConfig = onNavigateToModelConfig,
                replyToMessage = replyToMessage,
                onClearReply = actualViewModel::clearReplyToMessage,
                isWorkspaceOpen = isWorkspaceOpen,
                pendingQueueMessages = pendingQueueMessages,
                isPendingQueueExpanded = isPendingQueueExpanded,
                onPendingQueueExpandedChange = { isPendingQueueExpanded = it },
                onDeletePendingQueueMessage = { id ->
                    removePendingQueueMessageById(id)
                },
                onEditPendingQueueMessage = { id ->
                    removePendingQueueMessageById(id)?.let { queueItem ->
                        val text = queueItem.text
                        actualViewModel.updateUserMessage(
                            TextFieldValue(
                                text = text,
                                selection = TextRange(text.length),
                            ),
                        )
                    }
                },
                onSendPendingQueueMessage = { id ->
                    removePendingQueueMessageById(id)?.let { queueItem ->
                        sendQueuedItemNow(queueItem, true)
                    }
                },
        )
    }
}

@Composable
private fun MentionSuggestionOverlay(
    actualViewModel: ChatViewModel,
    bottomBarHeightPx: Int,
    panelStyle: MentionSuggestionPanelStyle,
) {
    val density = LocalDensity.current
    val showMentionSuggestionPanel by actualViewModel.showMentionSuggestionPanel.collectAsState()
    val bottomPaddingForSelector = with(density) { bottomBarHeightPx.toDp() }

    AnimatedVisibility(
        visible = showMentionSuggestionPanel,
        enter = fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = fadeOut(animationSpec = tween(durationMillis = 150)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = actualViewModel::hideMentionSuggestionPanel,
                        ),
            )
            MentionSuggestionPanel(
                modifier =
                    Modifier
                        .padding(start = 12.dp, end = 12.dp, bottom = bottomPaddingForSelector + 8.dp)
                        .animateEnterExit(
                            enter = slideInVertically(
                                animationSpec = tween(durationMillis = 240),
                            ) { it / 4 },
                            exit = slideOutVertically(
                                animationSpec = tween(durationMillis = 200),
                            ) { it / 4 },
                        ),
                viewModel = actualViewModel,
                panelStyle = panelStyle,
                onFileSelected = { relativePath ->
                    actualViewModel.selectMentionWorkspaceEntry(relativePath)
                },
                onPackageSelected = actualViewModel::selectMentionPackage,
            )
        }
    }
}
