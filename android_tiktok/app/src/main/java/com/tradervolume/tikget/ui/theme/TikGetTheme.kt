package com.tradervolume.tikget.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TgCyan = Color(0xFF00F2EA)
val TgPink = Color(0xFFFE2C55)
val TgBg = Color(0xFF000000)
val TgSurface = Color(0xFF121212)
val TgSurface2 = Color(0xFF1C1C1C)
val TgOnSurface = Color(0xFFEDEDED)
val TgOnSurfaceVariant = Color(0xFFA8A8A8)

private val TgColors = darkColorScheme(
    primary = TgCyan,
    onPrimary = Color.Black,
    primaryContainer = TgCyan,
    onPrimaryContainer = Color.Black,
    secondary = TgPink,
    onSecondary = Color.White,
    background = TgBg,
    onBackground = TgOnSurface,
    surface = TgSurface,
    onSurface = TgOnSurface,
    surfaceVariant = TgSurface2,
    onSurfaceVariant = TgOnSurfaceVariant,
    outline = Color(0xFF2A2A2A)
)

@Composable
fun TikGetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TgColors, content = content)
}
