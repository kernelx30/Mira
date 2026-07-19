package com.ai.assistance.operit.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiraAgentToolExposureTest {
    @Test
    fun `app tools are directly exposed to every Chinese chat turn`() {
        val tools = SystemToolPrompts.getAIAllCategoriesCn().flatMap { it.tools }

        assertEquals(1, tools.count { it.name == "start_app" })
        assertTrue(tools.any { it.name == "list_installed_apps" })
        assertTrue(
            tools.first { it.name == "start_app" }.description.contains("直接调用本工具"),
        )
    }

    @Test
    fun `memory export tool is exposed without model supplied parameters`() {
        val tools = SystemToolPrompts.getAIAllCategoriesCn().flatMap { it.tools }
        val exportTool = tools.single { it.name == "export_companion_memory" }

        assertTrue(exportTool.parametersStructured.orEmpty().isEmpty())
        assertTrue(exportTool.description.contains("Mira JSON 包"))
    }

    @Test
    fun `chat export tool is exposed with only an optional format`() {
        val tools = SystemToolPrompts.getAIAllCategoriesCn().flatMap { it.tools }
        val exportTool = tools.single { it.name == "export_chat_history" }

        assertEquals(listOf("format"), exportTool.parametersStructured.orEmpty().map { it.name })
        assertTrue(exportTool.description.contains("默认导出当前会话"))
        assertTrue(exportTool.description.contains("不要查找设置项"))
    }

    @Test
    fun `conversational settings tool is public and manageable`() {
        val publicToolsCn = SystemToolPrompts.getAIAllCategoriesCn().flatMap { it.tools }
        val publicToolsEn = SystemToolPrompts.getAIAllCategoriesEn().flatMap { it.tools }
        val manageableToolsCn = SystemToolPrompts.getManageableToolPrompts(useEnglish = false)
        val manageableToolsEn = SystemToolPrompts.getManageableToolPrompts(useEnglish = true)
        val expectedParameters = setOf("action", "query", "setting_id", "value")

        assertEquals(1, publicToolsCn.count { it.name == "manage_mira_settings" })
        assertEquals(1, publicToolsEn.count { it.name == "manage_mira_settings" })
        assertEquals(1, manageableToolsCn.count { it.name == "manage_mira_settings" })
        assertEquals(1, manageableToolsEn.count { it.name == "manage_mira_settings" })
        assertEquals(
            expectedParameters,
            publicToolsCn.single { it.name == "manage_mira_settings" }
                .parametersStructured.orEmpty().map { it.name }.toSet(),
        )
        assertEquals(
            expectedParameters,
            publicToolsEn.single { it.name == "manage_mira_settings" }
                .parametersStructured.orEmpty().map { it.name }.toSet(),
        )
    }
}
