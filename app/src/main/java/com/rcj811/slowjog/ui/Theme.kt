package com.rcj811.slowjog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 「日出」配色：暖黑底 + 橘粉漸層，跑步時有朝氣但不刺眼。 */
object JogColors {
    val Bg = Color(0xFF12100E)
    val Surface = Color(0xFF1C1916)
    val SurfaceHigh = Color(0xFF27221E)
    val Line = Color(0xFF3A332D)
    val Text = Color(0xFFF6EFE9)
    val Muted = Color(0xFFA59B92)
    val Faint = Color(0xFF6E655D)

    val Sun1 = Color(0xFFFFB347)
    val Sun2 = Color(0xFFFF7A45)
    val Sun3 = Color(0xFFFF5E62)
    val Mint = Color(0xFF7BE0B5)
    val Sky = Color(0xFF8EC5FF)
    val Flame = Color(0xFFFF8A5B)

    val sunrise = Brush.linearGradient(listOf(Sun1, Sun2, Sun3))
    val sunriseH = Brush.horizontalGradient(listOf(Sun1, Sun3))
}

private val scheme = darkColorScheme(
    primary = JogColors.Sun2,
    onPrimary = Color(0xFF1A0E08),
    secondary = JogColors.Mint,
    background = JogColors.Bg,
    onBackground = JogColors.Text,
    surface = JogColors.Surface,
    onSurface = JogColors.Text,
    surfaceVariant = JogColors.SurfaceHigh,
    onSurfaceVariant = JogColors.Muted,
    outline = JogColors.Line,
    surfaceContainer = JogColors.Surface,
    surfaceContainerHigh = JogColors.SurfaceHigh,
)

private val type = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
    displayMedium = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum"),
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

@Composable
fun JogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
