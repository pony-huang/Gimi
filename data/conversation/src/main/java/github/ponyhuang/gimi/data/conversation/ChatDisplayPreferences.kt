package github.ponyhuang.gimi.data.conversation

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import github.ponyhuang.gimi.domain.conversation.repository.ChatDisplayRepository

/** Persistent preferences that control how conversation content is presented. */
@Singleton
class ChatDisplayPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : ChatDisplayRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _showToolActivity = MutableStateFlow(
        preferences.getBoolean(SHOW_TOOL_ACTIVITY_KEY, true),
    )

    override val showToolActivity: StateFlow<Boolean> = _showToolActivity.asStateFlow()

    override fun setShowToolActivity(show: Boolean) {
        if (_showToolActivity.value == show) return
        _showToolActivity.value = show
        preferences.edit { putBoolean(SHOW_TOOL_ACTIVITY_KEY, show) }
    }

    private companion object {
        const val PREFERENCES_NAME = "chat_display_preferences"
        const val SHOW_TOOL_ACTIVITY_KEY = "show_tool_activity"
    }
}