package com.ai.assistance.operit.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveWindowMetricsTest {
    @Test
    fun widthBreakpointsMatchMiraLayoutContract() {
        assertEquals(AdaptiveWidthClass.COMPACT, AdaptiveWindowMetrics.widthClassFor(599))
        assertEquals(AdaptiveWidthClass.MEDIUM, AdaptiveWindowMetrics.widthClassFor(600))
        assertEquals(AdaptiveWidthClass.MEDIUM, AdaptiveWindowMetrics.widthClassFor(839))
        assertEquals(AdaptiveWidthClass.EXPANDED, AdaptiveWindowMetrics.widthClassFor(840))
        assertEquals(AdaptiveWidthClass.EXPANDED, AdaptiveWindowMetrics.widthClassFor(1199))
        assertEquals(AdaptiveWidthClass.LARGE, AdaptiveWindowMetrics.widthClassFor(1200))
    }

    @Test
    fun heightBreakpointsDriveCompactSurfaces() {
        assertEquals(AdaptiveHeightClass.COMPACT, AdaptiveWindowMetrics.heightClassFor(479))
        assertEquals(AdaptiveHeightClass.NORMAL, AdaptiveWindowMetrics.heightClassFor(480))
        assertEquals(AdaptiveHeightClass.NORMAL, AdaptiveWindowMetrics.heightClassFor(799))
        assertEquals(AdaptiveHeightClass.EXPANDED, AdaptiveWindowMetrics.heightClassFor(800))
    }

    @Test
    fun drawerAndChatWidthsStayBounded() {
        val phone = AdaptiveWindowMetrics(320, 640, 1f)
        val tablet = AdaptiveWindowMetrics(900, 1000, 1f)
        val desktop = AdaptiveWindowMetrics(1400, 1000, 1f)

        assertEquals(296, phone.drawerWidthDp)
        assertEquals(320, tablet.drawerWidthDp)
        assertEquals(360, desktop.drawerWidthDp)
        assertEquals(720, tablet.chatContentMaxWidthDp)
        assertEquals(760, desktop.chatContentMaxWidthDp)
        assertFalse(phone.usesPermanentNavigation)
        assertTrue(tablet.usesPermanentNavigation)
    }

    @Test
    fun largeFontsCompactTheChatHeader() {
        assertFalse(AdaptiveWindowMetrics(411, 800, 1.15f).shouldCompactChatHeader)
        assertTrue(AdaptiveWindowMetrics(411, 800, 1.3f).shouldCompactChatHeader)
        assertTrue(AdaptiveWindowMetrics(360, 800, 1f).shouldCompactChatHeader)
    }
}
