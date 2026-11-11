package github.ponyhuang.gimi.feature.modelsettings.defaults

import app.cash.turbine.test
import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.RealtimeVoiceOverride
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.UpdateDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSupport
import github.ponyhuang.gimi.domain.voice.repository.SpeechConnectionProbe
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityNotSupported
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import io.mockk.coEvery
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
    fun ttsVoiceIsKeptWhenItIsNotInTheSelectedModelCatalog() = runTest {
        val voices = listOf(TtsVoice(id = "voice_a", name = "Voice A", language = null, gender = null))
        val ttsVoiceRepository = mockk<TtsVoiceRepository> {
            coEvery { getVoices(any()) } returns voices
        }
        val repository = repository(
            ttsSelection = ModelSelection("service", "group", "tts"),
            ttsVoiceFlow = MutableStateFlow("female-shaonv"),
        )
        val viewModel = viewModel(repository, ttsVoiceRepository)
        viewModel.awaitUiStateReady()
        advanceUntilIdle()

        // 音色集并不封闭（基础音色、声音复刻音色都不在内置表里），按目录判定「失效」会误伤
        // 合法配置，因此改由选择器提示用户，ViewModel 不得静默改写已选音色。
        verify(exactly = 0) { repository.selectTtsVoice(any()) }
    }

    @Test
    fun ttsVoiceStaysUnchangedWhenItStillBelongsToSelectedModel() = runTest {
        val voices = listOf(TtsVoice(id = "female-shaonv", name = "SHAONV", language = null, gender = null))
        val ttsVoiceRepository = mockk<TtsVoiceRepository> {
            coEvery { getVoices(any()) } returns voices
        }
        val repository = repository(
            ttsSelection = ModelSelection("service", "group", "tts"),
            ttsVoiceFlow = MutableStateFlow("female-shaonv"),
        )
        val viewModel = viewModel(repository, ttsVoiceRepository)
        viewModel.awaitUiStateReady()
        advanceUntilIdle()

        verify(exactly = 0) { repository.selectTtsVoice(any()) }
    }

    @Test
    fun ttsVoiceOptionsAreLoadedFromRepository() = runTest {
        val customVoices = listOf(
            TtsVoice(id = "custom_voice_1", name = "Custom Voice 1", language = "中文", gender = "女声", description = "测试音色"),
        )
        val ttsVoiceRepository: TtsVoiceRepository = mockk {
            coEvery { getVoices(any()) } returns customVoices
        }
        val repository = repository(
            ttsSelection = ModelSelection("service", "group", "tts"),
        )
        val viewModel = viewModel(repository, ttsVoiceRepository)

        viewModel.uiState.test {
            awaitItem()
            var state = awaitItem()
            while (state.ttsVoiceOptions.none { it.id == "custom_voice_1" }) {
                state = awaitItem()
            }
            assertEquals("custom_voice_1", state.ttsVoiceOptions.first().id)
            assertEquals("测试音色", state.ttsVoiceOptions.first().description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun viewModel(
        repository: ModelCatalogRepository,
        ttsVoiceRepository: TtsVoiceRepository = mockk {
            coEvery { getVoices(any()) } returns emptyList()
        },
    ) = DefaultModelSettingsViewModel(
        observeSettings = ObserveDefaultModelSettingsUseCase(repository),
        updateSettings = UpdateDefaultModelSettingsUseCase(repository),
        runWhenAgentIdle = RunWhenAgentIdleUseCase(FakeAgentRuntimeGate()),
        ttsVoiceRepository = ttsVoiceRepository,
        voiceCapabilities = AllCapabilitiesSupportedResolver,
    )

    /**
     * 能力解析器的测试替身：所有服务都已接入全部能力，
     * 让断言聚焦在模型分组与选择行为本身。
     */
    private object AllCapabilitiesSupportedResolver : VoiceCapabilityResolver {
        override fun isSupported(serviceId: String, capability: ModelCapability): Boolean = true

        override fun connectionProbe(serviceId: String): SpeechConnectionProbe =
            throw VoiceCapabilityNotSupported(serviceId, ModelCapability.TTS)

        override fun endpointStrategy(serviceId: String): EndpointStrategy = EndpointStrategy.ConfiguredBaseUrl

        /** 本替身只覆盖能力判定，不提供原生 Realtime 适配器声明。 */
        override fun realtimeSupport(serviceId: String): RealtimeVoiceSupport? = null
    }

    private suspend fun DefaultModelSettingsViewModel.awaitUiStateReady() {
        uiState.test {
            awaitItem() // initial empty state
            awaitItem() // state populated from repository
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun repository(
        ttsSelection: ModelSelection? = null,
        ttsVoiceFlow: MutableStateFlow<String> = MutableStateFlow("female-shaonv"),
        realtimeSelection: ModelSelection? = null,
        realtimeVoiceFlow: MutableStateFlow<RealtimeVoiceOverride?> = MutableStateFlow(null),
    ): ModelCatalogRepository = mockk(relaxed = true) {
        every { observeServices() } returns MutableStateFlow(listOf(service()))
        every { observeAssistantSelection() } returns MutableStateFlow(null)
        every { observeFastSelection() } returns MutableStateFlow(null)
        every { observeSpeechSelection() } returns MutableStateFlow(null)
        every { observeTtsSelection() } returns MutableStateFlow(ttsSelection)
        every { observeTtsVoice() } returns ttsVoiceFlow
        every { observeRealtimeSelection() } returns MutableStateFlow(realtimeSelection)
        every { observeRealtimeVoice() } returns realtimeVoiceFlow
    }

    private fun service() = LLMModelSetting(
        id = "service",
        name = "Service",
        isEnabled = true,
        apiKey = "key",
        apiBaseUrl = "https://example.com",
        apiProtocol = ApiProtocol.OpenaiChatcompletions,
        anthropicBaseUrl = "https://example.com/anthropic",
        groups = listOf(
            ModelGroup(
                id = "group",
                name = "Group",
                models = listOf(
                    Model("chat", "Chat"),
                    Model("stt", "STT", capabilities = setOf(ModelCapability.STT)),
                    Model("tts", "TTS", capabilities = setOf(ModelCapability.TTS)),
                ),
            ),
        ),
    )
}
