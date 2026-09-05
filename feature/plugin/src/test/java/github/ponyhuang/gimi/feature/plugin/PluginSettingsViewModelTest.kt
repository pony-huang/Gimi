package github.ponyhuang.gimi.feature.plugin

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.plugin.model.PluginActionCallback
import github.ponyhuang.gimi.domain.plugin.model.PluginActionExecution
import github.ponyhuang.gimi.domain.plugin.model.PluginActionOutcome
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigDescriptor
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun refreshTogglesRefreshingIndicator() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakePluginRepository().apply { refreshGate = gate }
        val viewModel = PluginSettingsViewModel(repository)

        // uiState 是 WhileSubscribed 的 stateIn，需要订阅才会上游收集并 emit。
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        viewModel.onAction(PluginSettingsAction.Refresh)
        runCurrent()

        assertTrue("刷新进行中应显示下拉刷新指示器", viewModel.uiState.value.isRefreshing)

        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse("刷新结束后应关闭刷新指示器", viewModel.uiState.value.isRefreshing)
    }

    private class FakePluginRepository : PluginRepository {
        val pluginsFlow = MutableStateFlow<List<PluginDescriptor>>(emptyList())
        override val plugins: StateFlow<List<PluginDescriptor>> = pluginsFlow
        override val revision: StateFlow<Long> = MutableStateFlow(0L)
        val enabledCalls = mutableListOf<Pair<String, Boolean>>()
        var refreshResult: List<String> = emptyList()
        var refreshCalls: Int = 0
        var refreshGate: CompletableDeferred<Unit>? = null

        override fun setEnabled(pluginId: String, enabled: Boolean) {
            enabledCalls += pluginId to enabled
        }

        override fun configDescriptor(pluginId: String): PluginConfigDescriptor? = null
        override fun configValues(pluginId: String): Map<String, String> = emptyMap()
        override fun updateConfig(pluginId: String, values: Map<String, String>) = Unit
        override suspend fun runAction(pluginId: String, actionId: String): PluginActionExecution? = null

        override suspend fun refresh(): List<String> {
            refreshCalls += 1
            refreshGate?.await()
            return refreshResult
        }

        override suspend fun onActionCallback(
            pluginId: String,
            actionId: String,
            callback: PluginActionCallback,
        ): PluginActionOutcome? = null
    }
}
