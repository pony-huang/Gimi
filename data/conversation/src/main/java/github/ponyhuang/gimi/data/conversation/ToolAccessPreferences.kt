package github.ponyhuang.gimi.data.conversation

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.repository.ToolAccessRepository

/**
 * Persistent, app-wide tool loading preference.
 *
 * Defaults to [ToolAccessMode.ON_DEMAND] (load on demand, the recommended mode) and falls
 * back to it for any unknown/corrupt persisted value so the switch always reconciles to a
 * known enum.
 */
@Singleton
class ToolAccessPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : ToolAccessRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _defaultToolAccessMode = MutableStateFlow(readMode())

    override val defaultToolAccessMode: StateFlow<ToolAccessMode> =
        _defaultToolAccessMode.asStateFlow()

    override fun setDefaultToolAccessMode(mode: ToolAccessMode) {
        if (_defaultToolAccessMode.value == mode) return
        _defaultToolAccessMode.value = mode
        preferences.edit { putString(DEFAULT_TOOL_ACCESS_MODE_KEY, mode.name) }
    }

    private fun readMode(): ToolAccessMode {
        val name = preferences.getString(DEFAULT_TOOL_ACCESS_MODE_KEY, null)
        return ToolAccessMode.entries.firstOrNull { it.name == name }
            ?: ToolAccessMode.ON_DEMAND
    }

    private companion object {
        const val PREFERENCES_NAME = "tool_access_preferences"
        const val DEFAULT_TOOL_ACCESS_MODE_KEY = "default_tool_access_mode"
    }
}
