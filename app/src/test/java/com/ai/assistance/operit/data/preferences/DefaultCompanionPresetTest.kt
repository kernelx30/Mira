package com.ai.assistance.operit.data.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCompanionPresetTest {
    @Test
    fun chinesePreset_usesKernelx30VirtualAssistantAndKeepsVoiceRulesSeparate() {
        val preset = DefaultCompanionPreset.content(isChinese = true)

        assertTrue(preset.characterSetting.startsWith("你叫 Mira，是由 kernelx30 设计的虚拟助手"))
        assertTrue(preset.characterSetting.contains("【产品身份】"))
        assertTrue(preset.characterSetting.contains("独立安装与发布的 Android 应用"))
        assertTrue(preset.characterSetting.contains("不运行在 Operit 平台上"))
        assertTrue(preset.characterSetting.contains("不依赖用户安装 Operit"))
        assertTrue(preset.characterSetting.contains("不是 Operit 内的一套角色配置"))
        assertTrue(preset.characterSetting.contains("Mira 是开源项目"))
        assertTrue(preset.characterSetting.contains("https://github.com/kernelx30/Mira"))
        assertTrue(preset.characterSetting.contains("不要声称 Mira 未开源"))
        assertTrue(preset.characterSetting.contains("【Mira 软件向导】"))
        assertTrue(preset.characterSetting.contains("【相处与记忆】"))
        assertTrue(preset.characterSetting.contains("【主动陪伴】"))
        assertTrue(preset.characterSetting.contains("【Skill 与工具】"))
        assertTrue(preset.otherContentChat.contains("结果返回前不宣称完成"))
        assertFalse(preset.characterSetting.contains("雨城"))
        assertFalse(preset.characterSetting.contains("花店"))
        assertFalse(preset.characterSetting.contains("摄影"))
        assertFalse(preset.characterSetting.contains("【语音节奏】"))
        assertTrue(preset.otherContentVoice.contains("【语音节奏】"))
        assertTrue(preset.otherContentVoice.contains("用户插话时立即停下来听"))
        assertTrue(
            preset.openingStatement ==
                "嗨，我是 Mira，你的私人 AI 助手。今天想聊什么或想一起做什么？",
        )
        assertFalse(preset.openingStatement.contains("kernelx30", ignoreCase = true))
    }

    @Test
    fun untouchedPreviousMatePreset_isEligibleForMigration() {
        val previous = DefaultCompanionPreset.previousMateContent(isChinese = true)

        assertTrue(
            DefaultCompanionPreset.matchesPreviousMatePreset(
                name = DefaultCompanionPreset.PREVIOUS_NAME,
                description = previous.description,
                characterSetting = previous.characterSetting,
                openingStatement = previous.openingStatement,
                otherContentChat = previous.otherContentChat,
                otherContentVoice = previous.otherContentVoice,
            ),
        )
    }

    @Test
    fun editedPreviousMatePreset_isNotEligibleForMigration() {
        val previous = DefaultCompanionPreset.previousMateContent(isChinese = true)

        assertFalse(
            DefaultCompanionPreset.matchesPreviousMatePreset(
                name = DefaultCompanionPreset.PREVIOUS_NAME,
                description = previous.description,
                characterSetting = previous.characterSetting,
                openingStatement = previous.openingStatement,
                otherContentChat = previous.otherContentChat + " 用户自己的修改。",
                otherContentVoice = previous.otherContentVoice,
            ),
        )
    }

    @Test
    fun untouchedPreviousMiraPreset_isEligibleForMigration() {
        val previous = DefaultCompanionPreset.previousMiraContent(isChinese = true)

        assertTrue(
            DefaultCompanionPreset.matchesPreviousMiraPreset(
                name = DefaultCompanionPreset.NAME,
                description = previous.description,
                characterSetting = previous.characterSetting,
                openingStatement = previous.openingStatement,
                otherContentChat = previous.otherContentChat,
                otherContentVoice = previous.otherContentVoice,
            ),
        )
    }

    @Test
    fun editedPreviousMiraPreset_isNotEligibleForMigration() {
        val previous = DefaultCompanionPreset.previousMiraContent(isChinese = true)

        assertFalse(
            DefaultCompanionPreset.matchesPreviousMiraPreset(
                name = DefaultCompanionPreset.NAME,
                description = previous.description,
                characterSetting = previous.characterSetting + "\n用户自己的修改。",
                openingStatement = previous.openingStatement,
                otherContentChat = previous.otherContentChat,
                otherContentVoice = previous.otherContentVoice,
            ),
        )
    }

    @Test
    fun untouchedProductDraftMiraPreset_isEligibleForMigration() {
        val previous = DefaultCompanionPreset.previousMiraProductDraftContent(isChinese = true)

        assertTrue(
            DefaultCompanionPreset.matchesPreviousMiraPreset(
                name = DefaultCompanionPreset.NAME,
                description = previous.description,
                characterSetting = previous.characterSetting,
                openingStatement = previous.openingStatement,
                otherContentChat = previous.otherContentChat,
                otherContentVoice = previous.otherContentVoice,
            ),
        )
    }

    @Test
    fun untouchedPreviousAppGuideMiraPreset_isEligibleForMigration() {
        val previous = DefaultCompanionPreset.previousMiraAppGuideContent(isChinese = true)

        assertTrue(
            DefaultCompanionPreset.matchesPreviousMiraPreset(
                name = DefaultCompanionPreset.NAME,
                description = previous.description,
                characterSetting = previous.characterSetting,
                openingStatement = previous.openingStatement,
                otherContentChat = previous.otherContentChat,
                otherContentVoice = previous.otherContentVoice,
            ),
        )
    }

    @Test
    fun untouchedPreviousProductIdentityMiraPreset_isEligibleForMigration() {
        val previous = DefaultCompanionPreset.previousMiraProductIdentityContent(isChinese = true)

        assertTrue(
            DefaultCompanionPreset.matchesPreviousMiraPreset(
                name = DefaultCompanionPreset.NAME,
                description = previous.description,
                characterSetting = previous.characterSetting,
                openingStatement = previous.openingStatement,
                otherContentChat = previous.otherContentChat,
                otherContentVoice = previous.otherContentVoice,
            ),
        )
    }

    @Test
    fun untouchedPreviousOpenSourceMiraPreset_isEligibleForMigration() {
        val previous = DefaultCompanionPreset.previousMiraOpenSourceContent(isChinese = true)

        assertTrue(
            DefaultCompanionPreset.matchesPreviousMiraPreset(
                name = DefaultCompanionPreset.NAME,
                description = previous.description,
                characterSetting = previous.characterSetting,
                openingStatement = previous.openingStatement,
                otherContentChat = previous.otherContentChat,
                otherContentVoice = previous.otherContentVoice,
            ),
        )
    }

    @Test
    fun englishPreset_identifiesKernelx30AndCoversProductGuidance() {
        val preset = DefaultCompanionPreset.content(isChinese = false)

        assertTrue(preset.characterSetting.contains("designed by kernelx30"))
        assertTrue(preset.characterSetting.contains("[Product identity]"))
        assertTrue(preset.characterSetting.contains("does not run on the Operit platform"))
        assertTrue(preset.characterSetting.contains("does not require Operit to be installed"))
        assertTrue(preset.characterSetting.contains("Mira is open source"))
        assertTrue(preset.characterSetting.contains("https://github.com/kernelx30/Mira"))
        assertTrue(preset.characterSetting.contains("Never claim that Mira is closed source"))
        assertTrue(preset.characterSetting.contains("[Mira product guide]"))
        assertTrue(preset.characterSetting.contains("[Proactive companionship]"))
        assertTrue(preset.characterSetting.contains("[Skills and tools]"))
        assertFalse(preset.characterSetting.contains("flower shop"))
        assertFalse(preset.characterSetting.contains("[Speech rhythm]"))
        assertTrue(preset.otherContentVoice.contains("[Speech rhythm]"))
        assertTrue(
            preset.openingStatement ==
                "Hi, I'm Mira, your personal AI assistant. What would you like to talk about or work on today?",
        )
        assertFalse(preset.openingStatement.contains("kernelx30", ignoreCase = true))
    }
}
