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
    primary = Color(0xFFC8283A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF7A0F1C),
    onPrimaryContainer = Color(0xFFFFDADD),
    secondary = Color(0xFFA8979A),
    onSecondary = Color(0xFF0B0809),
    secondaryContainer = Color(0xFF3A1319),
    onSecondaryContainer = Color(0xFFF2C9CE),
    background = Color(0xFF0B0809),
    onBackground = Color(0xFFEDE3E4),
    surface = Color(0xFF160E10),
    onSurface = Color(0xFFEDE3E4),
    surfaceVariant = Color(0xFF221518),
    onSurfaceVariant = Color(0xFFA8979A),
    surfaceTint = Color(0xFFC8283A),
    surfaceContainerLowest = Color(0xFF0B0809),
    surfaceContainerLow = Color(0xFF130C0E),
    surfaceContainer = Color(0xFF1C1214),
    surfaceContainerHigh = Color(0xFF241719),
    surfaceContainerHighest = Color(0xFF2C1D20),
    outline = Color(0xFF3A2226),
    outlineVariant = Color(0xFF2C1A1E),
    error = Color(0xFFFFB4AB),
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
