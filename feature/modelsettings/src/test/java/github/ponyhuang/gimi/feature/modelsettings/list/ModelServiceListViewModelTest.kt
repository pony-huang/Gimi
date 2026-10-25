package github.ponyhuang.gimi.feature.modelsettings.list

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelCatalogLoadStateUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelServicesUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.SetModelServiceEnabledUseCase
import github.ponyhuang.gimi.feature.modelsettings.R
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 列表启停必须经过任务锁、保持配置，并在失败或重复点击时保留一致状态。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelServiceListViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun toggleUsesAtomicEnableWriteAndPreservesConfiguration() = runTest {
        val fixture = fixture()
        val original = fixture.services.value.single()
        coEvery { fixture.repository.setServiceEnabled("deepseek", false) } coAnswers {
            fixture.services.value = listOf(original.copy(isEnabled = false)); true
        }
        fixture.viewModel.onAction(ModelServiceListAction.EnabledChanged("deepseek", false))
        runCurrent()
        assertEquals(original.copy(isEnabled = false), fixture.viewModel.uiState.value.items.single())
        assertTrue(fixture.viewModel.uiState.value.pendingServiceIds.isEmpty())
        coVerify(exactly = 1) { fixture.repository.setServiceEnabled("deepseek", false) }
        coVerify(exactly = 0) { fixture.repository.saveService(any()) }
    }

    @Test fun repeatedClicksAreIgnoredUntilWriteFinishes() = runTest {
        val fixture = fixture()
        val finished = CompletableDeferred<Boolean>()
        coEvery { fixture.repository.setServiceEnabled(any(), any()) } coAnswers { finished.await() }
        fixture.viewModel.onAction(ModelServiceListAction.EnabledChanged("deepseek", false))
        fixture.viewModel.onAction(ModelServiceListAction.EnabledChanged("deepseek", false))
        runCurrent()
        assertEquals(setOf("deepseek"), fixture.viewModel.uiState.value.pendingServiceIds)
        coVerify(exactly = 1) { fixture.repository.setServiceEnabled(any(), any()) }
        finished.complete(false)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.items.single().isEnabled)
        assertTrue(fixture.viewModel.uiState.value.pendingServiceIds.isEmpty())
        assertEquals(R.string.modelsettings_write_failed, fixture.viewModel.uiState.value.errorRes)
    }

    @Test fun exceptionKeepsPreviousStateAndAllowsRetry() = runTest {
        val fixture = fixture()
        coEvery { fixture.repository.setServiceEnabled(any(), any()) } throws IllegalStateException("storage")
        fixture.viewModel.onAction(ModelServiceListAction.EnabledChanged("deepseek", false))
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.items.single().isEnabled)
        assertTrue(fixture.viewModel.uiState.value.pendingServiceIds.isEmpty())
        assertEquals(R.string.modelsettings_write_failed, fixture.viewModel.uiState.value.errorRes)
        coEvery { fixture.repository.setServiceEnabled(any(), any()) } returns true
        fixture.viewModel.onAction(ModelServiceListAction.EnabledChanged("deepseek", false))
        runCurrent()
        assertEquals(null, fixture.viewModel.uiState.value.errorRes)
    }

    @Test fun busyAgentAndUnaddedProvidersCannotBeToggled() = runTest {
        val gate = FakeAgentRuntimeGate.busy()
        val fixture = fixture(gate)
        fixture.viewModel.onAction(ModelServiceListAction.EnabledChanged("deepseek", false))
        runCurrent()
        coVerify(exactly = 0) { fixture.repository.setServiceEnabled(any(), any()) }
        gate.state.value = FakeAgentRuntimeGate().state.value
        fixture.services.value = listOf(fixture.services.value.single().copy(isAdded = false))
        runCurrent()
        fixture.viewModel.onAction(ModelServiceListAction.EnabledChanged("deepseek", false))
        runCurrent()
        coVerify(exactly = 0) { fixture.repository.setServiceEnabled(any(), any()) }
    }

    @Test fun gateRechecksAgentThatStartsBeforeCoroutineWrites() = runTest {
        val gate = FakeAgentRuntimeGate()
        val fixture = fixture(gate)
        fixture.viewModel.onAction(ModelServiceListAction.EnabledChanged("deepseek", false))
        gate.state.value = FakeAgentRuntimeGate.busy().state.value
        runCurrent()
        coVerify(exactly = 0) { fixture.repository.setServiceEnabled(any(), any()) }
        assertFalse(fixture.viewModel.uiState.value.pendingServiceIds.contains("deepseek"))
        assertEquals(R.string.modelsettings_agent_mutation_blocked, fixture.viewModel.uiState.value.errorRes)
    }

    private fun TestScope.fixture(gate: FakeAgentRuntimeGate = FakeAgentRuntimeGate()): Fixture {
        val services = MutableStateFlow(listOf(LLMModelSetting("deepseek", "DeepSeek", true,
            apiKey = "test-key", apiBaseUrl = "https://api.deepseek.com",
            apiProtocol = ApiProtocol.OpenaiChatcompletions, anthropicBaseUrl = "https://api.deepseek.com/anthropic",
            groups = emptyList(), isAdded = true)))
        val repository = mockk<ModelCatalogRepository>(relaxed = true) {
            every { observeServices() } returns services
            every { observeLoadState() } returns MutableStateFlow(CatalogLoadState.Ready)
        }
        val vm = LLMModelServiceListViewModel(ObserveModelServicesUseCase(repository),
            ObserveModelCatalogLoadStateUseCase(repository), RunWhenAgentIdleUseCase(gate), SetModelServiceEnabledUseCase(repository))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        return Fixture(vm, repository, services)
    }

    /** 用仓库回流区分持久化配置与临时写入状态。 */
    private data class Fixture(val viewModel: LLMModelServiceListViewModel,
        val repository: ModelCatalogRepository, val services: MutableStateFlow<List<LLMModelSetting>>)
}
