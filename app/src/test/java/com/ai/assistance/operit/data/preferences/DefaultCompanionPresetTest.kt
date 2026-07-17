package com.ai.assistance.operit.data.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCompanionPresetTest {
    @Test
    fun chinesePreset_usesMiraAndKeepsVoiceRulesSeparate() {
        val preset = DefaultCompanionPreset.content(isChinese = true)

        assertTrue(preset.characterSetting.startsWith("你叫 Mira，18岁，女生。"))
        assertTrue(preset.characterSetting.contains("【相处与记忆】"))
        assertFalse(preset.characterSetting.contains("【语音节奏】"))
        assertTrue(preset.otherContentVoice.contains("【语音节奏】"))
        assertTrue(preset.openingStatement.isNotBlank())
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
}
