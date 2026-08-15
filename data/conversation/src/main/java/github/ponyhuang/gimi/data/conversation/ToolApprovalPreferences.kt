package github.ponyhuang.gimi.data.conversation

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.conversation.repository.ToolApprovalRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persistent preferences for tool confirmation policy (always-allow whitelist / full access). */
@Singleton
class ToolApprovalPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : ToolApprovalRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _alwaysAllowedToolNames = MutableStateFlow(
        preferences.getStringSet(ALWAYS_ALLOWED_TOOL_NAMES_KEY, emptySet()).orEmpty(),
    )
    private val _fullAccess = MutableStateFlow(
        preferences.getBoolean(FULL_ACCESS_KEY, false),
    )

    override val alwaysAllowedToolNames: StateFlow<Set<String>> = _alwaysAllowedToolNames.asStateFlow()
    override val fullAccess: StateFlow<Boolean> = _fullAccess.asStateFlow()

    @Synchronized
    override fun setAlwaysAllowed(toolName: String) {
        if (toolName.isBlank()) return
        val updated = _alwaysAllowedToolNames.value + toolName
        _alwaysAllowedToolNames.value = updated
        preferences.edit { putStringSet(ALWAYS_ALLOWED_TOOL_NAMES_KEY, updated) }
    }

    @Synchronized
    override fun removeAlwaysAllowed(toolName: String) {
        val updated = _alwaysAllowedToolNames.value - toolName
        if (updated == _alwaysAllowedToolNames.value) return
        _alwaysAllowedToolNames.value = updated
        preferences.edit { putStringSet(ALWAYS_ALLOWED_TOOL_NAMES_KEY, updated) }
    }

    override fun setFullAccess(enabled: Boolean) {
        if (_fullAccess.value == enabled) return
        _fullAccess.value = enabled
        preferences.edit { putBoolean(FULL_ACCESS_KEY, enabled) }
    }

    override fun isAutoApproved(toolName: String): Boolean =
        _fullAccess.value || toolName in _alwaysAllowedToolNames.value

    private companion object {
        const val PREFERENCES_NAME = "tool_approval_preferences"
        const val ALWAYS_ALLOWED_TOOL_NAMES_KEY = "always_allowed_tool_names_v1"
        const val FULL_ACCESS_KEY = "full_access_v1"
    }
}
