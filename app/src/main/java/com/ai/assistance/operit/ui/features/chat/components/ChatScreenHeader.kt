package com.ai.assistance.operit.ui.features.chat.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import com.ai.assistance.operit.ui.adaptive.LocalAdaptiveWindowMetrics

@Composable
fun useFloatingWindowLauncher(
    actualViewModel: ChatViewModel,
    permissionLauncher: ActivityResultLauncher<String>,
): () -> Unit {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    return {
        actualViewModel.onFloatingButtonClick(
            FloatingMode.WINDOW,
            permissionLauncher,
            colorScheme,
            typography,
            moveTaskToBackOnReady = true,
        )
    }
}

@Composable
fun ChatScreenHeader(
    modifier: Modifier = Modifier,
    actualViewModel: ChatViewModel,
    showChatHistorySelector: Boolean,
    chatHeaderTransparent: Boolean,
    chatHeaderHistoryIconColor: Int?,
    chatHeaderPipIconColor: Int?,
    onCharacterSwitcherClick: () -> Unit,
    onOpenCharacterSettings: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onOpenCapabilities: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelSettings: () -> Unit,
) {
    val context = LocalContext.current
    val adaptiveMetrics = LocalAdaptiveWindowMetrics.current
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    LaunchedEffect(actualViewModel, context) {
        actualViewModel.moveTaskToBackEvents.collect {
            (context as? android.app.Activity)?.moveTaskToBack(true)
        }
    }

    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val characterGroupCardManager = remember { CharacterGroupCardManager.getInstance(context) }
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val userPreferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val activePrompt by
        activePromptManager.activePromptFlow.collectAsState(
            initial = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID),
        )
    val activeCharacterCard by
        remember(activePrompt) {
            when (val prompt = activePrompt) {
                is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCardFlow(prompt.id)
                is ActivePrompt.CharacterGroup -> flowOf(null)
            }
        }.collectAsState(initial = null)
    val activeCharacterGroup by
        remember(activePrompt) {
            when (val prompt = activePrompt) {
                is ActivePrompt.CharacterGroup ->
                    characterGroupCardManager.getCharacterGroupCardFlow(prompt.id)
                is ActivePrompt.CharacterCard -> flowOf(null)
            }
        }.collectAsState(initial = null)
    val activeCardAvatarUri by
        remember(activeCharacterCard?.id) {
            activeCharacterCard?.id?.let {
                userPreferencesManager.getAiAvatarForCharacterCardFlow(it)
            } ?: flowOf(null)
        }.collectAsState(initial = null)
    val activeGroupAvatarUri by
        remember(activeCharacterGroup?.id) {
            activeCharacterGroup?.id?.let {
                userPreferencesManager.getAiAvatarForCharacterGroupFlow(it)
            } ?: flowOf(null)
        }.collectAsState(initial = null)
    val activeGroupFallbackMemberCardId =
        remember(activeCharacterGroup?.members) {
            activeCharacterGroup?.members
                ?.sortedBy { it.orderIndex }
                .orEmpty()
                .firstOrNull()
                ?.characterCardId
        }
    val activeGroupFallbackMemberAvatarUri by
        remember(activeGroupFallbackMemberCardId) {
            activeGroupFallbackMemberCardId?.let {
                userPreferencesManager.getAiAvatarForCharacterCardFlow(it)
            } ?: flowOf(null)
        }.collectAsState(initial = null)
    val activeCharacterAvatarUri =
        when (activePrompt) {
            is ActivePrompt.CharacterGroup -> activeGroupAvatarUri ?: activeGroupFallbackMemberAvatarUri
            is ActivePrompt.CharacterCard -> activeCardAvatarUri
        }

    val isFloatingMode by actualViewModel.isFloatingMode.collectAsState()
    val isAutoReadEnabled by actualViewModel.isAutoReadEnabled.collectAsState()
    val chatHistories by actualViewModel.chatHistories.collectAsState()
    val currentChatId by actualViewModel.currentChatId.collectAsState()
    val userMessage by actualViewModel.userMessage.collectAsState()
    val currentContextTokens by actualViewModel.currentWindowSize.collectAsState()
    val maxContextWindowInK by actualViewModel.maxWindowSizeInK.collectAsState()
    val contextWindowUsage by actualViewModel.contextWindowUsage.collectAsState()
    val currentChat = chatHistories.firstOrNull { it.id == currentChatId }
    val effectiveAutoReadEnabled = currentChat?.autoReadOverride ?: isAutoReadEnabled

    // 浮窗与语音通话必须保留各自的授权后动作，否则首次授权会启动错误模式。
    val windowPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                actualViewModel.launchWindowFloatingModeAfterMicPermissionGranted(
                    colorScheme = colorScheme,
                    typography = typography,
                    moveTaskToBackOnReady = true,
                )
            } else {
                actualViewModel.showToast(context.getString(R.string.microphone_permission_denied))
            }
        }
    val launchFloatingWindow = useFloatingWindowLauncher(actualViewModel, windowPermissionLauncher)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                // The service state only controls the toolbar action; this header still belongs to the Activity.
                .statusBarsPadding()
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        ChatHeader(
            showChatHistorySelector = showChatHistorySelector,
            onToggleChatHistorySelector = { actualViewModel.toggleChatHistorySelector() },
            modifier =
                Modifier
                    .widthIn(max = adaptiveMetrics.chatContentMaxWidthDp.dp)
                    .fillMaxWidth(),
            isFloatingMode = isFloatingMode,
            onLaunchFloatingWindow = launchFloatingWindow,
            activeCharacterName = activeCharacterGroup?.name ?: activeCharacterCard?.name ?: "",
            activeCharacterAvatarUri = activeCharacterAvatarUri,
            onCharacterClick = onCharacterSwitcherClick,
            onCreateNewChat = { actualViewModel.createSessionChat() },
            onCreateTemporaryChat = { actualViewModel.createSessionChat(isTemporary = true) },
            onExportChat = actualViewModel::exportCurrentChat,
            onArchiveChat = {
                currentChatId?.let { chatId ->
                    actualViewModel.updateChatArchived(chatId, !(currentChat?.archived ?: false))
                }
            },
            onDeleteChat = {
                currentChatId?.let(actualViewModel::deleteChatHistory)
            },
            isArchived = currentChat?.archived == true,
            hasDraft = userMessage.text.isNotBlank(),
            autoReadEnabled = effectiveAutoReadEnabled,
            onAutoReadEnabledChange = actualViewModel::updateCurrentChatAutoReadOverride,
            currentContextTokens = currentContextTokens,
            maxContextTokens = (maxContextWindowInK * 1024).toLong().coerceAtLeast(0L),
            contextWindowUsage = contextWindowUsage,
            onRequestContextRefresh = actualViewModel::refreshContextWindowUsage,
            onManualMemoryUpdate = actualViewModel::manuallyUpdateMemory,
            onOpenVoiceSettings = onOpenVoiceSettings,
            onOpenCapabilities = onOpenCapabilities,
            onOpenSettings = onOpenSettings,
        )
    }
}
