package dev.sproutcode.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import dev.sproutcode.app.data.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary    = TerminalPrimary,
    secondary  = TerminalSecondary,
    background = TerminalBackground,
    surface    = TerminalSurface,
    onSurface  = TerminalOnSurface,
    error      = TerminalError
)

private val LightColorScheme = lightColorScheme(
    primary    = LightPrimary,
    background = LightBackground,
    surface    = LightSurface,
    onSurface  = LightOnSurface,
    error      = LightError
)

@Composable
fun SproutCodeTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (themeMode == ThemeMode.DARK) DarkColorScheme else LightColorScheme,
        content     = content
    )
}
