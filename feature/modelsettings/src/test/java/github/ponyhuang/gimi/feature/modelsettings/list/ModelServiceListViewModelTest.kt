package github.ponyhuang.gimi.feature.modelsettings.list

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelCatalogLoadStateUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelInstancesUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 服务列表投影：实例流与目录加载状态按序组合；搜索词只改列表状态。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelServiceListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun instancesAndLoadStateAreCombinedIntoListState() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            var state = awaitItem()
            while (state.items.isEmpty()) state = awaitItem()
            assertEquals(CatalogLoadState.Ready, state.loadState)
            assertEquals(listOf("OpenAI 个人"), state.items.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun queryChangesUpdateListStateWithoutTouchingRepository() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository)
        viewModel.uiState.test {
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onAction(ModelServiceListAction.QueryChanged("openai"))

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.query.isEmpty()) state = awaitItem()
            assertEquals("openai", state.query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun viewModel(repository: ModelCatalogRepository) = ModelServiceListViewModel(
        observeInstances = ObserveModelInstancesUseCase(repository),
        observeLoadState = ObserveModelCatalogLoadStateUseCase(repository),
    )

    private fun repository(): ModelCatalogRepository = mockk(relaxed = true) {
        every { observeInstances() } returns MutableStateFlow(listOf(instance()))
        every { observeLoadState() } returns MutableStateFlow(CatalogLoadState.Ready)
    }

    private fun instance() = ModelServiceInstance(
        id = "instance-openai",
        name = "OpenAI 个人",
        templateId = "openai",
        isEnabled = true,
        isOfficialToolsEnabled = true,
        sharedApiKey = "sk-key",
        connections = listOf(
            ServiceConnection(
                id = "conn-conversation",
                capability = ServiceCapability.Conversation,
                protocol = ApiProtocol.OpenaiChatcompletions,
                endpoint = "https://api.openai.com/v1",
                models = listOf(Model("gpt-4.1", "gpt-4.1")),
            ),
        ),
    )
}
