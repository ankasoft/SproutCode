package dev.sproutcode.app.ui.theme

import androidx.compose.ui.graphics.Color
import dev.sproutcode.app.data.TerminalTheme

// Dark (terminal-ish)
val TerminalBackground = Color(0xFF0D1117)
val TerminalSurface    = Color(0xFF161B22)
val TerminalOnSurface  = Color(0xFFE6EDF3)
val TerminalPrimary    = Color(0xFF58A6FF)
val TerminalSecondary  = Color(0xFF3FB950)
val TerminalError      = Color(0xFFF85149)

// Light
val LightBackground = Color(0xFFFFFFFF)
val LightSurface    = Color(0xFFF6F8FA)
val LightOnSurface  = Color(0xFF1F2328)
val LightPrimary    = Color(0xFF0969DA)
val LightError      = Color(0xFFCF222E)

// Solarized Dark
val SolarizedDarkBackground = Color(0xFF002B36)
val SolarizedDarkSurface    = Color(0xFF073642)
val SolarizedDarkOnSurface  = Color(0xFF839496)
val SolarizedDarkPrimary    = Color(0xFF268BD2)
val SolarizedDarkSecondary  = Color(0xFF859900)
val SolarizedDarkError      = Color(0xFFDC322F)

// Solarized Light
val SolarizedLightBackground = Color(0xFFFDF6E3)
val SolarizedLightSurface    = Color(0xFFEEE8D5)
val SolarizedLightOnSurface  = Color(0xFF657B83)
val SolarizedLightPrimary    = Color(0xFF268BD2)
val SolarizedLightSecondary  = Color(0xFF859900)
val SolarizedLightError      = Color(0xFFDC322F)

// Dracula
val DraculaBackground = Color(0xFF282A36)
val DraculaSurface    = Color(0xFF44475A)
val DraculaOnSurface  = Color(0xFFF8F8F2)
val DraculaPrimary    = Color(0xFFBD93F9)
val DraculaSecondary  = Color(0xFF50FA7B)
val DraculaError      = Color(0xFFFF5555)

// Monokai
val MonokaiBackground = Color(0xFF272822)
val MonokaiSurface    = Color(0xFF3E3D32)
val MonokaiOnSurface  = Color(0xFFF8F8F2)
val MonokaiPrimary    = Color(0xFF66D9EF)
val MonokaiSecondary  = Color(0xFFA6E22E)
val MonokaiError      = Color(0xFFF92672)

data class TerminalColorScheme(
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val primary: Color,
    val secondary: Color,
    val error: Color
)

fun TerminalTheme.toColorScheme(): TerminalColorScheme = when (this) {
    TerminalTheme.DEFAULT_DARK -> TerminalColorScheme(
        TerminalBackground, TerminalSurface, TerminalOnSurface,
        TerminalPrimary, TerminalSecondary, TerminalError
    )
    TerminalTheme.DEFAULT_LIGHT -> TerminalColorScheme(
        LightBackground, LightSurface, LightOnSurface,
        LightPrimary, TerminalSecondary, LightError
    )
    TerminalTheme.SOLARIZED_DARK -> TerminalColorScheme(
        SolarizedDarkBackground, SolarizedDarkSurface, SolarizedDarkOnSurface,
        SolarizedDarkPrimary, SolarizedDarkSecondary, SolarizedDarkError
    )
    TerminalTheme.SOLARIZED_LIGHT -> TerminalColorScheme(
        SolarizedLightBackground, SolarizedLightSurface, SolarizedLightOnSurface,
        SolarizedLightPrimary, SolarizedLightSecondary, SolarizedLightError
    )
    TerminalTheme.DRACULA -> TerminalColorScheme(
        DraculaBackground, DraculaSurface, DraculaOnSurface,
        DraculaPrimary, DraculaSecondary, DraculaError
    )
    TerminalTheme.MONOKAI -> TerminalColorScheme(
        MonokaiBackground, MonokaiSurface, MonokaiOnSurface,
        MonokaiPrimary, MonokaiSecondary, MonokaiError
    )
}
