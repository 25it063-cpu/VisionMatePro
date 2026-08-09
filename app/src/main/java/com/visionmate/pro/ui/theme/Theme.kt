package com.visionmate.pro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background = BgDark,
    surface = SurfaceDark,
    primary = CyanAccent,
    secondary = SoftBlue,
    tertiary = SafeGreen,
    error = DangerRed,
    onBackground = TextWhite,
    onSurface = TextWhite
)

@Composable
fun VisionMateProTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
