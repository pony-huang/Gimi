package github.ponyhuang.gimi.feature.modelsettings.defaults

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.RealtimeVoiceOverride
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.UpdateDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy
import github.ponyhuang.gimi.domain.voice.repository.RealtimeUplink
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import github.ponyhuang.gimi.domain.voice.repository.VoiceCatalogResolver

/**
 * Realtime 默认选择的行为契约。
 *
 * Realtime 模型是全局唯一入口，因此候选范围、音色归属与"未配置"的语义都要在这里钉死：
 * 未接入原生适配器的模板不能出现在候选里；音色覆盖绑定到具体模型，换模型即失效。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultModelSettingsRealtimeTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val realtimeSelection =
        ModelSelection("instance-qwen", "instance-qwen-realtime", "qwen-audio-3.1-realtime-plus")
    private val otherRealtimeSelection =
        ModelSelection("instance-qwen", "instance-qwen-realtime", "qwen-audio-3.0-realtime-plus")

    @Test
    fun onlyTemplatesWithNativeRealtimeSupportAppearAsCandidates() = runTest {
        val repository = repository()
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            var state = awaitItem()
            while (state.realtimeModels.isEmpty()) state = awaitItem()
            // 未接入原生 Realtime 适配器的模板不出现：它们的 Realtime 模型会静默回落级联，
            // 选它只会得到起不来的会话。
            assertEquals(
                listOf("qwen-audio-3.1-realtime-plus", "qwen-audio-3.0-realtime-plus"),
                state.realtimeModels.map { it.model.id },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun voiceOptionsFollowTheSelectedRealtimeModel() = runTest {
        val repository = repository(realtimeSelection = realtimeSelection)
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            var state = awaitItem()
            while (state.realtimeDefaultVoice.isEmpty()) state = awaitItem()
            assertEquals("longanqian_v3.1", state.realtimeDefaultVoice)
            // 音色集按模型解析：同一模板下 3.0 与 3.1 的可选音色互不相通。
            assertEquals(listOf("longanqian_v3.1"), state.realtimeVoiceOptions.map { it.id })
            assertTrue(state.realtimeCustomVoiceAllowed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun voiceOptionsFollowTheModelGeneration() = runTest {
        val repository = repository(realtimeSelection = otherRealtimeSelection)
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            var state = awaitItem()
            while (state.realtimeDefaultVoice.isEmpty()) state = awaitItem()
            assertEquals("longanqian", state.realtimeDefaultVoice)
            assertEquals(listOf("longanqian"), state.realtimeVoiceOptions.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun customVoiceOverrideIsBoundToTheModelItWasSetFor() = runTest {
        val repository = repository(
            realtimeSelection = otherRealtimeSelection,
            realtimeVoice = RealtimeVoiceOverride(realtimeSelection, "cloned_voice_id"),
        )
        val viewModel = viewModel(repository)

        viewModel.uiState.test {
            awaitItem()
            var state = awaitItem()
            while (state.realtimeDefaultVoice.isEmpty()) state = awaitItem()
            // 覆盖值绑定在另一个模型上：对当前模型不生效，回落到缺省音色。
            assertNull(state.realtimeVoiceId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectingAVoiceWritesAnOverrideBoundToTheCurrentModel() = runTest {
        val repository = repository(realtimeSelection = realtimeSelection)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(DefaultModelSettingsAction.SelectRealtimeVoice("cloned_voice_id"))
        advanceUntilIdle()

        verify {
            repository.selectRealtimeVoice(RealtimeVoiceOverride(realtimeSelection, "cloned_voice_id"))
        }
    }

    @Test
    fun clearingTheVoiceWritesNoOverride() = runTest {
        val repository = repository(realtimeSelection = realtimeSelection)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(DefaultModelSettingsAction.SelectRealtimeVoice(null))
        advanceUntilIdle()

        verify { repository.selectRealtimeVoice(null) }
    }

    @Test
    fun clearingTheRealtimeModelWritesNoSelection() = runTest {
        val repository = repository(realtimeSelection = realtimeSelection)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(DefaultModelSettingsAction.SelectRealtimeModel(null))
        advanceUntilIdle()

        verify { repository.selectRealtimeModel(null) }
    }

    private fun viewModel(repository: ModelCatalogRepository) = DefaultModelSettingsViewModel(
        observeSettings = ObserveDefaultModelSettingsUseCase(repository),
        updateSettings = UpdateDefaultModelSettingsUseCase(repository),
        runWhenAgentIdle = RunWhenAgentIdleUseCase(FakeAgentRuntimeGate()),
        ttsVoiceRepository = mockk<TtsVoiceRepository> { coEvery { getVoices(any()) } returns emptyList() },
        voiceCapabilities = RealtimeCapableResolver,
        voiceCatalog = mockk(relaxed = true),
    )

    /** 只有 qwen 模板声明了原生 Realtime 适配器，另一个模板带 Realtime 标记也不算数。 */
    private object RealtimeCapableResolver : VoiceCapabilityResolver {
        override fun isSupported(templateId: String, capability: ModelCapability): Boolean =
            capability != ModelCapability.Realtime || templateId == "qwen"

        override fun connectionProbe(templateId: String): SpeechConnectionProbe =
            throw VoiceCapabilityNotSupported(templateId, ModelCapability.TTS)

        override fun endpointStrategy(templateId: String): EndpointStrategy = EndpointStrategy.VendorOwned

        override fun realtimeSupport(templateId: String): RealtimeVoiceSupport? =
            if (templateId == "qwen") QwenLikeSupport else null
    }

    /** 千问式适配器：音色集按模型代际给、允许自由输入、缺省音色同样按代际给。 */
    private object QwenLikeSupport : RealtimeVoiceSupport {
        override val serviceId: String = "qwen"

        /** 音色集随模型变化，服务级没有统一表。 */
        override val builtInVoices: List<TtsVoice> = emptyList()
        override val customVoiceAllowed: Boolean = true
        override val uplink: RealtimeUplink = RealtimeUplink(sampleRateHz = 16_000, resampled = false)

        override fun voicesFor(modelId: String): List<TtsVoice> =
            if (modelId.startsWith("qwen-audio-3.0-")) {
                listOf(TtsVoice("longanqian", "龙安千", null, null))
            } else {
                listOf(TtsVoice("longanqian_v3.1", "龙安千", null, null))
            }

        override fun defaultVoiceFor(modelId: String): String =
            if (modelId.startsWith("qwen-audio-3.0-")) "longanqian" else "longanqian_v3.1"
    }

    private fun repository(
        realtimeSelection: ModelSelection? = null,
        realtimeVoice: RealtimeVoiceOverride? = null,
    ): ModelCatalogRepository = mockk(relaxed = true) {
        every { observeInstances() } returns MutableStateFlow(listOf(qwenInstance(), plainInstance()))
        every { observeAssistantSelection() } returns MutableStateFlow(null)
        every { observeFastSelection() } returns MutableStateFlow(null)
        every { observeSpeechSelection() } returns MutableStateFlow(null)
        every { observeTtsSelection() } returns MutableStateFlow(null)
        every { observeTtsVoice() } returns MutableStateFlow("")
        every { observeRealtimeSelection() } returns MutableStateFlow(realtimeSelection)
        every { observeRealtimeVoice() } returns MutableStateFlow(realtimeVoice)
    }

    /** 声明了原生 Realtime 适配器的语音服务实例。 */
    private fun qwenInstance() = ModelServiceInstance(
        id = "instance-qwen",
        name = "Qwen",
        templateId = "qwen",
        isEnabled = true,
        isOfficialToolsEnabled = false,
        sharedApiKey = "key",
        connections = listOf(
            ServiceConnection(
                id = "instance-qwen-realtime",
                capability = ServiceCapability.Realtime,
                protocol = ApiProtocol.OpenaiChatcompletions,
                endpoint = "wss://example.com",
                models = listOf(
                    Model("qwen-audio-3.1-realtime-plus", "qwen-audio-3.1-realtime-plus",
                        capabilities = setOf(ModelCapability.Realtime)),
                    Model("qwen-audio-3.0-realtime-plus", "qwen-audio-3.0-realtime-plus",
                        capabilities = setOf(ModelCapability.Realtime)),
                ),
            ),
        ),
    )

    /** 带 Realtime 标记但没有原生适配器的实例：不得出现在候选里。 */
    private fun plainInstance() = ModelServiceInstance(
        id = "instance-plain",
        name = "Plain",
        templateId = "plain-speech",
        isEnabled = true,
        isOfficialToolsEnabled = false,
        sharedApiKey = "key",
        connections = listOf(
            ServiceConnection(
                id = "instance-plain-realtime",
                capability = ServiceCapability.Realtime,
                protocol = ApiProtocol.OpenaiChatcompletions,
                endpoint = "wss://example.com",
                models = listOf(
                    Model("plain-realtime-model", "plain-realtime-model",
                        capabilities = setOf(ModelCapability.Realtime)),
                ),
            ),
        ),
    )
}
