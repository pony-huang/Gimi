package github.ponyhuang.gimi.data.modelaccess

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedAgentModel
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.SpeechEndpoints
import github.ponyhuang.gimi.domain.modelcatalog.model.resolveConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
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
import github.ponyhuang.gimi.data.modelaccess.shared.QwenRealtimeVoiceSupport
import github.ponyhuang.gimi.data.modelaccess.shared.OpenAiRealtimeVoiceSupport
import github.ponyhuang.gimi.data.modelaccess.qwen.realtime.QwenRealtimeModel
import github.ponyhuang.gimi.data.modelaccess.openai.realtime.OpenAiRealtimeModel

/**
 * 实时/级联分派契约：实时与级联连接共处同一服务实例后，分派只看所选模型的
 * [ModelCapability.Realtime] 标记与模板身份；端点由厂商自有，千问按业务空间 ID 改写。
 */
class VoiceLiveModelFactoryTest {
    private val models = mockk<AgentLLMModelFactory>()
    private val configuration = mockk<AgentModelConfigurationSource>()
    private val modelCatalog = mockk<ModelCatalogRepository>()
    private val recognition = mockk<SpeechRecognitionRepository> {
        every { availability } returns MutableStateFlow(false)
    }
    private val synthesis = mockk<SpeechSynthesisRepository> {
        every { isAvailable() } returns false
    }

    /**
     * 能力解析替身：只登记真实存在的两个原生 Realtime 适配器。
     *
     * 上行契约与缺省音色都由适配器自述，工厂不再按厂商硬编码差异，
     * 因此这个替身必须如实反映"哪些模板接入了原生 Realtime"。
     */
    private val capabilities = object : VoiceCapabilityResolver {
        private val supports = listOf(QwenRealtimeVoiceSupport(), OpenAiRealtimeVoiceSupport())
        override fun isSupported(templateId: String, capability: ModelCapability): Boolean = when (capability) {
            ModelCapability.Realtime -> realtimeSupport(templateId) != null
            else -> true
        }
        override fun connectionProbe(templateId: String) =
            throw VoiceCapabilityNotSupported(templateId, ModelCapability.TTS)
        override fun endpointStrategy(templateId: String) = EndpointStrategy.VendorOwned
        override fun realtimeSupport(templateId: String): RealtimeVoiceSupport? =
            supports.firstOrNull { it.serviceId == templateId }
    }

    private fun factory() = VoiceLiveModelFactory(
        models, configuration, modelCatalog, recognition, synthesis, capabilities, OkHttpClient(),
    )

    private fun realtimeInstance(
        id: String,
        templateId: String?,
        modelIds: List<String>,
        workspaceId: String? = null,
        isEnabled: Boolean = true,
    ): ModelServiceInstance {
        val connection = ServiceConnection(
            id = "$id-realtime",
            capability = ServiceCapability.Realtime,
            protocol = ApiProtocol.OpenaiChatcompletions,
            endpoint = SpeechEndpoints.QWEN_AUDIO_REALTIME_CN,
            workspaceId = workspaceId,
            models = modelIds.map { Model(it, it, capabilities = setOf(ModelCapability.Realtime)) },
        )
        return ModelServiceInstance(
            id = id,
            name = id,
            templateId = templateId,
            isEnabled = isEnabled,
            isOfficialToolsEnabled = false,
            sharedApiKey = "sk-$id",
            connections = listOf(connection),
        )
    }

    private fun instances() = listOf(
        realtimeInstance(
            "instance-qwen", "qwen",
            listOf("qwen-audio-3.1-realtime-plus", "qwen-audio-3.0-realtime-plus"),
            workspaceId = "ws-123",
        ),
        realtimeInstance("instance-openai", "openai", listOf("gpt-realtime-2.1")),
        realtimeInstance("instance-unknown", "unknown-vendor", listOf("whatever-realtime")),
    )

    private fun stubCatalog(instances: List<ModelServiceInstance> = instances()) {
        coEvery { configuration.awaitReady() } returns Unit
        every { configuration.currentInstances() } returns instances
        every { configuration.runtimeSelection } returns MutableStateFlow(null)
        every { configuration.fastSelection } returns MutableStateFlow(null)
        every { configuration.configurationRevision } returns MutableStateFlow(0L)
        every { configuration.defaultSelection() } returns null
        every { configuration.resolveChatModel(any()) } returns ResolvedAgentModel(
            serviceId = "instance-openai", templateId = "openai",
            protocol = ApiProtocol.OpenaiChatcompletions,
            modelId = "gpt-test", apiKey = "sk", modelBaseUrl = "https://api.openai.com/v1",
        )
        every { modelCatalog.resolveConnection(any()) } answers {
            instances.resolveConnection(firstArg())
        }
        every { models.selectModelConfig(any()) } returns ModelConfig(
            serviceId = "instance-openai", templateId = "openai",
            baseType = ApiProtocol.OpenaiChatcompletions,
            modelId = "gpt-test", apiKey = "sk", fullBaseUrl = "https://api.openai.com/v1",
        )
        // CascadedVoiceModel 构造读取 delegate.name
        every { models.createModel(any()) } answers {
            mockk<com.google.adk.kt.models.Model> { every { name } returns "fake" }
        }
    }

    private fun selection(instanceId: String, connectionId: String, modelId: String) =
        ModelSelection(instanceId, connectionId, modelId)

    @Test
    fun `realtime selection dispatches to the vendor native adapter`() = runTest {
        stubCatalog()
        val qwen = factory().create(selection("instance-qwen", "instance-qwen-realtime", "qwen-audio-3.1-realtime-plus"))
        assertTrue(qwen is QwenRealtimeModel)
        val openAi = factory().create(selection("instance-openai", "instance-openai-realtime", "gpt-realtime-2.1"))
        assertTrue(openAi is OpenAiRealtimeModel)
    }

    @Test
    fun `cascade model in the same instance still dispatches to cascade`() = runTest {
        stubCatalog()
        // 非实时连接（无 Realtime 标记）继续走级联装配。
        val cascade = factory().create(selection("instance-qwen", "chat", "gpt-test"))
        assertTrue(cascade is CascadedVoiceModel)
    }

    @Test
    fun `unknown vendor realtime model falls back to cascade defensively`() = runTest {
        stubCatalog()
        val cascade = factory().create(selection("instance-unknown", "instance-unknown-realtime", "whatever-realtime"))
        assertTrue(cascade is CascadedVoiceModel)
    }

    @Test
    fun `unconfigured realtime instance is unavailable`() = runTest {
        stubCatalog(
            listOf(
                realtimeInstance(
                    "instance-qwen", "qwen",
                    listOf("qwen-audio-3.1-realtime-plus"),
                    isEnabled = false,
                ),
            ),
        )
        val factory = factory()
        assertFalse(
            factory.isAvailable(
                selection("instance-qwen", "instance-qwen-realtime", "qwen-audio-3.1-realtime-plus"),
            ),
        )
    }

    @Test
    fun `uplink contract follows the vendor protocol`() = runTest {
        stubCatalog()
        val factory = factory()
        assertEquals(
            RealtimeUplink(sampleRateHz = 16_000, resampled = false),
            factory.realtimeUplink(selection("instance-qwen", "instance-qwen-realtime", "qwen-audio-3.1-realtime-plus")),
        )
        assertEquals(
            RealtimeUplink(sampleRateHz = 24_000, resampled = true),
            factory.realtimeUplink(selection("instance-openai", "instance-openai-realtime", "gpt-realtime-2.1")),
        )
        // 未知厂商无全双工上行契约；级联选择同样为 null。
        assertNull(factory.realtimeUplink(selection("instance-unknown", "instance-unknown-realtime", "whatever-realtime")))
        assertNull(factory.realtimeUplink(selection("instance-qwen", "chat", "gpt-test")))
        assertNull(factory.realtimeUplink(null))
    }

    @Test
    fun `session voice falls back to the vendor default of the selected model`() = runTest {
        stubCatalog()
        val factory = factory()
        // 3.1 代际的缺省音色带 _v3.1 后缀；3.0 代际用无后缀的旧音色，两者互不通用。
        val qwen31 = factory().create(selection("instance-qwen", "instance-qwen-realtime", "qwen-audio-3.1-realtime-plus"))
        assertEquals("longanqian_v3.1", (qwen31 as QwenRealtimeModel).voice)
        val qwen30 = factory().create(selection("instance-qwen", "instance-qwen-realtime", "qwen-audio-3.0-realtime-plus"))
        assertEquals("longanqian", (qwen30 as QwenRealtimeModel).voice)
        val openAi = factory().create(selection("instance-openai", "instance-openai-realtime", "gpt-realtime-2.1"))
        assertEquals("cedar", (openAi as OpenAiRealtimeModel).voice)
    }

    @Test
    fun `custom session voice overrides the vendor default`() = runTest {
        stubCatalog()
        val qwen = factory().create(
            selection("instance-qwen", "instance-qwen-realtime", "qwen-audio-3.1-realtime-plus"),
            voice = "cloned_voice_id",
        )
        assertEquals("cloned_voice_id", (qwen as QwenRealtimeModel).voice)
    }
}
