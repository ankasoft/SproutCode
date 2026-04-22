package dev.sproutcode.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { LIGHT, DARK }

enum class TerminalTheme {
    DEFAULT_DARK,
    DEFAULT_LIGHT,
    SOLARIZED_DARK,
    SOLARIZED_LIGHT,
    DRACULA,
    MONOKAI
}

enum class TerminalFont {
    DEFAULT,
    JETBRAINS_MONO,
    FIRA_CODE,
    SOURCE_CODE_PRO
}

class AppPrefs private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _terminalTheme = MutableStateFlow(loadTerminalTheme())
    val terminalTheme: StateFlow<TerminalTheme> = _terminalTheme

    private val _terminalFont = MutableStateFlow(loadTerminalFont())
    val terminalFont: StateFlow<TerminalFont> = _terminalFont

    private fun loadThemeMode(): ThemeMode {
        val name = prefs.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        return runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.DARK)
    }

    private fun loadTerminalTheme(): TerminalTheme {
        val name = prefs.getString("terminal_theme", TerminalTheme.DEFAULT_DARK.name) ?: TerminalTheme.DEFAULT_DARK.name
        return runCatching { TerminalTheme.valueOf(name) }.getOrDefault(TerminalTheme.DEFAULT_DARK)
    }

    private fun loadTerminalFont(): TerminalFont {
        val name = prefs.getString("terminal_font", TerminalFont.DEFAULT.name) ?: TerminalFont.DEFAULT.name
        return runCatching { TerminalFont.valueOf(name) }.getOrDefault(TerminalFont.DEFAULT)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
    }

    fun setTerminalTheme(theme: TerminalTheme) {
        prefs.edit().putString("terminal_theme", theme.name).apply()
        _terminalTheme.value = theme
    }

    fun setTerminalFont(font: TerminalFont) {
        prefs.edit().putString("terminal_font", font.name).apply()
        _terminalFont.value = font
    }

    fun toggleTheme() {
        setThemeMode(if (_themeMode.value == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    companion object {
        @Volatile private var INSTANCE: AppPrefs? = null
        fun getInstance(context: Context): AppPrefs =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppPrefs(context.applicationContext).also { INSTANCE = it }
            }
    }
}
