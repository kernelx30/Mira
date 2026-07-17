package com.ai.assistance.operit.ui.adaptive

enum class AdaptiveWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
    LARGE,
}

enum class AdaptiveHeightClass {
    COMPACT,
    NORMAL,
    EXPANDED,
}

enum class AdaptiveFontClass {
    NORMAL,
    LARGE,
    EXTRA_LARGE,
}

data class AdaptiveWindowMetrics(
    val widthDp: Int,
    val heightDp: Int,
    val fontScale: Float,
    val widthClass: AdaptiveWidthClass = widthClassFor(widthDp),
    val heightClass: AdaptiveHeightClass = heightClassFor(heightDp),
    val fontClass: AdaptiveFontClass = fontClassFor(fontScale),
) {
    val usesPermanentNavigation: Boolean
        get() = widthClass == AdaptiveWidthClass.EXPANDED || widthClass == AdaptiveWidthClass.LARGE

    val shouldCompactChatHeader: Boolean
        get() = widthDp < 400 || fontClass != AdaptiveFontClass.NORMAL

    val chatContentMaxWidthDp: Int
        get() = when (widthClass) {
            AdaptiveWidthClass.COMPACT -> widthDp
            AdaptiveWidthClass.MEDIUM -> 680
            AdaptiveWidthClass.EXPANDED -> 720
            AdaptiveWidthClass.LARGE -> 760
        }

    val drawerWidthDp: Int
        get() = when (widthClass) {
            AdaptiveWidthClass.COMPACT -> minOf(320, (widthDp - 24).coerceAtLeast(0))
            AdaptiveWidthClass.MEDIUM,
            AdaptiveWidthClass.EXPANDED -> 320
            AdaptiveWidthClass.LARGE -> 360
        }

    val usesFullScreenSheets: Boolean
        get() = widthClass == AdaptiveWidthClass.COMPACT && heightClass == AdaptiveHeightClass.COMPACT

    companion object {
        fun widthClassFor(widthDp: Int): AdaptiveWidthClass = when {
            widthDp < 600 -> AdaptiveWidthClass.COMPACT
            widthDp < 840 -> AdaptiveWidthClass.MEDIUM
            widthDp < 1200 -> AdaptiveWidthClass.EXPANDED
            else -> AdaptiveWidthClass.LARGE
        }

        fun heightClassFor(heightDp: Int): AdaptiveHeightClass = when {
            heightDp < 480 -> AdaptiveHeightClass.COMPACT
            heightDp < 800 -> AdaptiveHeightClass.NORMAL
            else -> AdaptiveHeightClass.EXPANDED
        }

        fun fontClassFor(fontScale: Float): AdaptiveFontClass = when {
            fontScale >= 1.5f -> AdaptiveFontClass.EXTRA_LARGE
            fontScale >= 1.3f -> AdaptiveFontClass.LARGE
            else -> AdaptiveFontClass.NORMAL
        }
    }
}
