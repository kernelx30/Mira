package com.ai.assistance.operit.ui.floating

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ColorScheme

/**
 * 为悬浮窗提供的独立主题
 * 使用静态颜色，避免对Activity上下文的依赖
 */
@Composable
fun FloatingWindowTheme(
    colorScheme: ColorScheme? = null,
    typography: Typography? = null,
    content: @Composable () -> Unit
) {
    // 使用静态颜色，匹配动态主题的默认值
    val finalColorScheme = colorScheme ?: lightColorScheme(
        // 主要颜色
        primary = Color(0xFF167A75),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB4E6E1),
        onPrimaryContainer = Color(0xFF00201E),

        // 次要颜色
        secondary = Color(0xFFE88A9A),
        onSecondary = Color(0xFF1D2527),
        secondaryContainer = Color(0xFFFFD9DF),
        onSecondaryContainer = Color(0xFF3F0013),

        // 第三颜色
        tertiary = Color(0xFFF3C55D),
        onTertiary = Color(0xFF3B2F00),
        tertiaryContainer = Color(0xFFFFE9AD),
        onTertiaryContainer = Color(0xFF251A00),

        // 错误颜色
        error = Color(0xFFB3261E),                  // 标准Material错误色
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),         // 浅红色容器
        onErrorContainer = Color(0xFF410E0B),       // 深红色文本

        // 背景和表面
        background = Color(0xFFFFF8EE),
        onBackground = Color(0xFF1D2527),
        surface = Color(0xFFFFF8EE),
        onSurface = Color(0xFF1D2527),
        surfaceVariant = Color(0xFFE7ECE8),
        onSurfaceVariant = Color(0xFF44504E),

        // 轮廓
        outline = Color(0xFF6F7C79)
    )
    
    // 创建调整大小后的默认Typography，如果没有传入typography参数则使用此默认值
    val defaultSmallTypography = Typography(
        // 正文大字号
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.5.sp
        ),
        // 正文中字号 
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.25.sp
        ),
        // 正文小字号 
        bodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.4.sp
        ),
        // 标签小字号
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.5.sp
        ),
        // 标题小字号
        titleSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.5.sp
        ),
        // 按钮文本样式
        labelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        // 按钮大文本样式
        labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.5.sp
        )
    )

    // 优先使用传入的typography，如果没有则使用默认的小型typography
    val finalTypography = typography ?: defaultSmallTypography
    
    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = finalTypography,
        content = content
    )
}
