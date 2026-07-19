package com.ai.assistance.operit.core.settings

import java.util.Locale

enum class MiraSettingValueType {
    BOOLEAN,
    INTEGER,
    DECIMAL,
    OPTION,
}

enum class MiraSettingScope {
    GLOBAL,
    CURRENT_CONVERSATION,
    CURRENT_COMPANION,
}

data class MiraSettingDefinition(
    val id: String,
    val label: String,
    val description: String,
    val aliases: Set<String>,
    val valueType: MiraSettingValueType,
    val scope: MiraSettingScope = MiraSettingScope.GLOBAL,
    val options: Set<String> = emptySet(),
    val optionAliases: Map<String, String> = emptyMap(),
    val minimum: Double? = null,
    val maximum: Double? = null,
) {
    val valueHint: String
        get() =
            when (valueType) {
                MiraSettingValueType.BOOLEAN -> "true | false"
                MiraSettingValueType.INTEGER,
                MiraSettingValueType.DECIMAL ->
                    listOfNotNull(minimum, maximum).joinToString("..")
                MiraSettingValueType.OPTION -> options.joinToString(" | ")
            }
}

sealed interface MiraSettingValueValidation {
    data class Valid(val canonicalValue: String) : MiraSettingValueValidation

    data class Invalid(val message: String) : MiraSettingValueValidation
}

object MiraSettingsCatalog {
    val definitions: List<MiraSettingDefinition> =
        listOf(
            booleanSetting(
                id = "chat.auto_read",
                label = "自动朗读",
                description = "当前会话是否自动朗读回复",
                aliases = setOf("自动播放语音", "自动读消息", "auto read", "朗读回复"),
                scope = MiraSettingScope.CURRENT_CONVERSATION,
            ),
            booleanSetting(
                id = "chat.immersive_mode",
                label = "沉浸对话",
                description = "当前角色是否按自然节奏拆分和呈现回复",
                aliases = setOf("沉浸式对话", "沉浸回复", "immersive chat"),
                scope = MiraSettingScope.CURRENT_COMPANION,
            ),
            booleanSetting(
                id = "chat.memory_auto_update",
                label = "自动记忆",
                description = "当前会话是否从稳定对话事实中更新长期记忆",
                aliases = setOf("记忆自动更新", "自动保存记忆", "memory update"),
                scope = MiraSettingScope.CURRENT_CONVERSATION,
            ),
            booleanSetting(
                id = "memory.auto_update.global",
                label = "全局自动记忆默认值",
                description = "新会话继承的自动记忆全局默认值",
                aliases = setOf("全局自动记忆", "默认自动记忆", "global memory update"),
            ),
            booleanSetting(
                id = "chat.thinking_mode",
                label = "思考模式",
                description = "是否启用思考模式",
                aliases = setOf("推理模式", "深度思考", "thinking mode"),
            ),
            integerSetting(
                id = "chat.thinking_level",
                label = "思考档位",
                description = "思考质量档位",
                aliases = setOf("推理档位", "思考等级", "thinking level"),
                minimum = 1,
                maximum = 5,
            ),
            booleanSetting(
                id = "chat.streaming",
                label = "流式回复",
                description = "回复生成时是否逐步显示文本",
                aliases = setOf("流式输出", "打字机输出", "streaming"),
            ),
            booleanSetting(
                id = "chat.keep_screen_on",
                label = "聊天时屏幕常亮",
                description = "使用聊天时是否保持屏幕常亮",
                aliases = setOf("屏幕常亮", "保持亮屏", "keep screen on"),
            ),
            booleanSetting(
                id = "chat.ai_input_suggestion",
                label = "智能接话建议",
                description = "AI 回复完成后是否在空白输入框中生成一条接话建议",
                aliases =
                    setOf(
                        "自动生成回复",
                        "自动生成消息",
                        "输入框自动回复",
                        "建议回复",
                        "回复草稿",
                        "smart reply draft",
                        "ai input suggestion",
                    ),
            ),
            booleanSetting(
                id = "speech.auto_read.global",
                label = "全局自动朗读默认值",
                description = "未设置角色或会话覆盖时使用的自动朗读默认值",
                aliases = setOf("全局自动朗读", "默认自动朗读", "global auto read"),
            ),
            optionSetting(
                id = "speech.auto_read.companion",
                label = "角色自动朗读",
                description = "当前角色继承、开启或关闭自动朗读",
                aliases = setOf("当前角色自动朗读", "角色朗读", "companion auto read"),
                options = setOf("INHERIT", "ENABLED", "DISABLED"),
                optionAliases =
                    mapOf(
                        "继承" to "INHERIT",
                        "跟随全局" to "INHERIT",
                        "inherit" to "INHERIT",
                        "开启" to "ENABLED",
                        "打开" to "ENABLED",
                        "true" to "ENABLED",
                        "enabled" to "ENABLED",
                        "关闭" to "DISABLED",
                        "false" to "DISABLED",
                        "disabled" to "DISABLED",
                    ),
                scope = MiraSettingScope.CURRENT_COMPANION,
            ),
            booleanSetting(
                id = "speech.expressive",
                label = "情感朗读",
                description = "TTS 是否使用回复中的情绪和表达指令",
                aliases = setOf("情绪朗读", "有感情朗读", "expressive tts"),
            ),
            optionSetting(
                id = "speech.expression_strength",
                label = "语音表现力度",
                description = "情感朗读的整体表现力度",
                aliases = setOf("情绪强度", "朗读力度", "expression strength"),
                options = setOf("RESTRAINED", "NATURAL", "VIVID"),
                optionAliases =
                    mapOf(
                        "克制" to "RESTRAINED",
                        "restrained" to "RESTRAINED",
                        "自然" to "NATURAL",
                        "natural" to "NATURAL",
                        "鲜明" to "VIVID",
                        "生动" to "VIVID",
                        "vivid" to "VIVID",
                    ),
            ),
            decimalSetting(
                id = "speech.rate",
                label = "朗读语速",
                description = "TTS 基础语速倍率",
                aliases = setOf("语速", "说话速度", "speech rate"),
                minimum = 0.5,
                maximum = 2.0,
            ),
            decimalSetting(
                id = "speech.pitch",
                label = "朗读音高",
                description = "TTS 基础音高倍率",
                aliases = setOf("音高", "声音高低", "speech pitch"),
                minimum = 0.5,
                maximum = 2.0,
            ),
            booleanSetting(
                id = "companion.background_service",
                label = "后台陪伴",
                description = "是否允许 Mira 的陪伴服务保持运行",
                aliases = setOf("后台常驻", "陪伴常驻", "keep alive"),
            ),
            booleanSetting(
                id = "companion.proactive_enabled",
                label = "主动陪伴消息",
                description = "当前角色是否可以发送主动消息",
                aliases = setOf("主动消息", "主动找我", "proactive messages"),
                scope = MiraSettingScope.CURRENT_COMPANION,
            ),
            optionSetting(
                id = "companion.proactive_intensity",
                label = "主动陪伴频率",
                description = "当前角色发送主动消息的频率",
                aliases = setOf("主动消息频率", "陪伴频率", "proactive intensity"),
                options = setOf("EXPLICIT_ONLY", "OCCASIONAL", "DAILY"),
                optionAliases =
                    mapOf(
                        "仅明确事项" to "EXPLICIT_ONLY",
                        "仅提醒" to "EXPLICIT_ONLY",
                        "explicit_only" to "EXPLICIT_ONLY",
                        "偶尔" to "OCCASIONAL",
                        "occasional" to "OCCASIONAL",
                        "每天" to "DAILY",
                        "每日" to "DAILY",
                        "daily" to "DAILY",
                    ),
                scope = MiraSettingScope.CURRENT_COMPANION,
            ),
            integerSetting(
                id = "companion.daily_limit",
                label = "每日主动消息上限",
                description = "当前角色每天最多发送多少条主动消息",
                aliases = setOf("每日消息上限", "主动消息上限", "daily proactive limit"),
                minimum = 1,
                maximum = 8,
                scope = MiraSettingScope.CURRENT_COMPANION,
            ),
            booleanSetting(
                id = "companion.quiet_hours_enabled",
                label = "主动陪伴免打扰",
                description = "当前角色是否遵守免打扰时段",
                aliases = setOf("免打扰", "安静时段", "quiet hours"),
                scope = MiraSettingScope.CURRENT_COMPANION,
            ),
            booleanSetting(
                id = "companion.emoticons",
                label = "角色表情",
                description = "当前角色是否可以使用表情内容",
                aliases = setOf("表情包", "角色表情包", "emoticons"),
                scope = MiraSettingScope.CURRENT_COMPANION,
            ),
            booleanSetting(
                id = "companion.selfie",
                label = "角色自拍",
                description = "当前角色是否可以生成自拍内容",
                aliases = setOf("自拍功能", "角色自拍功能", "selfie"),
                scope = MiraSettingScope.CURRENT_COMPANION,
            ),
            booleanSetting(
                id = "companion.merge_send",
                label = "合并发送",
                description = "当前角色是否等待并合并连续消息",
                aliases = setOf("合并消息", "合并回复", "merge send"),
                scope = MiraSettingScope.CURRENT_COMPANION,
            ),
            integerSetting(
                id = "companion.typing_delay_ms",
                label = "沉浸回复字间延迟",
                description = "当前角色沉浸回复的每字符延迟毫秒数",
                aliases = setOf("打字速度", "回复速度", "字间延迟", "typing delay"),
                minimum = 200,
                maximum = 1000,
                scope = MiraSettingScope.CURRENT_COMPANION,
            ),
            optionSetting(
                id = "appearance.theme",
                label = "明暗主题",
                description = "应用使用系统、浅色或深色主题",
                aliases = setOf("主题模式", "深色模式", "夜间模式", "theme"),
                options = setOf("SYSTEM", "LIGHT", "DARK"),
                optionAliases =
                    mapOf(
                        "跟随系统" to "SYSTEM",
                        "系统" to "SYSTEM",
                        "system" to "SYSTEM",
                        "浅色" to "LIGHT",
                        "白天" to "LIGHT",
                        "light" to "LIGHT",
                        "深色" to "DARK",
                        "夜间" to "DARK",
                        "dark" to "DARK",
                    ),
            ),
            optionSetting(
                id = "appearance.chat_style",
                label = "消息样式",
                description = "聊天消息使用气泡或简洁正文样式",
                aliases = setOf("聊天样式", "气泡样式", "message style"),
                options = setOf("BUBBLE", "CURSOR"),
                optionAliases =
                    mapOf(
                        "气泡" to "BUBBLE",
                        "bubble" to "BUBBLE",
                        "简洁" to "CURSOR",
                        "正文" to "CURSOR",
                        "cursor" to "CURSOR",
                    ),
            ),
            optionSetting(
                id = "appearance.input_style",
                label = "输入区样式",
                description = "聊天输入区使用经典或 Agent 布局",
                aliases = setOf("输入框样式", "输入栏样式", "input style"),
                options = setOf("CLASSIC", "AGENT"),
                optionAliases =
                    mapOf(
                        "经典" to "CLASSIC",
                        "classic" to "CLASSIC",
                        "agent" to "AGENT",
                        "智能体" to "AGENT",
                    ),
            ),
            booleanSetting("appearance.show_avatar", "显示头像", "气泡消息旁是否显示头像", setOf("消息头像", "头像显示")),
            booleanSetting("appearance.wide_messages", "宽消息布局", "气泡消息是否使用更宽的内容区域", setOf("宽气泡", "消息变宽")),
            booleanSetting("appearance.show_thinking", "显示思考过程", "消息中是否显示模型思考过程", setOf("思考过程", "推理过程")),
            booleanSetting("appearance.show_status", "显示状态标签", "消息中是否显示状态标签", setOf("状态标签", "工具状态")),
            booleanSetting("appearance.show_model_name", "显示模型名称", "消息中是否显示模型名称", setOf("模型名", "模型名称")),
            booleanSetting("appearance.show_role_name", "显示角色名称", "消息中是否显示角色名称", setOf("角色名", "角色名称")),
            booleanSetting("appearance.show_user_name", "显示用户名称", "消息中是否显示用户名称", setOf("用户名", "用户名称")),
            booleanSetting("appearance.show_token_stats", "显示 Token 统计", "消息中是否显示 Token 使用量", setOf("token统计", "令牌统计")),
            booleanSetting("appearance.show_timing", "显示耗时统计", "消息中是否显示生成耗时", setOf("响应耗时", "生成时间")),
            booleanSetting("appearance.show_timestamp", "显示消息时间", "消息中是否显示时间戳", setOf("消息时间", "时间戳")),
            booleanSetting("appearance.status_bar_hidden", "隐藏状态栏", "应用是否隐藏系统状态栏", setOf("状态栏隐藏", "全屏")),
            booleanSetting("appearance.chat_header_transparent", "透明聊天顶栏", "聊天顶部工具栏是否透明", setOf("顶部透明", "透明顶栏")),
            booleanSetting("appearance.chat_input_transparent", "透明聊天输入区", "聊天输入区域是否透明", setOf("输入框透明", "底部透明")),
            booleanSetting("appearance.chat_input_floating", "悬浮聊天输入区", "聊天输入区域是否以悬浮形式显示", setOf("悬浮输入框", "浮动输入栏")),
            booleanSetting("appearance.chat_input_liquid_glass", "输入区液态玻璃", "聊天输入区域是否使用液态玻璃效果", setOf("液态玻璃", "输入框玻璃")),
            booleanSetting("appearance.background_blur", "背景模糊", "聊天背景是否使用模糊效果", setOf("模糊背景", "背景虚化")),
            decimalSetting("appearance.font_scale", "字体缩放", "应用文字的整体缩放倍率", setOf("字体大小", "文字大小"), 0.8, 1.5),
        ).map { definition ->
            if (definition.id.startsWith("appearance.")) {
                definition.copy(scope = MiraSettingScope.CURRENT_COMPANION)
            } else {
                definition
            }
        }

    private val byId = definitions.associateBy { it.id }

    fun findById(id: String): MiraSettingDefinition? = byId[id.trim().lowercase(Locale.ROOT)]

    fun search(query: String, limit: Int = 12): List<MiraSettingDefinition> {
        val normalized = normalize(query)
        if (normalized.isBlank()) return definitions.take(limit.coerceIn(1, definitions.size))
        return definitions
            .mapNotNull { definition ->
                val candidates = listOf(definition.id, definition.label, definition.description) + definition.aliases
                val score = candidates.maxOf { candidate -> matchScore(normalized, normalize(candidate)) }
                definition.takeIf { score > 0 }?.let { it to score }
            }
            .sortedWith(compareByDescending<Pair<MiraSettingDefinition, Int>> { it.second }.thenBy { it.first.id })
            .take(limit.coerceIn(1, definitions.size))
            .map { it.first }
    }

    fun validateValue(definition: MiraSettingDefinition, rawValue: String): MiraSettingValueValidation {
        val trimmed = rawValue.trim()
        if (trimmed.isBlank()) return MiraSettingValueValidation.Invalid("value must not be empty")
        return when (definition.valueType) {
            MiraSettingValueType.BOOLEAN -> validateBoolean(trimmed)
            MiraSettingValueType.INTEGER -> validateInteger(definition, trimmed)
            MiraSettingValueType.DECIMAL -> validateDecimal(definition, trimmed)
            MiraSettingValueType.OPTION -> validateOption(definition, trimmed)
        }
    }

    private fun validateBoolean(value: String): MiraSettingValueValidation {
        val normalized = normalize(value)
        val parsed =
            when (normalized) {
                "true", "1", "on", "yes", "enable", "enabled", "开", "开启", "打开", "启用" -> true
                "false", "0", "off", "no", "disable", "disabled", "关", "关闭", "停用" -> false
                else -> null
            }
        return parsed?.let { MiraSettingValueValidation.Valid(it.toString()) }
            ?: MiraSettingValueValidation.Invalid("expected true or false")
    }

    private fun validateInteger(
        definition: MiraSettingDefinition,
        value: String,
    ): MiraSettingValueValidation {
        val parsed = value.toIntOrNull()
            ?: return MiraSettingValueValidation.Invalid("expected an integer")
        return validateRange(definition, parsed.toDouble())?.let { MiraSettingValueValidation.Invalid(it) }
            ?: MiraSettingValueValidation.Valid(parsed.toString())
    }

    private fun validateDecimal(
        definition: MiraSettingDefinition,
        value: String,
    ): MiraSettingValueValidation {
        val parsed = value.toDoubleOrNull()
            ?: return MiraSettingValueValidation.Invalid("expected a decimal number")
        if (!parsed.isFinite()) {
            return MiraSettingValueValidation.Invalid("expected a finite decimal number")
        }
        return validateRange(definition, parsed)?.let { MiraSettingValueValidation.Invalid(it) }
            ?: MiraSettingValueValidation.Valid(parsed.toString())
    }

    private fun validateOption(
        definition: MiraSettingDefinition,
        value: String,
    ): MiraSettingValueValidation {
        val normalized = normalize(value)
        val direct = definition.options.firstOrNull { normalize(it) == normalized }
        val alias = definition.optionAliases.entries.firstOrNull { normalize(it.key) == normalized }?.value
        val resolved = direct ?: alias
        return resolved?.let(MiraSettingValueValidation::Valid)
            ?: MiraSettingValueValidation.Invalid("expected one of: ${definition.options.joinToString(", ")}")
    }

    private fun validateRange(definition: MiraSettingDefinition, value: Double): String? {
        definition.minimum?.let { minimum ->
            if (value < minimum) return "value must be >= ${formatNumber(minimum)}"
        }
        definition.maximum?.let { maximum ->
            if (value > maximum) return "value must be <= ${formatNumber(maximum)}"
        }
        return null
    }

    private fun matchScore(query: String, candidate: String): Int =
        when {
            query == candidate -> 100
            candidate.contains(query) -> 80
            query.contains(candidate) -> 65
            query.split(' ').filter(String::isNotBlank).all(candidate::contains) -> 50
            else -> 0
        }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("[\\s._-]+"), " ")

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private fun booleanSetting(
        id: String,
        label: String,
        description: String,
        aliases: Set<String>,
        scope: MiraSettingScope = MiraSettingScope.GLOBAL,
    ) = MiraSettingDefinition(id, label, description, aliases, MiraSettingValueType.BOOLEAN, scope)

    private fun integerSetting(
        id: String,
        label: String,
        description: String,
        aliases: Set<String>,
        minimum: Int,
        maximum: Int,
        scope: MiraSettingScope = MiraSettingScope.GLOBAL,
    ) = MiraSettingDefinition(
        id,
        label,
        description,
        aliases,
        MiraSettingValueType.INTEGER,
        scope,
        minimum = minimum.toDouble(),
        maximum = maximum.toDouble(),
    )

    private fun decimalSetting(
        id: String,
        label: String,
        description: String,
        aliases: Set<String>,
        minimum: Double,
        maximum: Double,
        scope: MiraSettingScope = MiraSettingScope.GLOBAL,
    ) = MiraSettingDefinition(
        id,
        label,
        description,
        aliases,
        MiraSettingValueType.DECIMAL,
        scope,
        minimum = minimum,
        maximum = maximum,
    )

    private fun optionSetting(
        id: String,
        label: String,
        description: String,
        aliases: Set<String>,
        options: Set<String>,
        optionAliases: Map<String, String>,
        scope: MiraSettingScope = MiraSettingScope.GLOBAL,
    ) = MiraSettingDefinition(
        id,
        label,
        description,
        aliases,
        MiraSettingValueType.OPTION,
        scope,
        options,
        optionAliases,
    )
}
