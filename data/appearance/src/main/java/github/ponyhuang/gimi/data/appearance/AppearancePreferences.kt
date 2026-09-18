package github.ponyhuang.gimi.data.appearance

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.appearance.AppearanceRepository
import github.ponyhuang.gimi.domain.appearance.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 持久化外观偏好。以枚举名存 `theme_mode` 键，缺省即 [ThemeMode.SYSTEM]（跟随系统）。
 */
@Singleton
class AppearancePreferences @Inject constructor(
    @ApplicationContext context: Context,
) : AppearanceRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(readThemeMode())

    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    override fun setThemeMode(mode: ThemeMode) {
        if (_themeMode.value == mode) return
        _themeMode.value = mode
        preferences.edit { putString(THEME_MODE_KEY, mode.name) }
    }

    private fun readThemeMode(): ThemeMode {
        val stored = preferences.getString(THEME_MODE_KEY, null)
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    private companion object {
        const val PREFERENCES_NAME = "appearance_preferences"
        const val THEME_MODE_KEY = "theme_mode"
    }
}