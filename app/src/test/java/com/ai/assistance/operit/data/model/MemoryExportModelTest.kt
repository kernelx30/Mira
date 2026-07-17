package com.ai.assistance.operit.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryExportModelTest {
    @Test
    fun `version 1 memory without properties remains importable`() {
        val json =
            """
            {
              "uuid":"legacy",
              "title":"old memory",
              "content":"kept",
              "contentType":"text/plain",
              "source":"import",
              "credibility":0.8,
              "importance":0.5,
              "folderPath":null,
              "createdAt":1,
              "updatedAt":2,
              "tagNames":[]
            }
            """.trimIndent()

        val memory = Json.decodeFromString<SerializableMemory>(json)

        assertTrue(memory.propertyValues.isEmpty())
    }
}
