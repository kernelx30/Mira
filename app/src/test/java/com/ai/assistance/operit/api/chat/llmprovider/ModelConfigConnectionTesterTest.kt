package com.ai.assistance.operit.api.chat.llmprovider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConfigConnectionTesterTest {
    @Test
    fun emptyReportIsNotSuccessful() {
        val report = reportWithItems()

        assertFalse(report.success)
    }

    @Test
    fun reportRequiresEveryTestToPass() {
        assertTrue(
            reportWithItems(
                ModelConnectionTestItem(ModelConnectionTestType.CHAT, success = true)
            ).success
        )
        assertFalse(
            reportWithItems(
                ModelConnectionTestItem(
                    ModelConnectionTestType.CHAT,
                    success = false,
                    error = "empty"
                )
            ).success
        )
    }

    private fun reportWithItems(
        vararg items: ModelConnectionTestItem
    ): ModelConnectionTestReport =
        ModelConnectionTestReport(
            configId = "test",
            configName = "Test",
            providerType = "test",
            requestedModelIndex = 0,
            actualModelIndex = 0,
            testedModelName = "test-model",
            items = items.toList()
        )
}
