package github.ponyhuang.asssistantai.data.conversation

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatDisplayRepository

/** Persistent preferences that control how conversation content is presented. */
@Singleton
class ChatDisplayPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : ChatDisplayRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _showToolActivity = MutableStateFlow(
        preferences.getBoolean(SHOW_TOOL_ACTIVITY_KEY, true),
    )
    // 键不存在即"跟随系统"，与官方深色主题文档推荐的默认项一致。
    private val _darkThemeOverride = MutableStateFlow(
        if (preferences.contains(DARK_THEME_OVERRIDE_KEY)) {
            preferences.getBoolean(DARK_THEME_OVERRIDE_KEY, false)
        } else {
            null
        },
    )

    override val showToolActivity: StateFlow<Boolean> = _showToolActivity.asStateFlow()
    override val darkThemeOverride: StateFlow<Boolean?> = _darkThemeOverride.asStateFlow()

    override fun setShowToolActivity(show: Boolean) {
        if (_showToolActivity.value == show) return
        _showToolActivity.value = show
        preferences.edit { putBoolean(SHOW_TOOL_ACTIVITY_KEY, show) }
    }

    override fun setDarkThemeOverride(enabled: Boolean) {
        if (_darkThemeOverride.value == enabled) return
        _darkThemeOverride.value = enabled
        preferences.edit { putBoolean(DARK_THEME_OVERRIDE_KEY, enabled) }
    }

    private companion object {
        const val PREFERENCES_NAME = "chat_display_preferences"
        const val SHOW_TOOL_ACTIVITY_KEY = "show_tool_activity"
        const val DARK_THEME_OVERRIDE_KEY = "dark_theme_override"
    }
}
