package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.data.skill.ChatSkillActivationStore
import com.ai.assistance.operit.ui.features.chat.components.ContextCapacityDialog
import com.ai.assistance.operit.ui.features.chat.components.formatCompactTokenCount
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal fun resolveMiraThinkingGear(
    thinkingEnabled: Boolean,
    thinkingQualityLevel: Int,
    maxThinkingQualityLevel: Int = ApiPreferences.MAX_THINKING_QUALITY_LEVEL,
): Int =
    if (thinkingEnabled) {
        thinkingQualityLevel.coerceIn(
            ApiPreferences.MIN_THINKING_QUALITY_LEVEL,
            maxThinkingQualityLevel,
        )
    } else {
        0
    }

private data class MiraModelChoice(
    val configId: String,
    val configName: String,
    val modelIndex: Int,
    val modelName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiraComposerControlBar(
    actualViewModel: ChatViewModel,
    onOpenAttachments: () -> Unit,
    onNavigateToModelConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val modelConfigManager = remember(context) { ModelConfigManager(context) }
    val activePromptManager = remember(context) { ActivePromptManager.getInstance(context) }
    val waifuPreferences = remember(context) { WaifuPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()
    val skillActivationStore = remember(context) { ChatSkillActivationStore.getInstance(context) }
    val chatHistories by actualViewModel.chatHistories.collectAsState()
    val currentChatId by actualViewModel.currentChatId.collectAsState()
    val activeSkills by
        remember(currentChatId, skillActivationStore) {
            skillActivationStore.observeActiveSkills(currentChatId)
        }.collectAsState(initial = emptyList())
    val defaultModelName by actualViewModel.modelName.collectAsState()
    val globalMemoryEnabled by actualViewModel.enableMemoryAutoUpdate.collectAsState()
    val immersiveChatEnabled by
        waifuPreferences.enableWaifuModeFlow.collectAsState(
            initial = WaifuPreferences.DEFAULT_ENABLE_WAIFU_MODE
        )
    val thinkingEnabled by actualViewModel.enableThinkingMode.collectAsState()
    val thinkingQualityLevel by actualViewModel.thinkingQualityLevel.collectAsState()
    val currentContextTokens by actualViewModel.currentWindowSize.collectAsState()
    val maxContextWindowInK by actualViewModel.maxWindowSizeInK.collectAsState()
    val baseContextLengthInK by actualViewModel.baseContextLengthInK.collectAsState()
    val maxContextLengthInK by actualViewModel.maxContextLengthInK.collectAsState()
    val enableMaxContextMode by actualViewModel.enableMaxContextMode.collectAsState()
    val contextWindowUsage by actualViewModel.contextWindowUsage.collectAsState()
    val currentChat = chatHistories.firstOrNull { it.id == currentChatId }

    var modelChoices by remember { mutableStateOf<List<MiraModelChoice>>(emptyList()) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showContextDetails by remember { mutableStateOf(false) }

    LaunchedEffect(modelConfigManager) {
        modelConfigManager.initializeIfNeeded()
        modelConfigManager.configListFlow.collect { configIds ->
            modelChoices =
                configIds.flatMap { configId ->
                    val config = modelConfigManager.getModelConfig(configId) ?: return@flatMap emptyList()
                    getModelList(config.modelName).mapIndexed { index, modelName ->
                        MiraModelChoice(
                            configId = config.id,
                            configName = config.name,
                            modelIndex = index,
                            modelName = modelName,
                        )
                    }
                }
        }
    }

    val selectedConfigId = currentChat?.chatModelConfigId
    val selectedModelIndex = currentChat?.chatModelIndex ?: 0
    val fallbackModelName =
        getModelList(defaultModelName).firstOrNull()
            ?: if (defaultModelName.isBlank()) {
                stringResource(R.string.mira_model_not_configured)
            } else {
                defaultModelName
            }
    val displayModelName =
        modelChoices
            .firstOrNull { it.configId == selectedConfigId && it.modelIndex == selectedModelIndex }
            ?.modelName
            ?: fallbackModelName
    val effectiveMemoryEnabled = currentChat?.memoryAutoUpdateOverride ?: globalMemoryEnabled
    val maxContextTokens = (maxContextWindowInK * 1024).toLong().coerceAtLeast(0L)
    val contextProgress =
        if (maxContextTokens > 0L) {
            (currentContextTokens.toFloat() / maxContextTokens.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val thinkingGear = resolveMiraThinkingGear(thinkingEnabled, thinkingQualityLevel)
    val thinkingStatusLabel =
        if (thinkingGear == 0) {
            stringResource(R.string.mira_thinking_off)
        } else {
            stringResource(R.string.mira_thinking_gear_status, thinkingGear)
        }

    val setImmersiveChatEnabled: (Boolean) -> Unit = { enabled ->
        scope.launch {
            waifuPreferences.saveEnableWaifuMode(enabled)
            when (val activePrompt = activePromptManager.getActivePrompt()) {
                is ActivePrompt.CharacterCard ->
                    waifuPreferences.saveCurrentWaifuSettingsToCharacterCard(activePrompt.id)
                is ActivePrompt.CharacterGroup ->
                    waifuPreferences.saveCurrentWaifuSettingsToCharacterGroup(activePrompt.id)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (activeSkills.isNotEmpty()) {
            val primarySkill = activeSkills.first()
            val primarySkillLabel = stringResource(R.string.mira_active_skill, primarySkill)
            val skillSummary =
                if (activeSkills.size > 1) "$primarySkillLabel +${activeSkills.size - 1}" else primarySkillLabel
            Surface(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp).widthIn(max = 240.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = skillSummary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Surface(
                onClick = onOpenAttachments,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_attachment),
                        modifier = Modifier.size(21.dp),
                    )
                }
            }

            Surface(
                onClick = { showModelSheet = true },
                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$displayModelName · $thinkingStatusLabel",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (showModelSheet) {
        ModalBottomSheet(onDismissRequest = { showModelSheet = false }) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.mira_session_controls),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { actualViewModel.toggleThinkingMode() }
                            .padding(top = 16.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.mira_session_thinking),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = thinkingEnabled,
                        onCheckedChange = { actualViewModel.toggleThinkingMode() },
                    )
                }
                if (thinkingEnabled) {
                    Text(
                        text = stringResource(R.string.mira_thinking_gear),
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        (ApiPreferences.MIN_THINKING_QUALITY_LEVEL..ApiPreferences.MAX_THINKING_QUALITY_LEVEL)
                            .forEach { level ->
                                Surface(
                                    onClick = { actualViewModel.updateThinkingQualityLevel(level) },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color =
                                        if (thinkingGear == level) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = stringResource(R.string.mira_thinking_gear_short, level),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                        )
                                    }
                                }
                            }
                    }
                }

                HorizontalDivider()
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { setImmersiveChatEnabled(!immersiveChatEnabled) }
                            .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.enable_waifu_mode),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.waifu_mode_toggle_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Switch(
                        checked = immersiveChatEnabled,
                        onCheckedChange = null,
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                actualViewModel.updateCurrentChatMemoryOverride(!effectiveMemoryEnabled)
                            }
                            .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.mira_session_memory), modifier = Modifier.weight(1f))
                    Switch(
                        checked = effectiveMemoryEnabled,
                        onCheckedChange = actualViewModel::updateCurrentChatMemoryOverride,
                    )
                }
                HorizontalDivider()
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { actualViewModel.toggleEnableMaxContextMode() }
                            .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.max_mode_title),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.mira_max_context_summary,
                                    formatContextLength(baseContextLengthInK),
                                    formatContextLength(maxContextLengthInK),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enableMaxContextMode,
                        onCheckedChange = { actualViewModel.toggleEnableMaxContextMode() },
                    )
                }
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                actualViewModel.refreshContextWindowUsage()
                                showModelSheet = false
                                showContextDetails = true
                            }
                            .padding(vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.context_capacity),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text =
                                "${formatCompactTokenCount(currentContextTokens)} / " +
                                    formatCompactTokenCount(maxContextTokens),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    LinearProgressIndicator(
                        progress = { contextProgress },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 6.dp),
                        color =
                            when {
                                contextProgress >= 0.9f -> MaterialTheme.colorScheme.error
                                contextProgress >= 0.7f -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.mira_select_model),
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MiraModelChoiceRow(
                    title = stringResource(R.string.mira_follow_character_model),
                    subtitle = stringResource(R.string.mira_follow_character_model_desc),
                    selected = selectedConfigId == null,
                ) {
                    actualViewModel.updateCurrentChatModel(null, 0)
                }
                modelChoices.forEach { choice ->
                    MiraModelChoiceRow(
                        title = choice.modelName,
                        subtitle = choice.configName,
                        selected =
                            selectedConfigId == choice.configId &&
                                selectedModelIndex == choice.modelIndex,
                    ) {
                        actualViewModel.updateCurrentChatModel(choice.configId, choice.modelIndex)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                TextButton(
                    onClick = {
                        showModelSheet = false
                        onNavigateToModelConfig()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mira_manage_models_api))
                }
                Spacer(Modifier.heightIn(min = 8.dp))
            }
        }
    }

    if (showContextDetails) {
        ContextCapacityDialog(
            currentTokens = currentContextTokens,
            maxTokens = maxContextTokens,
            usage = contextWindowUsage,
            onDismiss = { showContextDetails = false },
        )
    }
}

private fun formatContextLength(valueInK: Float): String =
    if (valueInK % 1f == 0f) {
        valueInK.toInt().toString()
    } else {
        String.format("%.1f", valueInK)
    }

@Composable
private fun MiraModelChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
