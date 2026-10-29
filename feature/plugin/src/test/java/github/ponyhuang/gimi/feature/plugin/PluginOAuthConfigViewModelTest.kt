package github.ponyhuang.gimi.feature.plugin

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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginOAuthConfigViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun checksTheDeclaredProviderAndRefreshesAfterActionsFinish() = runTest {
        val repository = FakeRepository()
        val viewModel = PluginOAuthConfigViewModel(repository)
        viewModel.bind("provider-a", enabled = true, busy = false)
        advanceUntilIdle()
        assertEquals(PluginOAuthAuthorization.Authorized, viewModel.state.value.authorization)
        assertEquals("provider-a" to "oauth_status", repository.lastAction)
        viewModel.bind("provider-a", enabled = true, busy = true)
        repository.read = { PluginActionOutcome("unauthorized") }
        viewModel.bind("provider-a", enabled = true, busy = false)
        advanceUntilIdle()
        assertEquals(PluginOAuthAuthorization.Unauthorized, viewModel.state.value.authorization)
    }

    @Test
    fun failureAndUnknownProtocolRemainUnknownUntilSuccessfulRetry() = runTest {
        val repository = FakeRepository()
        repository.read = { PluginActionOutcome("authorized", success = false) }
        val viewModel = PluginOAuthConfigViewModel(repository)
        viewModel.bind("provider-b", enabled = true, busy = false)
        advanceUntilIdle()
        assertEquals(PluginOAuthAuthorization.Failed, viewModel.state.value.authorization)
        repository.read = { PluginActionOutcome("unexpected") }
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(PluginOAuthAuthorization.Failed, viewModel.state.value.authorization)
        repository.read = { PluginActionOutcome("authorized") }
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(PluginOAuthAuthorization.Authorized, viewModel.state.value.authorization)
    }

    @Test
    fun switchingProviderCancelsPreviousQueryAndUnsupportedPluginsAreNotQueried() = runTest {
        val repository = FakeRepository()
        val pending = CompletableDeferred<PluginActionOutcome>()
        repository.read = { pending.await() }
        val viewModel = PluginOAuthConfigViewModel(repository)
        viewModel.bind("provider-a", enabled = true, busy = false)
        runCurrent()
        viewModel.bind("provider-b", enabled = false, busy = false)
        pending.complete(PluginActionOutcome("authorized"))
        advanceUntilIdle()
        assertEquals("provider-b", viewModel.state.value.pluginId)
        assertEquals(false, viewModel.state.value.enabled)
        assertEquals("provider-a" to "oauth_status", repository.lastAction)
    }

    @Test
    fun pendingStatusUpdatesAutomaticallyAndStopsAfterAuthorization() = runTest {
        val repository = FakeRepository()
        var calls = 0
        repository.read = { PluginActionOutcome(if (++calls < 3) "pending" else "authorized") }
        val viewModel = PluginOAuthConfigViewModel(repository)
        viewModel.bind("provider", enabled = true, busy = false)
        runCurrent()
        assertEquals(PluginOAuthAuthorization.Waiting, viewModel.state.value.authorization)
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(PluginOAuthAuthorization.Authorized, viewModel.state.value.authorization)
        assertEquals(3, calls)
    }

    @Test
    fun expiredStatusIsNotReportedAsAuthorizationFailure() = runTest {
        val repository = FakeRepository()
        repository.read = { PluginActionOutcome("expired") }
        val viewModel = PluginOAuthConfigViewModel(repository)
        viewModel.bind("provider", enabled = true, busy = false)
        advanceUntilIdle()
        assertEquals(PluginOAuthAuthorization.Expired, viewModel.state.value.authorization)
    }

    private class FakeRepository : PluginRepository {
        override val plugins = MutableStateFlow<List<PluginDescriptor>>(emptyList())
        override val revision = MutableStateFlow(0L)
        var read: suspend () -> PluginActionOutcome = { PluginActionOutcome("authorized") }
        var lastAction: Pair<String, String>? = null
        override suspend fun runAction(pluginId: String, actionId: String): PluginActionExecution {
            lastAction = pluginId to actionId
            return PluginActionExecution.Completed(read())
        }
        override fun setEnabled(pluginId: String, enabled: Boolean) = Unit
        override fun configDescriptor(pluginId: String): PluginConfigDescriptor? = null
        override fun configValues(pluginId: String): Map<String, String> = emptyMap()
        override fun updateConfig(pluginId: String, values: Map<String, String>) = Unit
        override suspend fun refresh(): List<String> = emptyList()
        override suspend fun onActionCallback(
            pluginId: String,
            actionId: String,
            callback: PluginActionCallback,
        ): PluginActionOutcome? = null
    }
}
