package dev.sproutcode.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { LIGHT, DARK }

class AppPrefs private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private fun loadThemeMode(): ThemeMode {
        val name = prefs.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        return runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.DARK)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
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
