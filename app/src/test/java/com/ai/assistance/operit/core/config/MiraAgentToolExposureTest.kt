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
}
