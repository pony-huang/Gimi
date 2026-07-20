package github.ponyhuang.asssistantai.feature.modelsettings.defaults

import app.cash.turbine.test
import github.ponyhuang.asssistantai.core.testing.MainDispatcherRule
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.UpdateDefaultModelSettingsUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
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

        verify { repository.selectAssistantModel(selection) }
        verify { repository.selectTtsVoice("female-shaonv") }
        assertEquals(null, viewModel.uiState.value.dialog)
    }

    private fun viewModel(repository: ModelCatalogRepository) = DefaultModelSettingsViewModel(
        observeSettings = ObserveDefaultModelSettingsUseCase(repository),
        updateSettings = UpdateDefaultModelSettingsUseCase(repository),
    )

    private fun repository(): ModelCatalogRepository = mockk(relaxed = true) {
        every { observeServices() } returns MutableStateFlow(listOf(service()))
        every { observeAssistantSelection() } returns MutableStateFlow(null)
        every { observeFastSelection() } returns MutableStateFlow(null)
        every { observeSpeechSelection() } returns MutableStateFlow(null)
        every { observeTtsSelection() } returns MutableStateFlow(null)
        every { observeTtsVoice() } returns MutableStateFlow("female-shaonv")
    }

    private fun service() = ModelService(
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
