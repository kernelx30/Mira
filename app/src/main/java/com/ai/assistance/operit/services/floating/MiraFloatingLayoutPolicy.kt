package com.ai.assistance.operit.services.floating

data class FloatingCardPosition(val x: Int, val y: Int)

data class FloatingBallPosition(val x: Int, val y: Int)

object MiraFloatingLayoutPolicy {
    const val CARD_MAX_HEIGHT_DP = 260

    fun cardWidthDp(screenWidthDp: Int): Int = when {
        screenWidthDp <= 360 -> minOf(312, (screenWidthDp - 24).coerceAtLeast(0))
        screenWidthDp < 600 -> 336
        else -> 380
    }

    fun clampBallPosition(
        screenWidth: Int,
        screenHeight: Int,
        x: Int,
        y: Int,
        ballSize: Int,
        safeLeft: Int,
        safeTop: Int,
        safeRight: Int,
        safeBottom: Int,
        margin: Int,
    ): FloatingBallPosition {
        val minX = safeLeft + margin
        val maxX = (screenWidth - safeRight - margin - ballSize).coerceAtLeast(minX)
        val minY = safeTop + margin
        val maxY = (screenHeight - safeBottom - margin - ballSize).coerceAtLeast(minY)
        return FloatingBallPosition(
            x = x.coerceIn(minX, maxX),
            y = y.coerceIn(minY, maxY),
        )
    }

    fun nearestHorizontalEdgePosition(
        screenWidth: Int,
        screenHeight: Int,
        x: Int,
        y: Int,
        ballSize: Int,
        safeLeft: Int,
        safeTop: Int,
        safeRight: Int,
        safeBottom: Int,
        margin: Int,
    ): FloatingBallPosition {
        val clamped = clampBallPosition(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            x = x,
            y = y,
            ballSize = ballSize,
            safeLeft = safeLeft,
            safeTop = safeTop,
            safeRight = safeRight,
            safeBottom = safeBottom,
            margin = margin,
        )
        val leftX = safeLeft + margin
        val rightX = (screenWidth - safeRight - margin - ballSize).coerceAtLeast(leftX)
        val usableCenterX = (leftX + rightX + ballSize) / 2
        return clamped.copy(
            x = if (clamped.x + ballSize / 2 <= usableCenterX) leftX else rightX,
        )
    }

    fun anchoredCardPosition(
        screenWidth: Int,
        screenHeight: Int,
        ballX: Int,
        ballY: Int,
        ballSize: Int,
        cardWidth: Int,
        cardHeight: Int,
        safeLeft: Int,
        safeTop: Int,
        safeRight: Int,
        safeBottom: Int,
        gap: Int,
    ): FloatingCardPosition {
        val minX = safeLeft
        val maxX = (screenWidth - safeRight - cardWidth).coerceAtLeast(minX)
        val minY = safeTop
        val maxY = (screenHeight - safeBottom - cardHeight).coerceAtLeast(minY)
        val ballCenterX = ballX + ballSize / 2
        val ballCenterY = ballY + ballSize / 2
        val preferredX = if (ballCenterX <= screenWidth / 2) {
            ballX + ballSize + gap
        } else {
            ballX - cardWidth - gap
        }
        val preferredY = if (ballCenterY <= screenHeight / 2) {
            ballY
        } else {
            ballY + ballSize - cardHeight
        }
        return FloatingCardPosition(
            x = preferredX.coerceIn(minX, maxX),
            y = preferredY.coerceIn(minY, maxY),
        )
    }
}
