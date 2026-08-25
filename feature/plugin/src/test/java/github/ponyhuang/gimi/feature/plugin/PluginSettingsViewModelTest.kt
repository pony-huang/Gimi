package github.ponyhuang.gimi.feature.plugin

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.plugin.model.PluginActionOutcome
import github.ponyhuang.gimi.domain.plugin.model.PluginBrowserRequest
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
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

    @Test
    fun refreshDelegatesAndEmitsAddedEffect() = runTest {
        val repository = FakePluginRepository().apply { refreshResult = listOf("spotify") }
        val viewModel = PluginSettingsViewModel(repository)

        viewModel.effects.test {
            viewModel.onAction(PluginSettingsAction.Refresh)

            assertEquals(
                PluginSettingsEffect.ShowPluginAdded(listOf("spotify")),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun refreshWithNoAdditionsEmitsNothing() = runTest {
        val repository = FakePluginRepository() // refreshResult 为空
        val viewModel = PluginSettingsViewModel(repository)

        viewModel.onAction(PluginSettingsAction.Refresh)
        advanceUntilIdle()

        assertEquals(1, repository.refreshCalls)
    }

    private class FakePluginRepository : PluginRepository {
        val pluginsFlow = MutableStateFlow<List<PluginDescriptor>>(emptyList())
        override val plugins: StateFlow<List<PluginDescriptor>> = pluginsFlow
        override val revision: StateFlow<Long> = MutableStateFlow(0L)
        val enabledCalls = mutableListOf<Pair<String, Boolean>>()
        var refreshResult: List<String> = emptyList()
        var refreshCalls: Int = 0

        override fun setEnabled(pluginId: String, enabled: Boolean) {
            enabledCalls += pluginId to enabled
        }

        override fun configDescriptor(pluginId: String): PluginConfigDescriptor? = null
        override fun configValues(pluginId: String): Map<String, String> = emptyMap()
        override fun updateConfig(pluginId: String, values: Map<String, String>) = Unit
        override suspend fun runAction(pluginId: String, actionId: String): PluginActionOutcome? = null

        override suspend fun refresh(): List<String> {
            refreshCalls += 1
            return refreshResult
        }

        override fun configActionBrowserRequest(pluginId: String, actionId: String): PluginBrowserRequest? = null

        override suspend fun completeAction(
            pluginId: String,
            actionId: String,
            redirectUrl: String,
        ): PluginActionOutcome? = null
    }
}
