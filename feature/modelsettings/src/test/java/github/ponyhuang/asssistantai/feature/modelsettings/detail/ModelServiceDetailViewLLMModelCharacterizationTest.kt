package github.ponyhuang.asssistantai.feature.modelsettings.detail

import app.cash.turbine.test
import github.ponyhuang.asssistantai.core.testing.MainDispatcherRule
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelServiceRemoteGateway
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.LoadModelServiceUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.ObserveModelServiceUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.RefreshModelCatalogUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.TestModelServiceConnectionUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.UpdateModelServiceUseCase
import github.ponyhuang.asssistantai.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.asssistantai.feature.modelsettings.TestAgentRuntimeGate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelServiceDetailViewLLMModelCharacterizationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadServiceExpandsGroupsAndToggleHidesChildren() = runTest {
        val provider = service(apiKey = "key")
        val fixture = fixture(provider)

        fixture.viewModel.uiState.test {
            assertTrue(awaitItem().isLoading)
            fixture.viewModel.onAction(LLmModelSettingDetailAction.Load(provider.id))

            var state = awaitItem()
            while (state.service == null) state = awaitItem()
            assertEquals(2, state.rows.size)
            assertEquals(
                LLMModelSettingDetailRow.GroupHeader("chat", "Chat", isExpanded = true),
                state.rows.first(),
            )

            fixture.viewModel.onAction(LLmModelSettingDetailAction.ToggleGroup("chat"))
            do {
                state = awaitItem()
            } while (state.rows.size != 1)
            assertEquals(
                listOf(LLMModelSettingDetailRow.GroupHeader("chat", "Chat", isExpanded = false)),
                state.rows,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun newlySynchronizedGroupsAreExpanded() = runTest {
        val provider = service(apiKey = "key")
        val fixture = fixture(provider)
        fixture.viewModel.onAction(LLmModelSettingDetailAction.Load(provider.id))
        advanceUntilIdle()

        fixture.services.value = provider.copy(
            groups = listOf(
                ModelGroup(
                    id = "MiniMax",
                    name = "MiniMax",
                    models = listOf(Model("MiniMax-M2.7", "MiniMax-M2.7")),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(
            listOf(
                LLMModelSettingDetailRow.GroupHeader("MiniMax", "MiniMax", isExpanded = true),
                LLMModelSettingDetailRow.LLMModelItem(
                    "MiniMax",
                    Model("MiniMax-M2.7", "MiniMax-M2.7"),
                ),
            ),
            fixture.viewModel.uiState.value.rows,
        )
    }

    @Test
    fun missingServiceRequestsCloseAndShowsExistingMessage() = runTest {
        val fixture = fixture(null)

        fixture.viewModel.onAction(LLmModelSettingDetailAction.Load("missing"))
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.shouldClose)
        assertEquals(LLMModelSettingDetailNotice.SettingNotFoundLLM, state.notice)
    }

    @Test
    fun blankApiKeyReturnsFailureWithoutRequest() = runTest {
        val provider = service(apiKey = "")
        val fixture = fixture(provider)
        fixture.viewModel.onAction(LLmModelSettingDetailAction.Load(provider.id))
        advanceUntilIdle()

        fixture.viewModel.onAction(LLmModelSettingDetailAction.TestConnection)
        advanceUntilIdle()

        assertEquals(
            LLMModelSettingDetailNotice.ConnectionFailed,
            fixture.viewModel.uiState.value.notice,
        )
        coVerify(exactly = 0) { fixture.remote.validateConnection(any()) }
    }

    @Test
    fun apiKeyChangeUpdatesStateAndRepository() = runTest {
        val provider = service(apiKey = "old")
        val fixture = fixture(provider)
        fixture.viewModel.onAction(LLmModelSettingDetailAction.Load(provider.id))
        advanceUntilIdle()

        fixture.viewModel.onAction(LLmModelSettingDetailAction.ApiKeyChanged("new"))
        advanceUntilIdle()

        assertEquals("new", fixture.viewModel.uiState.value.service?.apiKey)
        io.mockk.verify { fixture.repository.updateApiKey(provider.id, "new") }
    }

    @Test
    fun protocolChangeToUnsupportedProtocolIsRejected() = runTest {
        val provider = service(apiKey = "key").copy(
            supportedProtocols = listOf(ApiProtocol.Standard),
        )
        val fixture = fixture(provider)
        fixture.viewModel.onAction(LLmModelSettingDetailAction.Load(provider.id))
        advanceUntilIdle()

        fixture.viewModel.onAction(
            LLmModelSettingDetailAction.ApiProtocolChanged(ApiProtocol.Anthropic),
        )
        advanceUntilIdle()

        assertEquals(ApiProtocol.Standard, fixture.viewModel.uiState.value.service?.apiProtocol)
        io.mockk.verify(exactly = 0) {
            fixture.repository.updateApiProtocol(any(), ApiProtocol.Anthropic)
        }
    }

    @Test
    fun protocolChangeToSupportedProtocolUpdatesStateAndRepository() = runTest {
        val provider = service(apiKey = "key")
        val fixture = fixture(provider)
        fixture.viewModel.onAction(LLmModelSettingDetailAction.Load(provider.id))
        advanceUntilIdle()

        fixture.viewModel.onAction(
            LLmModelSettingDetailAction.ApiProtocolChanged(ApiProtocol.Anthropic),
        )
        advanceUntilIdle()

        assertEquals(ApiProtocol.Anthropic, fixture.viewModel.uiState.value.service?.apiProtocol)
        io.mockk.verify {
            fixture.repository.updateApiProtocol(provider.id, ApiProtocol.Anthropic)
        }
    }

    @Test
    fun officialToolSelectionUpdatesStateAndRepository() = runTest {
        val provider = service(apiKey = "key").copy(
            supportedOfficialTools = listOf("web_search", "kimi_formulas"),
            enabledOfficialTools = setOf("web_search", "kimi_formulas"),
        )
        val fixture = fixture(provider)
        fixture.viewModel.onAction(LLmModelSettingDetailAction.Load(provider.id))
        advanceUntilIdle()

        fixture.viewModel.onAction(
            LLmModelSettingDetailAction.OfficialToolEnabledChanged("web_search", false),
        )
        advanceUntilIdle()

        assertEquals(
            setOf("kimi_formulas"),
            fixture.viewModel.uiState.value.service?.enabledOfficialTools,
        )
        io.mockk.verify {
            fixture.repository.updateOfficialToolEnabled(provider.id, "web_search", false)
        }
    }

    private fun fixture(service: LLMModelSetting?): Fixture {
        val services = MutableStateFlow(service)
        val repository = mockk<ModelCatalogRepository>(relaxed = true) {
            coEvery { awaitReady() } returns Unit
            every { currentService(any()) } answers {
                service?.takeIf { it.id == firstArg() }
            }
            every { observeService(any()) } returns services
            every { updateEnabled(any(), any()) } returns true
        }
        val remote = mockk<ModelServiceRemoteGateway>(relaxed = true)
        val viewModel = ModelServiceDetailViewModel(
            loadModelService = LoadModelServiceUseCase(repository),
            observeModelService = ObserveModelServiceUseCase(repository),
            updateModelService = UpdateModelServiceUseCase(repository),
            testConnection = TestModelServiceConnectionUseCase(remote),
            refreshCatalog = RefreshModelCatalogUseCase(repository, remote),
            runWhenAgentIdle = RunWhenAgentIdleUseCase(TestAgentRuntimeGate()),
        )
        return Fixture(viewModel, repository, remote, services)
    }

    private fun service(apiKey: String): LLMModelSetting = LLMModelSetting(
        id = "deepseek",
        name = "DeepSeek",
        isEnabled = true,
        apiKey = apiKey,
        apiBaseUrl = "https://api.deepseek.com",
        apiProtocol = ApiProtocol.Standard,
        anthropicBaseUrl = "https://api.deepseek.com/anthropic",
        groups = listOf(
            ModelGroup(
                id = "chat",
                name = "Chat",
                models = listOf(Model("deepseek-chat", "deepseek-chat")),
            ),
        ),
    )

    private data class Fixture(
        val viewModel: ModelServiceDetailViewModel,
        val repository: ModelCatalogRepository,
        val remote: ModelServiceRemoteGateway,
        val services: MutableStateFlow<LLMModelSetting?>,
    )
}
