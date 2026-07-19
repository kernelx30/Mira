package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.navigation.RouteEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MainNavigationViewModelTest {
    @Test
    fun `retains router and back stack across host recreation`() {
        val viewModel = MainNavigationViewModel()
        val router = viewModel.getOrCreateRouterState(RouteEntry(routeId = "screen.chat"))
        router.navigate(routeId = "screen.settings")
        router.navigate(routeId = "screen.voice")

        val restored =
            viewModel.getOrCreateRouterState(RouteEntry(routeId = "screen.chat"))

        assertSame(router, restored)
        assertEquals("screen.voice", restored.currentEntry.routeId)
        assertEquals(
            listOf("screen.chat", "screen.settings", "screen.voice"),
            restored.backStack.map { it.routeId },
        )
    }
}
