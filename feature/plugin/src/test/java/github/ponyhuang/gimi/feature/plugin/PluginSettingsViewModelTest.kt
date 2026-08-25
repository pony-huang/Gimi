package github.ponyhuang.gimi.feature.plugin

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.plugin.model.PluginActionOutcome
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun setEnabledDelegatesToRepository() = runTest {
        val repository = FakePluginRepository()
        val viewModel = PluginSettingsViewModel(repository)

        viewModel.onAction(PluginSettingsAction.SetEnabled("zhihu", false))

        assertEquals(listOf("zhihu" to false), repository.enabledCalls)
    }

    private class FakePluginRepository : PluginRepository {
        val pluginsFlow = MutableStateFlow<List<PluginDescriptor>>(emptyList())
        override val plugins: StateFlow<List<PluginDescriptor>> = pluginsFlow
        override val revision: StateFlow<Long> = MutableStateFlow(0L)
        val enabledCalls = mutableListOf<Pair<String, Boolean>>()

        override fun setEnabled(pluginId: String, enabled: Boolean) {
            enabledCalls += pluginId to enabled
        }

        override fun configDescriptor(pluginId: String): PluginConfigDescriptor? = null
        override fun configValues(pluginId: String): Map<String, String> = emptyMap()
        override fun updateConfig(pluginId: String, values: Map<String, String>) = Unit
        override suspend fun runAction(pluginId: String, actionId: String): PluginActionOutcome? = null
    }
}
