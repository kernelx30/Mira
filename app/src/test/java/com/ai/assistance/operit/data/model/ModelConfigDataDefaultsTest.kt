package com.ai.assistance.operit.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConfigDataDefaultsTest {
    @Test
    fun `legacy config without tool call field defaults to enabled`() {
        val config =
            Json { ignoreUnknownKeys = true }.decodeFromString<ModelConfigData>(
                """{"id":"default","name":"默认配置","modelName":"deepseek-v4-flash"}""",
            )

        assertTrue(config.enableToolCall)
    }
}
