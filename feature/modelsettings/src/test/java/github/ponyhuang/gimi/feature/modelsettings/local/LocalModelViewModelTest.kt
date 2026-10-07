package github.ponyhuang.gimi.feature.modelsettings.local

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.*
import github.ponyhuang.gimi.domain.modelcatalog.repository.LocalModelRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalModelViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val model = LocalModelState(LocalModelVariant("cpu", "gemma4", "Gemma 4", LocalModelBackend.CPU, 20), LocalModelDownloadStatus.Ready)
    private val catalog = MutableStateFlow(LocalModelCatalogState(false, listOf(model)))
    private val repository = mockk<LocalModelRepository>(relaxed = true) { every { state } returns catalog }

    private val runtime = mockk<github.ponyhuang.gimi.domain.modelcatalog.repository.LocalModelRuntime>(relaxed = true)

    @Test fun requestingOrDismissingRemovalNeverDeletesFile() = runTest {
        val vm = LocalModelViewModel(repository, runtime, RunWhenAgentIdleUseCase(FakeAgentRuntimeGate()))
        vm.uiState.test {
            awaitItem(); runCurrent()
            vm.onAction(LocalModelAction.RequestRemoval("cpu")); runCurrent()
            assertEquals("cpu", vm.uiState.value.pendingRemoval?.variant?.id)
            coVerify(exactly = 0) { runtime.remove(any()) }
            vm.onAction(LocalModelAction.DismissRemoval); runCurrent()
            assertNull(vm.uiState.value.pendingRemoval)
            coVerify(exactly = 0) { runtime.remove(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun confirmationDeletesOnlyPendingVersionOnce() = runTest {
        val vm = LocalModelViewModel(repository, runtime, RunWhenAgentIdleUseCase(FakeAgentRuntimeGate()))
        vm.onAction(LocalModelAction.ConfirmRemoval); runCurrent()
        coVerify(exactly = 0) { runtime.remove(any()) }
        vm.onAction(LocalModelAction.RequestRemoval("cpu"))
        vm.onAction(LocalModelAction.ConfirmRemoval); runCurrent()
        vm.onAction(LocalModelAction.ConfirmRemoval); runCurrent()
        coVerify(exactly = 1) { runtime.remove("cpu") }
    }

    @Test fun activeAgentBlocksRemoval() = runTest {
        val vm = LocalModelViewModel(repository, runtime, RunWhenAgentIdleUseCase(FakeAgentRuntimeGate.busy()))
        vm.onAction(LocalModelAction.RequestRemoval("cpu"))
        vm.onAction(LocalModelAction.ConfirmRemoval); runCurrent()
        coVerify(exactly = 0) { runtime.remove(any()) }
    }

    @Test fun cancellingDownloadsDoesNotDeleteReadyModels() = runTest {
        val vm = LocalModelViewModel(repository, runtime, RunWhenAgentIdleUseCase(FakeAgentRuntimeGate()))
        vm.onAction(LocalModelAction.Download("gpu")); runCurrent()
        vm.onAction(LocalModelAction.CancelDownload("gpu")); runCurrent()
        coVerify(exactly = 1) { repository.download("gpu") }
        coVerify(exactly = 1) { repository.cancelDownload("gpu") }
        coVerify(exactly = 0) { runtime.remove(any()) }
    }
}
