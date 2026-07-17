package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.SpeechExpressionStrength
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.AutoReadOverride
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.data.preferences.WakeWordPreferences
import com.ai.assistance.operit.data.preferences.resolveAutoReadEnabled
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun VoiceAndCallSettingsScreen(
    onNavigateToAdvancedServices: () -> Unit,
    onNavigateToTextToSpeech: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val waifuPreferences = remember { WaifuPreferences.getInstance(context) }
    val speechPreferences = remember { SpeechServicesPreferences(context) }
    val wakePreferences = remember { WakeWordPreferences(context) }
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val characterGroupManager = remember { CharacterGroupCardManager.getInstance(context) }

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
                is ActivePrompt.CharacterGroup -> characterGroupManager.getCharacterGroupCardFlow(prompt.id)
                is ActivePrompt.CharacterCard -> flowOf(null)
            }
        }.collectAsState(initial = null)

    val globalAutoRead by
        apiPreferences.enableAutoReadFlow.collectAsState(
            initial = ApiPreferences.DEFAULT_ENABLE_AUTO_READ,
        )
    val autoReadOverride by
        waifuPreferences.autoReadOverrideFlow.collectAsState(
            initial = AutoReadOverride.INHERIT,
        )
    val ttsServiceType by
        speechPreferences.ttsServiceTypeFlow.collectAsState(
            initial = SpeechServicesPreferences.DEFAULT_TTS_SERVICE_TYPE,
        )
    val sttServiceType by
        speechPreferences.sttServiceTypeFlow.collectAsState(
            initial = SpeechServicesPreferences.DEFAULT_STT_SERVICE_TYPE,
        )
    val httpTtsConfig by
        speechPreferences.ttsHttpConfigFlow.collectAsState(
            initial = SpeechServicesPreferences.DEFAULT_HTTP_TTS_PRESET,
        )
    val vitsConfig by
        speechPreferences.ttsVitsPackageConfigFlow.collectAsState(
            initial = SpeechServicesPreferences.DEFAULT_VITS_TTS_PACKAGE_CONFIG,
        )
    val speechRate by
        speechPreferences.ttsSpeechRateFlow.collectAsState(
            initial = SpeechServicesPreferences.DEFAULT_TTS_SPEECH_RATE,
        )
    val pitch by
        speechPreferences.ttsPitchFlow.collectAsState(
            initial = SpeechServicesPreferences.DEFAULT_TTS_PITCH,
        )
    val expressiveTtsEnabled by
        speechPreferences.expressiveTtsEnabledFlow.collectAsState(
            initial = SpeechServicesPreferences.DEFAULT_EXPRESSIVE_TTS_ENABLED,
        )
    val expressiveTtsStrength by
        speechPreferences.expressiveTtsStrengthFlow.collectAsState(
            initial = SpeechServicesPreferences.DEFAULT_EXPRESSIVE_TTS_STRENGTH,
        )
    val voiceCallBargeInEnabled by
        wakePreferences.voiceCallBargeInEnabledFlow.collectAsState(
            initial = WakeWordPreferences.DEFAULT_VOICE_CALL_BARGE_IN_ENABLED,
        )
    val voiceCallSilenceTimeoutMs by
        wakePreferences.voiceCallSilenceTimeoutMsFlow.collectAsState(
            initial = WakeWordPreferences.DEFAULT_VOICE_CALL_SILENCE_TIMEOUT_MS,
        )

    val activeTargetName =
        activeCharacterGroup?.name
            ?: activeCharacterCard?.name
            ?: stringResource(R.string.mate_default_companion)
    val effectiveAutoRead = resolveAutoReadEnabled(globalAutoRead, autoReadOverride)
    val currentVoice =
        when (ttsServiceType) {
            VoiceServiceFactory.VoiceServiceType.VITS_TTS -> vitsConfig.speakerId
            else -> httpTtsConfig.voiceId
        }.ifBlank { stringResource(R.string.voice_call_default_voice) }

    fun saveCharacterOverride(override: AutoReadOverride) {
        scope.launch {
            waifuPreferences.saveAutoReadOverride(override)
            when (val prompt = activePrompt) {
                is ActivePrompt.CharacterCard ->
                    waifuPreferences.saveCurrentWaifuSettingsToCharacterCard(prompt.id)
                is ActivePrompt.CharacterGroup ->
                    waifuPreferences.saveCurrentWaifuSettingsToCharacterGroup(prompt.id)
            }
        }
    }

    CustomScaffold { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                VoiceSettingsSectionTitle(stringResource(R.string.voice_call_section_character_voice))
                VoiceSettingsGroup {
                    VoiceSettingsRow(
                        title = stringResource(R.string.voice_call_character_auto_read),
                        subtitle =
                            stringResource(
                                R.string.voice_call_character_auto_read_summary,
                                activeTargetName,
                                stringResource(
                                    if (effectiveAutoRead) {
                                        R.string.voice_call_effective_enabled
                                    } else {
                                        R.string.voice_call_effective_disabled
                                    },
                                ),
                            ),
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    )
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AutoReadOverride.entries.forEach { option ->
                            FilterChip(
                                selected = autoReadOverride == option,
                                onClick = { saveCharacterOverride(option) },
                                label = {
                                    Text(
                                        text = autoReadOverrideLabel(option),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    HorizontalDivider()
                    VoiceSettingsRow(
                        title = stringResource(R.string.voice_call_global_default),
                        subtitle = stringResource(R.string.voice_call_global_default_desc),
                        icon = Icons.Default.RecordVoiceOver,
                        trailing = {
                            Switch(
                                checked = globalAutoRead,
                                onCheckedChange = { enabled ->
                                    scope.launch { apiPreferences.saveEnableAutoRead(enabled) }
                                },
                            )
                        },
                    )
                }

                VoiceSettingsSectionTitle(stringResource(R.string.voice_call_section_voice_style))
                VoiceSettingsGroup {
                    VoiceSettingsRow(
                        title = stringResource(R.string.voice_call_expressive_tts),
                        subtitle = stringResource(R.string.voice_call_expressive_tts_desc),
                        icon = Icons.Default.RecordVoiceOver,
                        trailing = {
                            Switch(
                                checked = expressiveTtsEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { speechPreferences.saveExpressiveTtsEnabled(enabled) }
                                },
                            )
                        },
                    )
                    if (expressiveTtsEnabled) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SpeechExpressionStrength.entries.forEach { strength ->
                                FilterChip(
                                    selected = expressiveTtsStrength == strength,
                                    onClick = {
                                        scope.launch {
                                            speechPreferences.saveExpressiveTtsStrength(strength)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text =
                                                stringResource(
                                                    when (strength) {
                                                        SpeechExpressionStrength.RESTRAINED -> R.string.voice_call_expression_restrained
                                                        SpeechExpressionStrength.NATURAL -> R.string.voice_call_expression_natural
                                                        SpeechExpressionStrength.VIVID -> R.string.voice_call_expression_vivid
                                                    }
                                                ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    VoiceSettingsRow(
                        title = stringResource(R.string.voice_call_current_voice),
                        subtitle = "${ttsServiceLabel(ttsServiceType)} · $currentVoice",
                        icon = Icons.Default.GraphicEq,
                        onClick = onNavigateToAdvancedServices,
                    )
                    HorizontalDivider()
                    VoiceSettingsRow(
                        title = stringResource(R.string.voice_call_rate_pitch),
                        subtitle =
                            stringResource(
                                R.string.voice_call_rate_pitch_summary,
                                speechRate,
                                pitch,
                            ),
                        icon = Icons.Default.Tune,
                        onClick = onNavigateToAdvancedServices,
                    )
                    HorizontalDivider()
                    VoiceSettingsRow(
                        title = stringResource(R.string.voice_call_preview_voice),
                        subtitle = stringResource(R.string.voice_call_preview_voice_desc),
                        icon = Icons.Default.PlayArrow,
                        onClick = onNavigateToTextToSpeech,
                    )
                }

                VoiceSettingsSectionTitle(stringResource(R.string.voice_call_section_interaction))
                VoiceSettingsGroup {
                    VoiceSettingsRow(
                        title = stringResource(R.string.voice_call_barge_in),
                        subtitle = stringResource(R.string.voice_call_barge_in_desc),
                        icon = Icons.Default.Mic,
                        trailing = {
                            Switch(
                                checked = voiceCallBargeInEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { wakePreferences.saveVoiceCallBargeInEnabled(enabled) }
                                },
                            )
                        },
                    )
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.voice_call_silence_rhythm),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp),
                    )
                    Text(
                        text = stringResource(R.string.voice_call_silence_rhythm_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
                    )
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            1_200 to R.string.voice_call_silence_fast,
                            1_800 to R.string.voice_call_silence_natural,
                            2_600 to R.string.voice_call_silence_relaxed,
                        ).forEach { (timeoutMs, labelRes) ->
                            FilterChip(
                                selected = voiceCallSilenceTimeoutMs == timeoutMs,
                                onClick = {
                                    scope.launch { wakePreferences.saveVoiceCallSilenceTimeoutMs(timeoutMs) }
                                },
                                label = { Text(stringResource(labelRes), maxLines = 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                VoiceSettingsSectionTitle(stringResource(R.string.voice_call_section_services))
                VoiceSettingsGroup {
                    VoiceSettingsRow(
                        title = stringResource(R.string.voice_call_tts_service),
                        subtitle = ttsServiceLabel(ttsServiceType),
                        icon = Icons.Default.RecordVoiceOver,
                        onClick = onNavigateToAdvancedServices,
                    )
                    HorizontalDivider()
                    VoiceSettingsRow(
                        title = stringResource(R.string.voice_call_stt_service),
                        subtitle = sttServiceLabel(sttServiceType),
                        icon = Icons.Default.Mic,
                        onClick = onNavigateToAdvancedServices,
                    )
                    HorizontalDivider()
                    VoiceSettingsRow(
                        title = stringResource(R.string.voice_call_advanced_services),
                        subtitle = stringResource(R.string.voice_call_advanced_services_desc),
                        icon = Icons.Default.Settings,
                        onClick = onNavigateToAdvancedServices,
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))
            }
        }
    }
}

@Composable
private fun VoiceSettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun VoiceSettingsGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(content = { content() })
    }
}

@Composable
private fun VoiceSettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = subtitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        },
        trailingContent =
            trailing
                ?: onClick?.let {
                    {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier =
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            },
    )
}

@Composable
private fun autoReadOverrideLabel(override: AutoReadOverride): String {
    return stringResource(
        when (override) {
            AutoReadOverride.INHERIT -> R.string.voice_call_override_inherit
            AutoReadOverride.ENABLED -> R.string.voice_call_override_enabled
            AutoReadOverride.DISABLED -> R.string.voice_call_override_disabled
        },
    )
}

@Composable
private fun ttsServiceLabel(type: VoiceServiceFactory.VoiceServiceType): String {
    return stringResource(
        when (type) {
            VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS -> R.string.speech_services_tts_type_simple
            VoiceServiceFactory.VoiceServiceType.HTTP_TTS -> R.string.speech_services_tts_type_http
            VoiceServiceFactory.VoiceServiceType.OPENAI_WS_TTS -> R.string.speech_services_tts_type_openai_ws
            VoiceServiceFactory.VoiceServiceType.SILICONFLOW_TTS -> R.string.speech_services_tts_type_siliconflow
            VoiceServiceFactory.VoiceServiceType.MINIMAX_TTS -> R.string.speech_services_tts_type_minimax
            VoiceServiceFactory.VoiceServiceType.MIMO_TTS -> R.string.speech_services_tts_type_mimo
            VoiceServiceFactory.VoiceServiceType.DOUBAO_TTS -> R.string.speech_services_tts_type_doubao
            VoiceServiceFactory.VoiceServiceType.OPENAI_TTS -> R.string.speech_services_tts_type_openai
            VoiceServiceFactory.VoiceServiceType.VITS_TTS -> R.string.speech_services_tts_type_vits
        },
    )
}

@Composable
private fun sttServiceLabel(type: SpeechServiceFactory.SpeechServiceType): String {
    return stringResource(
        when (type) {
            SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN -> R.string.speech_services_stt_type_sherpa
            SpeechServiceFactory.SpeechServiceType.OPENAI_STT -> R.string.speech_services_stt_type_openai
            SpeechServiceFactory.SpeechServiceType.DEEPGRAM_STT -> R.string.speech_services_stt_type_deepgram
        },
    )
}
