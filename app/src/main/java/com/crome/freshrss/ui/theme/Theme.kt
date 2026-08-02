package com.crome.freshrss.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** User-selectable appearance (Settings → Appearance). */
enum class AppThemeMode(val storageValue: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            entries.firstOrNull { it.storageValue == value?.lowercase() } ?: SYSTEM
    }
}

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
    surfaceVariant = Color(0xFF2F334D),
    primaryContainer = Color(0xFF3D59A1),
    onPrimaryContainer = Color(0xFFC0CAF5),
)

// Clean light counterpart (readable on white / off-white surfaces)
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E5AAC),
    onPrimary = Color.White,
    secondary = Color(0xFF6B4FA0),
    onSecondary = Color.White,
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1B26),
    onSurface = Color(0xFF1A1B26),
    onSurfaceVariant = Color(0xFF5C6370),
    error = Color(0xFFB00020),
    surfaceVariant = Color(0xFFE8EAF0),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF0D1B3A),
)

@Composable
fun FreshRssTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
