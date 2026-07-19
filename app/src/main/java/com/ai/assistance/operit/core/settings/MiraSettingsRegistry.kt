package com.ai.assistance.operit.core.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.core.companion.CompanionReminderScheduler
import com.ai.assistance.operit.core.companion.MiraCompanionService
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.SpeechExpressionStrength
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.AutoReadOverride
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CompanionReminderIntensity
import com.ai.assistance.operit.data.preferences.CompanionReminderPreferences
import com.ai.assistance.operit.data.preferences.CompanionReminderTarget
import com.ai.assistance.operit.data.preferences.CompanionReminderTargetType
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.data.preferences.resolveAutoReadEnabled
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

data class MiraSettingSnapshot(
    val definition: MiraSettingDefinition,
    val value: String,
)

data class MiraSettingChange(
    val definition: MiraSettingDefinition,
    val previousValue: String,
    val currentValue: String,
) {
    val changed: Boolean
        get() = previousValue != currentValue
}

class MiraSettingsRegistry(context: Context) {
    private val appContext = context.applicationContext
    private val apiPreferences = ApiPreferences.getInstance(appContext)
    private val displayPreferences = DisplayPreferencesManager.getInstance(appContext)
    private val waifuPreferences = WaifuPreferences.getInstance(appContext)
    private val speechPreferences = SpeechServicesPreferences(appContext)
    private val reminderPreferences = CompanionReminderPreferences.getInstance(appContext)
    private val reminderScheduler = CompanionReminderScheduler.getInstance(appContext)
    private val userPreferences = UserPreferencesManager.getInstance(appContext)
    private val database = AppDatabase.getDatabase(appContext)
    private val activePromptManager = ActivePromptManager.getInstance(appContext)
    private val characterCardManager = CharacterCardManager.getInstance(appContext)

    suspend fun read(
        settingId: String,
        callerChatId: String,
        callerCardId: String,
    ): MiraSettingSnapshot {
        val definition = requireDefinition(settingId)
        requireScopeContext(definition, callerChatId, callerCardId)
        val value = readValue(definition.id, callerChatId, callerCardId)
        return MiraSettingSnapshot(definition, value)
    }

    suspend fun write(
        settingId: String,
        rawValue: String,
        callerChatId: String,
        callerCardId: String,
    ): MiraSettingChange {
        val definition = requireDefinition(settingId)
        val canonicalValue =
            when (val validation = MiraSettingsCatalog.validateValue(definition, rawValue)) {
                is MiraSettingValueValidation.Valid -> validation.canonicalValue
                is MiraSettingValueValidation.Invalid -> throw IllegalArgumentException(validation.message)
            }
        mutationMutex.lock()
        try {
            requireScopeContext(definition, callerChatId, callerCardId)
            val previousValue = readValue(definition.id, callerChatId, callerCardId)
            writeValue(definition.id, canonicalValue, callerChatId, callerCardId)
            val currentValue = readValue(definition.id, callerChatId, callerCardId)
            return MiraSettingChange(definition, previousValue, currentValue)
        } finally {
            mutationMutex.unlock()
        }
    }

    private fun requireDefinition(settingId: String): MiraSettingDefinition =
        MiraSettingsCatalog.findById(settingId)
            ?: throw IllegalArgumentException("Unknown Mira setting: $settingId")

    private suspend fun requireScopeContext(
        definition: MiraSettingDefinition,
        callerChatId: String,
        callerCardId: String,
    ) {
        when (definition.scope) {
            MiraSettingScope.GLOBAL -> Unit
            MiraSettingScope.CURRENT_CONVERSATION -> requireConversationChat(callerChatId)
            MiraSettingScope.CURRENT_COMPANION -> {
                val binding = resolveCompanionBinding(callerChatId, callerCardId)
                if (definition.id in ACTIVE_COMPANION_SNAPSHOT_SETTINGS || definition.id.startsWith("appearance.")) {
                    requireActiveCompanion(binding)
                }
            }
        }
    }

    private suspend fun readValue(
        settingId: String,
        callerChatId: String,
        callerCardId: String,
    ): String =
        when (settingId) {
            "chat.auto_read" ->
                readChatAutoRead(callerChatId, callerCardId).toString()
            "chat.immersive_mode" -> waifuPreferences.enableWaifuModeFlow.first().toString()
            "chat.memory_auto_update" -> readChatMemoryAutoUpdate(callerChatId).toString()
            "memory.auto_update.global" -> apiPreferences.enableMemoryAutoUpdateFlow.first().toString()
            "chat.thinking_mode" -> apiPreferences.enableThinkingModeFlow.first().toString()
            "chat.thinking_level" -> apiPreferences.thinkingQualityLevelFlow.first().toString()
            "chat.streaming" -> (!apiPreferences.disableStreamOutputFlow.first()).toString()
            "chat.keep_screen_on" -> apiPreferences.keepScreenOnFlow.first().toString()
            "chat.ai_input_suggestion" -> displayPreferences.enableAiInputSuggestion.first().toString()
            "speech.auto_read.global" -> apiPreferences.enableAutoReadFlow.first().toString()
            "speech.auto_read.companion" -> waifuPreferences.autoReadOverrideFlow.first().name
            "speech.expressive" -> speechPreferences.expressiveTtsEnabledFlow.first().toString()
            "speech.expression_strength" -> speechPreferences.expressiveTtsStrengthFlow.first().name
            "speech.rate" -> formatDecimal(speechPreferences.ttsSpeechRateFlow.first())
            "speech.pitch" -> formatDecimal(speechPreferences.ttsPitchFlow.first())
            "companion.background_service" -> reminderPreferences.keepAliveEnabledFlow.first().toString()
            "companion.proactive_enabled" ->
                reminderPreferences.getSettings(resolveReminderTarget(callerChatId, callerCardId)).enabled.toString()
            "companion.proactive_intensity" ->
                reminderPreferences.getSettings(resolveReminderTarget(callerChatId, callerCardId)).intensity.name
            "companion.daily_limit" ->
                reminderPreferences.getSettings(resolveReminderTarget(callerChatId, callerCardId)).dailyLimit.toString()
            "companion.quiet_hours_enabled" ->
                reminderPreferences.getSettings(resolveReminderTarget(callerChatId, callerCardId)).quietHoursEnabled.toString()
            "companion.emoticons" -> waifuPreferences.waifuEnableEmoticonsFlow.first().toString()
            "companion.selfie" -> waifuPreferences.waifuEnableSelfieFlow.first().toString()
            "companion.merge_send" -> waifuPreferences.waifuEnableMergeSendFlow.first().toString()
            "companion.typing_delay_ms" -> waifuPreferences.waifuCharDelayFlow.first().toString()
            "appearance.theme" -> readTheme()
            "appearance.chat_style" -> userPreferences.chatStyle.first().uppercase()
            "appearance.input_style" -> userPreferences.inputStyle.first().uppercase()
            "appearance.show_avatar" -> userPreferences.bubbleShowAvatar.first().toString()
            "appearance.wide_messages" -> userPreferences.bubbleWideLayoutEnabled.first().toString()
            "appearance.show_thinking" -> userPreferences.showThinkingProcess.first().toString()
            "appearance.show_status" -> userPreferences.showStatusTags.first().toString()
            "appearance.show_model_name" -> userPreferences.showModelName.first().toString()
            "appearance.show_role_name" -> userPreferences.showRoleName.first().toString()
            "appearance.show_user_name" -> userPreferences.showUserName.first().toString()
            "appearance.show_token_stats" -> userPreferences.showMessageTokenStats.first().toString()
            "appearance.show_timing" -> userPreferences.showMessageTimingStats.first().toString()
            "appearance.show_timestamp" -> userPreferences.showMessageTimestamp.first().toString()
            "appearance.status_bar_hidden" -> userPreferences.statusBarHidden.first().toString()
            "appearance.chat_header_transparent" -> userPreferences.chatHeaderTransparent.first().toString()
            "appearance.chat_input_transparent" -> userPreferences.chatInputTransparent.first().toString()
            "appearance.chat_input_floating" -> userPreferences.chatInputFloating.first().toString()
            "appearance.chat_input_liquid_glass" -> userPreferences.chatInputLiquidGlass.first().toString()
            "appearance.background_blur" -> userPreferences.useBackgroundBlur.first().toString()
            "appearance.font_scale" -> formatDecimal(userPreferences.fontScale.first())
            else -> throw IllegalStateException("Mira setting has no reader: $settingId")
        }

    private suspend fun writeValue(
        settingId: String,
        value: String,
        callerChatId: String,
        callerCardId: String,
    ) {
        when (settingId) {
            "chat.auto_read" -> {
                val chat = requireConversationChat(callerChatId)
                database.chatDao().updateChatAutoReadOverride(chat.id, value.toBooleanStrict())
            }
            "chat.immersive_mode" -> {
                waifuPreferences.saveEnableWaifuMode(value.toBooleanStrict())
                persistCurrentWaifuSettings(callerChatId, callerCardId)
            }
            "chat.memory_auto_update" -> {
                val chat = requireConversationChat(callerChatId)
                database.chatDao().updateChatMemoryAutoUpdateOverride(chat.id, value.toBooleanStrict())
            }
            "memory.auto_update.global" -> apiPreferences.saveEnableMemoryAutoUpdate(value.toBooleanStrict())
            "chat.thinking_mode" -> apiPreferences.saveEnableThinkingMode(value.toBooleanStrict())
            "chat.thinking_level" -> apiPreferences.saveThinkingQualityLevel(value.toInt())
            "chat.streaming" -> apiPreferences.saveDisableStreamOutput(!value.toBooleanStrict())
            "chat.keep_screen_on" -> apiPreferences.saveKeepScreenOn(value.toBooleanStrict())
            "chat.ai_input_suggestion" ->
                displayPreferences.saveDisplaySettings(enableAiInputSuggestion = value.toBooleanStrict())
            "speech.auto_read.global" -> apiPreferences.saveEnableAutoRead(value.toBooleanStrict())
            "speech.auto_read.companion" -> {
                waifuPreferences.saveAutoReadOverride(AutoReadOverride.valueOf(value))
                persistCurrentWaifuSettings(callerChatId, callerCardId)
            }
            "speech.expressive" -> speechPreferences.saveExpressiveTtsEnabled(value.toBooleanStrict())
            "speech.expression_strength" ->
                speechPreferences.saveExpressiveTtsStrength(SpeechExpressionStrength.valueOf(value))
            "speech.rate" -> speechPreferences.saveTtsSpeechRate(value.toFloat())
            "speech.pitch" -> speechPreferences.saveTtsPitch(value.toFloat())
            "companion.background_service" -> setBackgroundService(value.toBooleanStrict())
            "companion.proactive_enabled" -> {
                val enabled = value.toBooleanStrict()
                if (enabled) requireNotificationPermission()
                reminderPreferences.saveEnabled(resolveReminderTarget(callerChatId, callerCardId), enabled)
                reminderScheduler.syncAllProfiles()
            }
            "companion.proactive_intensity" -> {
                reminderPreferences.saveIntensity(
                    resolveReminderTarget(callerChatId, callerCardId),
                    CompanionReminderIntensity.valueOf(value),
                )
                reminderScheduler.syncAllProfiles()
            }
            "companion.daily_limit" -> {
                reminderPreferences.saveDailyLimit(resolveReminderTarget(callerChatId, callerCardId), value.toInt())
                reminderScheduler.syncAllProfiles()
            }
            "companion.quiet_hours_enabled" -> {
                reminderPreferences.saveQuietHoursEnabled(
                    resolveReminderTarget(callerChatId, callerCardId),
                    value.toBooleanStrict(),
                )
                reminderScheduler.syncAllProfiles()
            }
            "companion.emoticons" -> {
                waifuPreferences.saveWaifuEnableEmoticons(value.toBooleanStrict())
                persistCurrentWaifuSettings(callerChatId, callerCardId)
            }
            "companion.selfie" -> {
                waifuPreferences.saveWaifuEnableSelfie(value.toBooleanStrict())
                persistCurrentWaifuSettings(callerChatId, callerCardId)
            }
            "companion.merge_send" -> {
                waifuPreferences.saveWaifuEnableMergeSend(value.toBooleanStrict())
                persistCurrentWaifuSettings(callerChatId, callerCardId)
            }
            "companion.typing_delay_ms" -> {
                waifuPreferences.saveWaifuCharDelay(value.toInt())
                persistCurrentWaifuSettings(callerChatId, callerCardId)
            }
            "appearance.theme" -> {
                when (value) {
                    "SYSTEM" -> userPreferences.saveThemeSettings(useSystemTheme = true)
                    "LIGHT" ->
                        userPreferences.saveThemeSettings(
                            useSystemTheme = false,
                            themeMode = UserPreferencesManager.THEME_MODE_LIGHT,
                        )
                    "DARK" ->
                        userPreferences.saveThemeSettings(
                            useSystemTheme = false,
                            themeMode = UserPreferencesManager.THEME_MODE_DARK,
                        )
                    else -> throw IllegalArgumentException("Unsupported theme value: $value")
                }
                persistCurrentTheme(callerChatId, callerCardId)
            }
            "appearance.chat_style" -> {
                userPreferences.saveThemeSettings(chatStyle = value.lowercase())
                persistCurrentTheme(callerChatId, callerCardId)
            }
            "appearance.input_style" -> {
                userPreferences.saveThemeSettings(inputStyle = value.lowercase())
                persistCurrentTheme(callerChatId, callerCardId)
            }
            "appearance.show_avatar" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(bubbleShowAvatar = value.toBooleanStrict()) }
            "appearance.wide_messages" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(bubbleWideLayoutEnabled = value.toBooleanStrict()) }
            "appearance.show_thinking" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(showThinkingProcess = value.toBooleanStrict()) }
            "appearance.show_status" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(showStatusTags = value.toBooleanStrict()) }
            "appearance.show_model_name" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(showModelName = value.toBooleanStrict()) }
            "appearance.show_role_name" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(showRoleName = value.toBooleanStrict()) }
            "appearance.show_user_name" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(showUserName = value.toBooleanStrict()) }
            "appearance.show_token_stats" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(showMessageTokenStats = value.toBooleanStrict()) }
            "appearance.show_timing" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(showMessageTimingStats = value.toBooleanStrict()) }
            "appearance.show_timestamp" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(showMessageTimestamp = value.toBooleanStrict()) }
            "appearance.status_bar_hidden" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(statusBarHidden = value.toBooleanStrict()) }
            "appearance.chat_header_transparent" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(chatHeaderTransparent = value.toBooleanStrict()) }
            "appearance.chat_input_transparent" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(chatInputTransparent = value.toBooleanStrict()) }
            "appearance.chat_input_floating" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(chatInputFloating = value.toBooleanStrict()) }
            "appearance.chat_input_liquid_glass" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(chatInputLiquidGlass = value.toBooleanStrict()) }
            "appearance.background_blur" -> saveThemeBoolean(callerChatId, callerCardId) { userPreferences.saveThemeSettings(useBackgroundBlur = value.toBooleanStrict()) }
            "appearance.font_scale" -> {
                userPreferences.saveThemeSettings(fontScale = value.toFloat())
                persistCurrentTheme(callerChatId, callerCardId)
            }
            else -> throw IllegalStateException("Mira setting has no writer: $settingId")
        }
    }

    private suspend fun setBackgroundService(enabled: Boolean) {
        if (!enabled) {
            reminderPreferences.saveKeepAliveEnabled(false)
            MiraCompanionService.stopKeepAlive(appContext)
            return
        }
        requireNotificationPermission()
        reminderPreferences.saveKeepAliveEnabled(true)
        if (!MiraCompanionService.startKeepAlive(appContext)) {
            reminderPreferences.saveKeepAliveEnabled(false)
            throw IllegalStateException("Mira background companion service did not start")
        }
    }

    private fun requireNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            throw IllegalStateException("Notification permission is required for this setting")
        }
    }

    private suspend fun saveThemeBoolean(
        callerChatId: String,
        callerCardId: String,
        write: suspend () -> Unit,
    ) {
        write()
        persistCurrentTheme(callerChatId, callerCardId)
    }

    private suspend fun persistCurrentWaifuSettings(callerChatId: String, callerCardId: String) {
        when (val binding = resolveCompanionBinding(callerChatId, callerCardId)) {
            is CompanionBinding.Character -> waifuPreferences.saveCurrentWaifuSettingsToCharacterCard(binding.id)
            is CompanionBinding.Group -> waifuPreferences.saveCurrentWaifuSettingsToCharacterGroup(binding.id)
        }
    }

    private suspend fun persistCurrentTheme(callerChatId: String, callerCardId: String) {
        when (val binding = resolveCompanionBinding(callerChatId, callerCardId)) {
            is CompanionBinding.Character -> userPreferences.saveCurrentThemeToCharacterCard(binding.id)
            is CompanionBinding.Group -> userPreferences.saveCurrentThemeToCharacterGroup(binding.id)
        }
    }

    private suspend fun resolveReminderTarget(
        callerChatId: String,
        callerCardId: String,
    ): CompanionReminderTarget =
        when (val binding = resolveCompanionBinding(callerChatId, callerCardId)) {
            is CompanionBinding.Character ->
                CompanionReminderTarget(CompanionReminderTargetType.CHARACTER, binding.id)
            is CompanionBinding.Group ->
                CompanionReminderTarget(CompanionReminderTargetType.GROUP, binding.id)
        }

    private suspend fun resolveCompanionBinding(
        callerChatId: String,
        callerCardId: String,
    ): CompanionBinding {
        val chat =
            callerChatId.trim().takeIf { it.isNotBlank() }
                ?.let { database.chatDao().getChatById(it) }
        chat?.characterGroupId?.trim()?.takeIf { it.isNotBlank() }?.let {
            return CompanionBinding.Group(it)
        }
        callerCardId.trim().takeIf { it.isNotBlank() }?.let {
            return CompanionBinding.Character(it)
        }
        val cardName = chat?.characterCardName?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("This setting requires a companion-bound chat")
        val card = characterCardManager.findCharacterCardByName(cardName)
            ?: throw IllegalStateException("The companion card for this chat no longer exists")
        return CompanionBinding.Character(card.id)
    }

    private suspend fun requireActiveCompanion(binding: CompanionBinding) {
        val activePrompt = activePromptManager.getActivePrompt()
        val matches =
            when (binding) {
                is CompanionBinding.Character ->
                    activePrompt is ActivePrompt.CharacterCard && activePrompt.id == binding.id
                is CompanionBinding.Group ->
                    activePrompt is ActivePrompt.CharacterGroup && activePrompt.id == binding.id
            }
        if (!matches) {
            throw IllegalStateException("Open this conversation's companion before changing its role or appearance settings")
        }
    }

    private suspend fun readTheme(): String =
        if (userPreferences.useSystemTheme.first()) {
            "SYSTEM"
        } else {
            userPreferences.themeMode.first().uppercase()
        }

    private suspend fun readChatAutoRead(callerChatId: String, callerCardId: String): Boolean {
        val chat = requireConversationChat(callerChatId)
        val companionOverride =
            when (val binding = resolveCompanionBinding(callerChatId, callerCardId)) {
                is CompanionBinding.Character -> waifuPreferences.getAutoReadOverrideForCharacterCard(binding.id)
                is CompanionBinding.Group -> waifuPreferences.getAutoReadOverrideForCharacterGroup(binding.id)
            }
        return chat.autoReadOverride
            ?: resolveAutoReadEnabled(
                apiPreferences.enableAutoReadFlow.first(),
                companionOverride,
            )
    }

    private suspend fun readChatMemoryAutoUpdate(callerChatId: String): Boolean {
        val chat = requireConversationChat(callerChatId)
        return chat.memoryAutoUpdateOverride ?: apiPreferences.enableMemoryAutoUpdateFlow.first()
    }

    private suspend fun requireConversationChat(callerChatId: String) =
        callerChatId.trim().takeIf { it.isNotBlank() }
            ?.let { database.chatDao().getChatById(it) }
            ?: throw IllegalStateException("This setting requires an active saved conversation")

    private fun formatDecimal(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()

    private sealed interface CompanionBinding {
        data class Character(val id: String) : CompanionBinding

        data class Group(val id: String) : CompanionBinding
    }

    private companion object {
        val mutationMutex = Mutex()

        val ACTIVE_COMPANION_SNAPSHOT_SETTINGS =
            setOf(
                "chat.immersive_mode",
                "speech.auto_read.companion",
                "companion.emoticons",
                "companion.selfie",
                "companion.merge_send",
                "companion.typing_delay_ms",
            )
    }
}
