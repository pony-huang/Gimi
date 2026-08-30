package github.ponyhuang.gimi.feature.memory

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.memory.model.MemoryConfiguration
import github.ponyhuang.gimi.domain.memory.repository.MemorySettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemorySettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `enabling without token keeps draft on and shows validation error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeMemorySettingsRepository()
            val viewModel = MemorySettingsViewModel(repository)
            // 先排空 repository 初始配置的收集，模拟真实 App 中收集器早于用户操作启动。
            advanceUntilIdle()

            viewModel.onAction(MemorySettingsAction.SetMem0Enabled(true))
            advanceUntilIdle()

            // 开关乐观置位以便展开 Token 配置区，但校验失败不落库。
            assertTrue(viewModel.uiState.value.mem0Enabled)
            assertTrue(viewModel.uiState.value.tokenError)
            assertEquals(MemoryConfiguration(), repository.configuration.value)
        }

    @Test
    fun `enabling with stored token persists immediately`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeMemorySettingsRepository(MemoryConfiguration(apiKey = "token"))
        val viewModel = MemorySettingsViewModel(repository)

        viewModel.onAction(MemorySettingsAction.SetMem0Enabled(true))
        advanceUntilIdle()

        assertEquals(
            MemoryConfiguration(mem0Enabled = true, apiKey = "token"),
            repository.configuration.value,
        )
        assertFalse(viewModel.uiState.value.tokenError)
    }

    @Test
    fun `master off discards unfinished mem0 draft without token`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeMemorySettingsRepository()
            val viewModel = MemorySettingsViewModel(repository)

            viewModel.onAction(MemorySettingsAction.SetMem0Enabled(true))
            viewModel.onAction(MemorySettingsAction.SetMemoryEnabled(false))
            advanceUntilIdle()

            assertEquals(
                MemoryConfiguration(memoryEnabled = false, mem0Enabled = false),
                repository.configuration.value,
            )
            assertFalse(viewModel.uiState.value.mem0Enabled)
        }

    @Test
    fun `disabling Mem0 persists immediately`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeMemorySettingsRepository(MemoryConfiguration(mem0Enabled = true, apiKey = "token"))
        val viewModel = MemorySettingsViewModel(repository)

        viewModel.onAction(MemorySettingsAction.SetMem0Enabled(false))
        advanceUntilIdle()

        assertEquals(MemoryConfiguration(mem0Enabled = false, apiKey = "token"), repository.configuration.value)
    }

    @Test
    fun `master switch off persists immediately`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeMemorySettingsRepository(MemoryConfiguration(mem0Enabled = true, apiKey = "token"))
        val viewModel = MemorySettingsViewModel(repository)

        viewModel.onAction(MemorySettingsAction.SetMemoryEnabled(false))
        advanceUntilIdle()

        assertEquals(
            MemoryConfiguration(memoryEnabled = false, mem0Enabled = true, apiKey = "token"),
            repository.configuration.value,
        )
        assertFalse(viewModel.uiState.value.memoryEnabled)
    }

    @Test
    fun `saving token keeps it in state for the password field`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeMemorySettingsRepository()
        val viewModel = MemorySettingsViewModel(repository)

        viewModel.effects.test {
            viewModel.onAction(MemorySettingsAction.SetToken(" token "))
            viewModel.onAction(MemorySettingsAction.SetMem0Enabled(true))
            viewModel.onAction(MemorySettingsAction.Save)

            assertEquals(MemorySettingsEffect.Saved, awaitItem())
            assertEquals(MemoryConfiguration(mem0Enabled = true, apiKey = "token"), repository.configuration.value)
            assertEquals("token", viewModel.uiState.value.token)
            assertTrue(viewModel.uiState.value.hasStoredToken)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class FakeMemorySettingsRepository(
    initialConfiguration: MemoryConfiguration = MemoryConfiguration(),
) : MemorySettingsRepository {
    private val mutableConfiguration = MutableStateFlow(initialConfiguration)
    override val configuration: StateFlow<MemoryConfiguration> = mutableConfiguration

    override suspend fun save(memoryEnabled: Boolean, mem0Enabled: Boolean, apiKey: String?) {
        val token = apiKey?.trim()?.takeIf(String::isNotEmpty) ?: mutableConfiguration.value.apiKey
        require(!mem0Enabled || token.isNotEmpty())
        mutableConfiguration.value = MemoryConfiguration(memoryEnabled, mem0Enabled, token)
    }
}
