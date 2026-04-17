package com.tradervolume.ytdl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val YdRed = Color(0xFFFF0033)
val YdRedDark = Color(0xFFCC0029)
val YdGreen = Color(0xFF9CFF8A)
val YdBg = Color(0xFF0A0A0A)
val YdSurface = Color(0xFF141414)
val YdSurface2 = Color(0xFF1E1E1E)
val YdOnSurface = Color(0xFFE8E8E8)
val YdOnSurfaceVariant = Color(0xFFB5B5B5)

private val YdColors = darkColorScheme(
    primary = YdRed,
    onPrimary = Color.White,
    primaryContainer = YdRedDark,
    onPrimaryContainer = Color.White,
    secondary = YdGreen,
    onSecondary = Color.Black,
    background = YdBg,
    onBackground = YdOnSurface,
    surface = YdSurface,
    onSurface = YdOnSurface,
    surfaceVariant = YdSurface2,
    onSurfaceVariant = YdOnSurfaceVariant,
    outline = Color(0xFF2A2A2A)
)

@Composable
fun YDownTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YdColors,
        content = content
    )
}
