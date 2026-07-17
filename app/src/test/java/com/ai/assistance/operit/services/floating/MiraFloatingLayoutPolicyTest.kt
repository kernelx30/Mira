package com.ai.assistance.operit.services.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiraFloatingLayoutPolicyTest {
    @Test
    fun ballPositionKeepsHorizontalMovementInsideSafeBounds() {
        val position = MiraFloatingLayoutPolicy.clampBallPosition(
            screenWidth = 1080,
            screenHeight = 2400,
            x = 420,
            y = 760,
            ballSize = 156,
            safeLeft = 0,
            safeTop = 96,
            safeRight = 0,
            safeBottom = 72,
            margin = 24,
        )

        assertEquals(420, position.x)
        assertEquals(760, position.y)
    }

    @Test
    fun ballSnapsToNearestHorizontalEdgeAndPreservesY() {
        val left = MiraFloatingLayoutPolicy.nearestHorizontalEdgePosition(
            screenWidth = 1080,
            screenHeight = 2400,
            x = 280,
            y = 760,
            ballSize = 156,
            safeLeft = 0,
            safeTop = 96,
            safeRight = 0,
            safeBottom = 72,
            margin = 24,
        )
        val right = MiraFloatingLayoutPolicy.nearestHorizontalEdgePosition(
            screenWidth = 1080,
            screenHeight = 2400,
            x = 720,
            y = 760,
            ballSize = 156,
            safeLeft = 0,
            safeTop = 96,
            safeRight = 0,
            safeBottom = 72,
            margin = 24,
        )

        assertEquals(24, left.x)
        assertEquals(900, right.x)
        assertEquals(760, left.y)
        assertEquals(760, right.y)
    }

    @Test
    fun ballPositionAccountsForCutoutAndNavigationInsets() {
        val position = MiraFloatingLayoutPolicy.clampBallPosition(
            screenWidth = 1080,
            screenHeight = 2400,
            x = -200,
            y = 2500,
            ballSize = 156,
            safeLeft = 30,
            safeTop = 110,
            safeRight = 20,
            safeBottom = 90,
            margin = 24,
        )

        assertEquals(54, position.x)
        assertEquals(2130, position.y)
    }

    @Test
    fun cardWidthAdaptsWithoutTakingOverTheScreen() {
        assertEquals(296, MiraFloatingLayoutPolicy.cardWidthDp(320))
        assertEquals(312, MiraFloatingLayoutPolicy.cardWidthDp(360))
        assertEquals(336, MiraFloatingLayoutPolicy.cardWidthDp(411))
        assertEquals(380, MiraFloatingLayoutPolicy.cardWidthDp(840))
    }

    @Test
    fun leftTopBubbleExpandsInwardAndDown() {
        val position = MiraFloatingLayoutPolicy.anchoredCardPosition(
            screenWidth = 1080,
            screenHeight = 2400,
            ballX = 0,
            ballY = 300,
            ballSize = 156,
            cardWidth = 936,
            cardHeight = 684,
            safeLeft = 36,
            safeTop = 96,
            safeRight = 36,
            safeBottom = 72,
            gap = 24,
        )

        assertEquals(108, position.x)
        assertEquals(300, position.y)
    }

    @Test
    fun rightBottomBubbleExpandsLeftAndUpInsideSafeBounds() {
        val position = MiraFloatingLayoutPolicy.anchoredCardPosition(
            screenWidth = 1080,
            screenHeight = 2400,
            ballX = 924,
            ballY = 2200,
            ballSize = 156,
            cardWidth = 936,
            cardHeight = 684,
            safeLeft = 36,
            safeTop = 96,
            safeRight = 36,
            safeBottom = 72,
            gap = 24,
        )

        assertEquals(36, position.x)
        assertTrue(position.y <= 1644)
        assertTrue(position.y >= 96)
    }
}
