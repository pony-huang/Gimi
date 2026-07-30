package github.ponyhuang.gimi.feature.modelsettings.defaults

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.UpdateDefaultModelSettingsUseCase
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
class DefaultModelSettingsViewModelCharacterizationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun configuredModelsAreSeparatedByCapability() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            var state = awaitItem()
            while (state.chatModels.isEmpty()) state = awaitItem()

            assertEquals(listOf("chat"), state.chatModels.map { it.model.id })
            assertEquals(listOf("stt"), state.speechModels.map { it.model.id })
            assertEquals(listOf("tts"), state.ttsModels.map { it.model.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectionsDelegateToDomainRepositoryAndDismissDialog() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository)
        val selection = ModelSelection("service", "group", "chat")

        viewModel.onAction(DefaultModelSettingsAction.ShowDialog(DefaultModelDialog.Assistant))
        viewModel.onAction(
            DefaultModelSettingsAction.SelectModel(DefaultModelDialog.Assistant, selection),
        )
        viewModel.onAction(DefaultModelSettingsAction.SelectVoice("female-shaonv"))
        advanceUntilIdle()

        verify { repository.selectAssistantModel(selection) }
        verify { repository.selectTtsVoice("female-shaonv") }
        assertEquals(null, viewModel.uiState.value.dialog)
    }

    @Test
    fun ttsVoiceResetsToDefaultWhenTtsServiceChanges() = runTest {
        val repository = repository(
            ttsSelection = ModelSelection("service", "group", "tts"),
        )
        val viewModel = viewModel(repository)
        viewModel.awaitUiStateReady()
        val newSelection = ModelSelection("mimo", "group", "tts")

        viewModel.onAction(
            DefaultModelSettingsAction.SelectModel(DefaultModelDialog.Tts, newSelection),
        )
        advanceUntilIdle()

        verify { repository.selectTtsModel(newSelection) }
        verify { repository.selectTtsVoice("mimo_default") }
    }

    @Test
    fun ttsVoiceStaysUnchangedWhenTtsServiceStaysSame() = runTest {
        val repository = repository(
            ttsSelection = ModelSelection("service", "group", "tts"),
        )
        val viewModel = viewModel(repository)
        viewModel.awaitUiStateReady()
        val newSelection = ModelSelection("service", "group2", "tts2")

        viewModel.onAction(
            DefaultModelSettingsAction.SelectModel(DefaultModelDialog.Tts, newSelection),
        )
        advanceUntilIdle()

        verify { repository.selectTtsModel(newSelection) }
        verify(exactly = 0) { repository.selectTtsVoice(any()) }
    }

    private fun viewModel(repository: ModelCatalogRepository) = DefaultModelSettingsViewModel(
        observeSettings = ObserveDefaultModelSettingsUseCase(repository),
        updateSettings = UpdateDefaultModelSettingsUseCase(repository),
        runWhenAgentIdle = RunWhenAgentIdleUseCase(FakeAgentRuntimeGate()),
    )

    private suspend fun DefaultModelSettingsViewModel.awaitUiStateReady() {
        uiState.test {
            awaitItem() // initial empty state
            awaitItem() // state populated from repository
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun repository(
        ttsSelection: ModelSelection? = null,
    ): ModelCatalogRepository = mockk(relaxed = true) {
        every { observeServices() } returns MutableStateFlow(listOf(service()))
        every { observeAssistantSelection() } returns MutableStateFlow(null)
        every { observeFastSelection() } returns MutableStateFlow(null)
        every { observeSpeechSelection() } returns MutableStateFlow(null)
        every { observeTtsSelection() } returns MutableStateFlow(ttsSelection)
        every { observeTtsVoice() } returns MutableStateFlow("female-shaonv")
    }

    private fun service() = LLMModelSetting(
        id = "service",
        name = "Service",
        isEnabled = true,
        apiKey = "key",
        apiBaseUrl = "https://example.com",
        apiProtocol = ApiProtocol.Standard,
        anthropicBaseUrl = "https://example.com/anthropic",
        groups = listOf(
            ModelGroup(
                id = "group",
                name = "Group",
                models = listOf(
                    Model("chat", "Chat"),
                    Model("stt", "STT", isStt = true),
                    Model("tts", "TTS", isTts = true),
                ),
            ),
        ),
    )
}
