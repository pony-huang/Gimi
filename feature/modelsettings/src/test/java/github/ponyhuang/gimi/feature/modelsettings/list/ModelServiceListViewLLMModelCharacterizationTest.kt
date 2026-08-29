package github.ponyhuang.gimi.feature.modelsettings.list

import app.cash.turbine.test
import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelCatalogLoadStateUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelServicesUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.UpdateModelServiceUseCase
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelServiceListViewLLMModelCharacterizationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun queryFiltersByServiceIdOrNameIgnoringCase() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            assertEquals(CatalogLoadState.Loading, awaitItem().loadState)
            var state = awaitItem()
            while (state.items.size != 2) state = awaitItem()

            viewModel.onAction(ModelServiceListAction.QueryChanged("MINI"))
            do {
                state = awaitItem()
            } while (state.items.size != 1)
            assertEquals("minimax", state.items.single().id)

            viewModel.onAction(ModelServiceListAction.QueryChanged("深度"))
            do {
                state = awaitItem()
            } while (state.items.singleOrNull()?.id != "deepseek")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toggleDelegatesToRepository() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.onAction(ModelServiceListAction.EnabledChanged("deepseek", true))
        advanceUntilIdle()

        verify { repository.updateEnabled("deepseek", true) }
    }

    private fun viewModel(repository: ModelCatalogRepository) = LLMModelServiceListViewModel(
        observeServices = ObserveModelServicesUseCase(repository),
        observeLoadState = ObserveModelCatalogLoadStateUseCase(repository),
        updateModelService = UpdateModelServiceUseCase(repository),
        runWhenAgentIdle = RunWhenAgentIdleUseCase(FakeAgentRuntimeGate()),
    )

    private fun repository(): ModelCatalogRepository = mockk(relaxed = true) {
        every { observeServices() } returns MutableStateFlow(
            listOf(
                service("deepseek", "深度求索"),
                service("minimax", "MiniMax"),
            ),
        )
        every { observeLoadState() } returns MutableStateFlow(CatalogLoadState.Ready)
        every { updateEnabled(any(), any()) } returns true
    }

    private fun service(id: String, name: String) = LLMModelSetting(
        id = id,
        name = name,
        isEnabled = true,
        apiKey = "key",
        apiBaseUrl = "https://example.com",
        apiProtocol = ApiProtocol.Standard,
        anthropicBaseUrl = "https://example.com/anthropic",
        groups = emptyList(),
    )
}
