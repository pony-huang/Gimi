package github.ponyhuang.gimi.feature.plugin

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigFieldDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginConfigViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadPrefersStoredValueOverDefault() = runTest {
        val repository = FakePluginRepository(
            descriptor = PluginConfigDescriptor(
                fields = listOf(
                    PluginConfigFieldDescriptor(
                        key = "token",
                        label = "API Token",
                        kind = PluginConfigFieldDescriptor.Kind.TEXT,
                        secret = true,
                        defaultValue = "dflt",
                    ),
                ),
            ),
            stored = mapOf("token" to "stored"),
        )
        val viewModel = PluginConfigViewModel(repository)

        viewModel.load("zhihu")

        val field = viewModel.state.value.fields.single()
        assertEquals("token", field.key)
        assertEquals("stored", field.value)
        assertTrue(field.secret)
    }

    @Test
    fun loadFallsBackToDefaultWhenNoStoredValue() = runTest {
        val repository = FakePluginRepository(
            descriptor = PluginConfigDescriptor(
                fields = listOf(
                    PluginConfigFieldDescriptor(
                        key = "token",
                        label = "API Token",
                        kind = PluginConfigFieldDescriptor.Kind.TEXT,
                        defaultValue = "dflt",
                    ),
                ),
            ),
            stored = emptyMap(),
        )
        val viewModel = PluginConfigViewModel(repository)

        viewModel.load("zhihu")

        assertEquals("dflt", viewModel.state.value.fields.single().value)
    }

    @Test
    fun savePersistsEditedValues() = runTest {
        val repository = FakePluginRepository(
            descriptor = PluginConfigDescriptor(
                fields = listOf(
                    PluginConfigFieldDescriptor(
                        key = "token",
                        label = "API Token",
                        kind = PluginConfigFieldDescriptor.Kind.TEXT,
                    ),
                ),
            ),
            stored = emptyMap(),
        )
        val viewModel = PluginConfigViewModel(repository)
        viewModel.load("zhihu")

        viewModel.onAction(PluginConfigAction.SetValue("token", "abc"))
        viewModel.onAction(PluginConfigAction.Save)

        assertEquals("zhihu" to mapOf("token" to "abc"), repository.updatedConfig)
    }

    private class FakePluginRepository(
        private val descriptor: PluginConfigDescriptor,
        private val stored: Map<String, String>,
    ) : PluginRepository {
        val pluginsFlow = MutableStateFlow<List<PluginDescriptor>>(emptyList())
        override val plugins: StateFlow<List<PluginDescriptor>> = pluginsFlow
        override val revision: StateFlow<Long> = MutableStateFlow(0L)
        var updatedConfig: Pair<String, Map<String, String>>? = null

        override fun setEnabled(pluginId: String, enabled: Boolean) = Unit
        override fun configDescriptor(pluginId: String): PluginConfigDescriptor? = descriptor
        override fun configValues(pluginId: String): Map<String, String> = stored
        override fun updateConfig(pluginId: String, values: Map<String, String>) {
            updatedConfig = pluginId to values
        }
    }
}
