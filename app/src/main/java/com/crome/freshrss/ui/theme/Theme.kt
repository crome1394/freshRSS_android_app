package com.crome.freshrss.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Tokyo Night–ish palette close to the quickshell bar aesthetic
private val Accent = Color(0xFF7AA2F7)
private val DarkBg = Color(0xFF1A1B26)
private val DarkSurface = Color(0xFF24283B)
private val DarkOn = Color(0xFFC0CAF5)
private val DarkMuted = Color(0xFF565F89)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A1B26),
    secondary = Color(0xFFBB9AF7),
    background = DarkBg,
    surface = DarkSurface,
    onBackground = DarkOn,
    onSurface = DarkOn,
    onSurfaceVariant = DarkMuted,
    error = Color(0xFFF7768E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E5AAC),
    secondary = Color(0xFF6B4FA0),
)

@Composable
fun FreshRssTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
