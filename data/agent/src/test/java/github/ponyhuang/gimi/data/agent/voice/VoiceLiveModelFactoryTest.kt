package github.ponyhuang.gimi.data.agent.voice

import github.ponyhuang.gimi.data.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceKind
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedAgentModel
import github.ponyhuang.gimi.domain.modelcatalog.model.SpeechEndpoints
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy
import github.ponyhuang.gimi.domain.voice.repository.RealtimeUplink
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityNotSupported
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSupport
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 实时/级联分派契约：实时与级联模型共处同一语音服务后，分派只看所选模型的
 * [ModelCapability.Realtime] 标记与 serviceId；端点由厂商自有，千问按业务空间 ID 改写。
 */
class VoiceLiveModelFactoryTest {
    private val models = mockk<AgentLLMModelFactory>()
    private val configuration = mockk<AgentModelConfigurationSource>()
    private val recognition = mockk<SpeechRecognitionRepository>()
    private val synthesis = mockk<SpeechSynthesisRepository>()

    /**
     * 能力解析替身：只登记真实存在的两个原生 Realtime 适配器。
     *
     * 上行契约与缺省音色都由适配器自述，工厂不再按 serviceId 硬编码厂商差异，
     * 因此这个替身必须如实反映"哪些服务接入了原生 Realtime"。
     */
    private val capabilities = object : VoiceCapabilityResolver {
        private val supports = listOf(QwenRealtimeVoiceSupport(), OpenAiRealtimeVoiceSupport())
        override fun isSupported(serviceId: String, capability: ModelCapability): Boolean = when (capability) {
            ModelCapability.Realtime -> realtimeSupport(serviceId) != null
            else -> true
        }
        override fun connectionProbe(serviceId: String) =
            throw VoiceCapabilityNotSupported(serviceId, ModelCapability.TTS)
        override fun endpointStrategy(serviceId: String) = EndpointStrategy.VendorOwned
        override fun realtimeSupport(serviceId: String): RealtimeVoiceSupport? =
            supports.firstOrNull { it.serviceId == serviceId }
    }

    private fun factory() =
        VoiceLiveModelFactory(models, configuration, recognition, synthesis, capabilities, OkHttpClient())

    private fun realtimeGroup(groupId: String, vararg modelIds: String) =
        ModelGroup(groupId, "实时语音对话", modelIds.map { Model(it, it, capabilities = setOf(ModelCapability.Realtime)) })

    private fun speechService(
        id: String,
        groups: List<ModelGroup>,
        workspaceId: String? = null,
        isEnabled: Boolean = true,
    ) = LLMModelSetting(
        id = id, name = id, isEnabled = isEnabled, apiKey = "sk-$id",
        apiBaseUrl = SpeechEndpoints.QWEN_AUDIO_CN, apiProtocol = ApiProtocol.OpenaiChatcompletions,
        supportedProtocols = listOf(ApiProtocol.OpenaiChatcompletions), anthropicBaseUrl = "",
        groups = groups, kind = ModelServiceKind.Speech, workspaceId = workspaceId,
    )

    private fun services() = listOf(
        speechService(
            "qwen-audio",
            listOf(
                realtimeGroup(
                    "qwen-audio-realtime",
                    "qwen-audio-3.1-realtime-plus",
                    "qwen-audio-3.0-realtime-plus",
                ),
            ),
            workspaceId = "ws-123",
        ),
        speechService("openai-speech", listOf(realtimeGroup("openai-speech-realtime", "gpt-realtime-2.1"))),
        speechService("unknown-speech", listOf(realtimeGroup("unknown-realtime", "whatever-realtime"))),
    )

    private fun stubCatalog() {
        coEvery { configuration.awaitReady() } returns Unit
        every { configuration.currentServices() } returns services()
        every { configuration.runtimeSelection } returns MutableStateFlow(null)
        every { configuration.fastSelection } returns MutableStateFlow(null)
        every { configuration.configurationRevision } returns MutableStateFlow(0L)
        every { configuration.defaultSelection() } returns null
        every { configuration.resolveChatModel(any()) } returns ResolvedAgentModel(
            serviceId = "openai", protocol = ApiProtocol.OpenaiChatcompletions,
            modelId = "gpt-test", apiKey = "sk", modelBaseUrl = "https://api.openai.com/v1",
        )
        every { models.selectModelConfig(any()) } returns github.ponyhuang.gimi.data.agent.ModelConfig(
            serviceId = "openai", baseType = ApiProtocol.OpenaiChatcompletions,
            modelId = "gpt-test", apiKey = "sk", fullBaseUrl = "https://api.openai.com/v1",
        )
        // CascadedVoiceModel 构造读取 delegate.name
        every { models.createModel(any()) } answers {
            mockk<com.google.adk.kt.models.Model> { every { name } returns "fake" }
        }
    }

    private fun selection(serviceId: String, groupId: String, modelId: String) =
        ModelSelection(serviceId, groupId, modelId)

    @Test
    fun `realtime selection dispatches to the vendor native adapter`() = runTest {
        stubCatalog()
        val qwen = factory().create(selection("qwen-audio", "qwen-audio-realtime", "qwen-audio-3.1-realtime-plus"))
        assertTrue(qwen is QwenRealtimeModel)
        val openAi = factory().create(selection("openai-speech", "openai-speech-realtime", "gpt-realtime-2.1"))
        assertTrue(openAi is OpenAiRealtimeModel)
    }

    @Test
    fun `cascade model in the same service still dispatches to cascade`() = runTest {
        stubCatalog()
        // 同一服务里的级联模型（无 Realtime 标记）继续走级联装配。
        val cascade = factory().create(selection("qwen-audio", "chat", "gpt-test"))
        assertTrue(cascade is CascadedVoiceModel)
    }

    @Test
    fun `unknown vendor realtime model falls back to cascade defensively`() = runTest {
        stubCatalog()
        val cascade = factory().create(selection("unknown-speech", "unknown-realtime", "whatever-realtime"))
        assertTrue(cascade is CascadedVoiceModel)
    }

    @Test
    fun `unconfigured realtime service is unavailable`() = runTest {
        stubCatalog()
        coEvery { configuration.currentServices() } returns listOf(
            speechService(
                "qwen-audio",
                listOf(realtimeGroup("qwen-audio-realtime", "qwen-audio-3.1-realtime-plus")),
                isEnabled = false,
            ),
        )
        val factory = factory()
        assertFalse(factory.isAvailable(selection("qwen-audio", "qwen-audio-realtime", "qwen-audio-3.1-realtime-plus")))
    }

    @Test
    fun `uplink contract follows the vendor protocol`() = runTest {
        stubCatalog()
        val factory = factory()
        assertEquals(
            RealtimeUplink(sampleRateHz = 16_000, resampled = false),
            factory.realtimeUplink(selection("qwen-audio", "qwen-audio-realtime", "qwen-audio-3.1-realtime-plus")),
        )
        assertEquals(
            RealtimeUplink(sampleRateHz = 24_000, resampled = true),
            factory.realtimeUplink(selection("openai-speech", "openai-speech-realtime", "gpt-realtime-2.1")),
        )
        // 未知厂商无全双工上行契约；级联选择同样为 null。
        assertNull(factory.realtimeUplink(selection("unknown-speech", "unknown-realtime", "whatever-realtime")))
        assertNull(factory.realtimeUplink(selection("qwen-audio", "chat", "gpt-test")))
        assertNull(factory.realtimeUplink(null))
    }

    @Test
    fun `session voice falls back to the vendor default of the selected model`() = runTest {
        stubCatalog()
        val factory = factory()
        // 3.1 代际的缺省音色带 _v3.1 后缀；3.0 代际用无后缀的旧音色，两者互不通用。
        val qwen31 = factory().create(selection("qwen-audio", "qwen-audio-realtime", "qwen-audio-3.1-realtime-plus"))
        assertEquals("longanqian_v3.1", (qwen31 as QwenRealtimeModel).voice)
        val qwen30 = factory().create(selection("qwen-audio", "qwen-audio-realtime", "qwen-audio-3.0-realtime-plus"))
        assertEquals("longanqian", (qwen30 as QwenRealtimeModel).voice)
        val openAi = factory().create(selection("openai-speech", "openai-speech-realtime", "gpt-realtime-2.1"))
        assertEquals("cedar", (openAi as OpenAiRealtimeModel).voice)
    }

    @Test
    fun `custom session voice overrides the vendor default`() = runTest {
        stubCatalog()
        val qwen = factory().create(
            selection("qwen-audio", "qwen-audio-realtime", "qwen-audio-3.1-realtime-plus"),
            voice = "cloned_voice_id",
        )
        assertEquals("cloned_voice_id", (qwen as QwenRealtimeModel).voice)
    }
}
