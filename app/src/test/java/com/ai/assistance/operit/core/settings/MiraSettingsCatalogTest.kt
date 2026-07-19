package com.ai.assistance.operit.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiraSettingsCatalogTest {
    @Test
    fun `natural Chinese aliases find stable setting ids`() {
        assertEquals("chat.auto_read", MiraSettingsCatalog.search("自动播放语音").first().id)
        assertEquals("chat.ai_input_suggestion", MiraSettingsCatalog.search("关闭自动生成回复").first().id)
        assertEquals("appearance.theme", MiraSettingsCatalog.search("夜间模式").first().id)
        assertEquals("companion.proactive_enabled", MiraSettingsCatalog.search("让她主动找我").first().id)
    }

    @Test
    fun `boolean values accept conversational commands`() {
        val definition = requireNotNull(MiraSettingsCatalog.findById("chat.immersive_mode"))

        assertEquals(
            MiraSettingValueValidation.Valid("true"),
            MiraSettingsCatalog.validateValue(definition, "开启"),
        )
        assertEquals(
            MiraSettingValueValidation.Valid("false"),
            MiraSettingsCatalog.validateValue(definition, "关闭"),
        )
    }

    @Test
    fun `option aliases are converted to canonical values`() {
        val definition = requireNotNull(MiraSettingsCatalog.findById("speech.expression_strength"))
        val companionAutoRead = requireNotNull(MiraSettingsCatalog.findById("speech.auto_read.companion"))

        assertEquals(
            MiraSettingValueValidation.Valid("VIVID"),
            MiraSettingsCatalog.validateValue(definition, "鲜明"),
        )
        assertEquals(
            MiraSettingValueValidation.Valid("INHERIT"),
            MiraSettingsCatalog.validateValue(companionAutoRead, "跟随全局"),
        )
    }

    @Test
    fun `numeric settings enforce the same bounds as the UI`() {
        val typingDelay = requireNotNull(MiraSettingsCatalog.findById("companion.typing_delay_ms"))
        val dailyLimit = requireNotNull(MiraSettingsCatalog.findById("companion.daily_limit"))
        val speechRate = requireNotNull(MiraSettingsCatalog.findById("speech.rate"))

        assertTrue(MiraSettingsCatalog.validateValue(typingDelay, "199") is MiraSettingValueValidation.Invalid)
        assertEquals(
            MiraSettingValueValidation.Valid("200"),
            MiraSettingsCatalog.validateValue(typingDelay, "200"),
        )
        assertTrue(MiraSettingsCatalog.validateValue(dailyLimit, "9") is MiraSettingValueValidation.Invalid)
        assertTrue(MiraSettingsCatalog.validateValue(speechRate, "NaN") is MiraSettingValueValidation.Invalid)
    }

    @Test
    fun `setting ids are unique and appearance settings use companion scope`() {
        assertEquals(
            MiraSettingsCatalog.definitions.size,
            MiraSettingsCatalog.definitions.map { it.id }.distinct().size,
        )
        assertTrue(
            MiraSettingsCatalog.definitions
                .filter { it.id.startsWith("appearance.") }
                .all { it.scope == MiraSettingScope.CURRENT_COMPANION },
        )
        assertEquals(
            MiraSettingScope.CURRENT_CONVERSATION,
            requireNotNull(MiraSettingsCatalog.findById("chat.auto_read")).scope,
        )
        assertEquals(
            MiraSettingScope.CURRENT_CONVERSATION,
            requireNotNull(MiraSettingsCatalog.findById("chat.memory_auto_update")).scope,
        )
        assertEquals(
            MiraSettingScope.CURRENT_COMPANION,
            requireNotNull(MiraSettingsCatalog.findById("speech.auto_read.companion")).scope,
        )
        assertEquals(
            MiraSettingScope.GLOBAL,
            requireNotNull(MiraSettingsCatalog.findById("speech.auto_read.global")).scope,
        )
        assertEquals(
            MiraSettingScope.GLOBAL,
            requireNotNull(MiraSettingsCatalog.findById("chat.ai_input_suggestion")).scope,
        )
    }

    @Test
    fun `catalog does not expose credentials or destructive data operations`() {
        val forbiddenTerms =
            setOf("api key", "api_key", "apikey", "access token", "access_token", "authorization", "password", "secret", "清空", "重置", "导入")

        MiraSettingsCatalog.definitions.forEach { definition ->
            val searchableText =
                buildList {
                    add(definition.id)
                    add(definition.label)
                    add(definition.description)
                    addAll(definition.aliases)
                }.joinToString(" ").lowercase()
            forbiddenTerms.forEach { term ->
                assertTrue("${definition.id} exposed forbidden term: $term", term !in searchableText)
            }
        }
    }
}
