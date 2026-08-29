package github.ponyhuang.gimi.data.appearance

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.appearance.AppearanceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 持久化外观偏好。`dark_theme_override` 键不存在即"跟随系统"；一旦用户拨动开关就写入
 * 明确的浅/深偏好。
 */
@Singleton
class AppearancePreferences @Inject constructor(
    @ApplicationContext context: Context,
) : AppearanceRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _darkThemeOverride = MutableStateFlow(
        if (preferences.contains(DARK_THEME_OVERRIDE_KEY)) {
            preferences.getBoolean(DARK_THEME_OVERRIDE_KEY, false)
        } else {
            null
        },
    )

    override val darkThemeOverride: StateFlow<Boolean?> = _darkThemeOverride.asStateFlow()

    override fun setDarkThemeOverride(enabled: Boolean) {
        if (_darkThemeOverride.value == enabled) return
        _darkThemeOverride.value = enabled
        preferences.edit { putBoolean(DARK_THEME_OVERRIDE_KEY, enabled) }
    }

    private companion object {
        const val PREFERENCES_NAME = "appearance_preferences"
        const val DARK_THEME_OVERRIDE_KEY = "dark_theme_override"
    }
}