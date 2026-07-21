package github.ponyhuang.asssistantai.data.toolauthorization

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDefinition
import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDescriptor
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.LocalToolDefinitionSource
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ToolAuthorizationPreferences @Inject constructor(
    @ApplicationContext context: Context,
    source: LocalToolDefinitionSource,
) : ToolAuthorizationRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val definitions = source.definitions().also(::requireUniqueIds)
    private val currentIds = definitions.mapTo(linkedSetOf(), ToolDefinition::id)
    private val _tools: MutableStateFlow<List<ToolDescriptor>>
    private val _revision = MutableStateFlow(0L)
    private val _isCustomizationEnabled: MutableStateFlow<Boolean>
    private var storedEnabledIds: Set<String>

    init {
        val initialized = preferences.getBoolean(INITIALIZED_KEY, false)
        val knownIds = preferences.getStringSet(KNOWN_IDS_KEY, mutableSetOf()).orEmpty()
        val customizeEnabled = preferences.getBoolean(CUSTOMIZE_ENABLED_KEY, false)
        storedEnabledIds = if (!initialized) {
            currentIds
        } else {
            preferences.getStringSet(ENABLED_IDS_KEY, mutableSetOf()).orEmpty().intersect(currentIds)
        }
        persist(storedEnabledIds, knownIds = currentIds, customizeEnabled = customizeEnabled)
        _isCustomizationEnabled = MutableStateFlow(customizeEnabled)
        _tools = MutableStateFlow(descriptors(effectiveEnabledIds()))
    }

    override val tools: StateFlow<List<ToolDescriptor>> = _tools.asStateFlow()
    override val revision: StateFlow<Long> = _revision.asStateFlow()
    override val isCustomizationEnabled: StateFlow<Boolean> = _isCustomizationEnabled.asStateFlow()

    override fun enabledToolIds(): Set<String> = effectiveEnabledIds()

    override fun setEnabled(toolId: String, enabled: Boolean) {
        if (toolId !in currentIds) return
        updateStoredEnabledIds { current ->
            if (enabled) current + toolId else current - toolId
        }
    }

    override fun setAllEnabled(enabled: Boolean) {
        updateStoredEnabledIds { if (enabled) currentIds else emptySet() }
    }

    override fun setCustomizationEnabled(enabled: Boolean) {
        if (_isCustomizationEnabled.value == enabled) return
        preferences.edit {
            putBoolean(CUSTOMIZE_ENABLED_KEY, enabled)
        }
        _isCustomizationEnabled.value = enabled
        _tools.value = descriptors(effectiveEnabledIds())
        _revision.value += 1
    }

    private fun effectiveEnabledIds(): Set<String> = if (_isCustomizationEnabled.value) {
        storedEnabledIds
    } else {
        currentIds
    }

    private fun updateStoredEnabledIds(transform: (Set<String>) -> Set<String>) {
        val updated = transform(storedEnabledIds).intersect(currentIds)
        if (updated == storedEnabledIds) return
        storedEnabledIds = updated
        persist(storedEnabledIds, knownIds = currentIds, customizeEnabled = _isCustomizationEnabled.value)
        _tools.value = descriptors(effectiveEnabledIds())
        _revision.value += 1
    }

    private fun descriptors(enabledIds: Set<String>): List<ToolDescriptor> = definitions.map { definition ->
        ToolDescriptor(
            id = definition.id,
            name = definition.name,
            description = definition.description,
            isEnabled = definition.id in enabledIds,
        )
    }

    private fun persist(enabledIds: Set<String>, knownIds: Set<String>, customizeEnabled: Boolean) {
        preferences.edit {
            putBoolean(INITIALIZED_KEY, true)
            putBoolean(CUSTOMIZE_ENABLED_KEY, customizeEnabled)
            putStringSet(ENABLED_IDS_KEY, enabledIds.toMutableSet())
            putStringSet(KNOWN_IDS_KEY, knownIds.toMutableSet())
        }
    }

    private fun requireUniqueIds(items: List<ToolDefinition>) {
        val duplicates = items.groupingBy(ToolDefinition::id).eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate local tool ids: ${duplicates.joinToString()}" }
    }

    private companion object {
        const val PREFERENCES_NAME = "tool_authorization"
        const val INITIALIZED_KEY = "initialized_v1"
        const val KNOWN_IDS_KEY = "known_ids_v1"
        const val ENABLED_IDS_KEY = "enabled_ids_v1"
        const val CUSTOMIZE_ENABLED_KEY = "customize_enabled_v1"
    }
}
