package com.davidbitner.watchlater.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Scheme = darkColorScheme(
    primary = Color(0xFF7CC4FF),
    onPrimary = Color(0xFF00243D),
    secondaryContainer = Color(0xFF26323D),
    onSecondaryContainer = Color(0xFFD4E8FA),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFE6E6EA),
    surface = Color(0xFF16161A),
    onSurface = Color(0xFFE6E6EA),
    surfaceVariant = Color(0xFF212127),
    onSurfaceVariant = Color(0xFF9E9EA8),
    outline = Color(0xFF3A3A42),
    error = Color(0xFFFF6B6B),
)

private val AppTypography = Typography(
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
fun WatchLaterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
