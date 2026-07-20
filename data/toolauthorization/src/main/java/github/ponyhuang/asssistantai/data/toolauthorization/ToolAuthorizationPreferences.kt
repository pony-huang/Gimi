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

    init {
        val initialized = preferences.getBoolean(INITIALIZED_KEY, false)
        val knownIds = preferences.getStringSet(KNOWN_IDS_KEY, mutableSetOf()).orEmpty()
        val enabledIds = if (!initialized) {
            currentIds
        } else {
            preferences.getStringSet(ENABLED_IDS_KEY, mutableSetOf()).orEmpty().intersect(currentIds)
        }
        persist(enabledIds, knownIds = currentIds)
        _tools = MutableStateFlow(descriptors(enabledIds))
    }

    override val tools: StateFlow<List<ToolDescriptor>> = _tools.asStateFlow()
    override val revision: StateFlow<Long> = _revision.asStateFlow()

    override fun enabledToolIds(): Set<String> = _tools.value
        .asSequence()
        .filter(ToolDescriptor::isEnabled)
        .mapTo(linkedSetOf(), ToolDescriptor::id)

    override fun setEnabled(toolId: String, enabled: Boolean) {
        if (toolId !in currentIds) return
        updateEnabledIds { current ->
            if (enabled) current + toolId else current - toolId
        }
    }

    override fun setAllEnabled(enabled: Boolean) {
        updateEnabledIds { if (enabled) currentIds else emptySet() }
    }

    private fun updateEnabledIds(transform: (Set<String>) -> Set<String>) {
        val current = enabledToolIds()
        val updated = transform(current).intersect(currentIds)
        if (updated == current) return
        persist(updated, knownIds = currentIds)
        _tools.value = descriptors(updated)
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

    private fun persist(enabledIds: Set<String>, knownIds: Set<String>) {
        preferences.edit {
            putBoolean(INITIALIZED_KEY, true)
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
    }
}
