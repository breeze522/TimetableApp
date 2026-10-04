package com.example.timetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 浅色配色。
 *
 * 补充了 surfaceContainer / surfaceContainerLow 等容器色，
 * 避免 Material 组件在未指定时回退到默认的紫色调。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF5B5BD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E7FB),
    onPrimaryContainer = Color(0xFF26265C),
    secondary = Color(0xFF3E8FD4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1EFFA),
    onSecondaryContainer = Color(0xFF173A55),
    tertiary = Color(0xFF3EAE93),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDFF4EE),
    onTertiaryContainer = Color(0xFF14332B),
    error = Color(0xFFD2504B),
    onError = Color.White,
    errorContainer = Color(0xFFFBE3E2),
    onErrorContainer = Color(0xFF4A1512),
    background = Color(0xFFF7F8FB),
    onBackground = Color(0xFF14161C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0xFFF0F2F6),
    onSurfaceVariant = Color(0xFF5C6472),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FB),
    surfaceContainer = Color(0xFFF2F4F8),
    surfaceContainerHigh = Color(0xFFECEEF4),
    surfaceContainerHighest = Color(0xFFE6E9F0),
    outline = Color(0xFFD5DAE3),
    outlineVariant = Color(0xFFE7EAF0),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9B9BF2),
    onPrimary = Color(0xFF1B1B4A),
    primaryContainer = Color(0xFF37377A),
    onPrimaryContainer = Color(0xFFE7E7FB),
    secondary = Color(0xFF7FB9E8),
    onSecondary = Color(0xFF0E2536),
    secondaryContainer = Color(0xFF22455F),
    onSecondaryContainer = Color(0xFFE1EFFA),
    tertiary = Color(0xFF71CCB4),
    onTertiary = Color(0xFF0C251F),
    tertiaryContainer = Color(0xFF1F473D),
    onTertiaryContainer = Color(0xFFDFF4EE),
    error = Color(0xFFE88C88),
    onError = Color(0xFF3E1210),
    errorContainer = Color(0xFF5C2320),
    onErrorContainer = Color(0xFFFBE3E2),
    background = Color(0xFF0E1015),
    onBackground = Color(0xFFE4E7EE),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFE4E7EE),
    surfaceVariant = Color(0xFF232833),
    onSurfaceVariant = Color(0xFF9BA3B2),
    surfaceContainerLowest = Color(0xFF0A0C10),
    surfaceContainerLow = Color(0xFF12151B),
    surfaceContainer = Color(0xFF181C24),
    surfaceContainerHigh = Color(0xFF1F242E),
    surfaceContainerHighest = Color(0xFF262C38),
    outline = Color(0xFF3A414F),
    outlineVariant = Color(0xFF272D38),
    scrim = Color(0xFF000000),
)

/** 统一形状：对话框、卡片、输入框都用同一套圆角语言 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun TimetableTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
