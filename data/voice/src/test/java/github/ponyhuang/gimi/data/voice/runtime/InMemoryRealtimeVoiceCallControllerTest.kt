package github.ponyhuang.gimi.data.voice.runtime

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.resolveConnection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.repository.ConversationVoiceOverrides
import github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy
import github.ponyhuang.gimi.domain.voice.repository.RealtimeUplink
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallForeground
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallState
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceConfiguration
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoicePhase
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceRepository
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSession
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceState
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSupport
import github.ponyhuang.gimi.domain.voice.repository.VoiceApplyResult
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityNotSupported
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigDraft
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigField
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigLookup
import github.ponyhuang.gimi.domain.voice.repository.VoiceConversationStyle
import github.ponyhuang.gimi.domain.voice.repository.VoiceGlobalDefaults
import github.ponyhuang.gimi.domain.voice.repository.VoiceGlobalPreference
import github.ponyhuang.gimi.domain.voice.repository.VoiceGlobalDefaultsRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceInputResult
import github.ponyhuang.gimi.domain.voice.repository.VoiceManualInput
import github.ponyhuang.gimi.domain.voice.repository.VoiceOverrideRepository
import github.ponyhuang.gimi.domain.voice.repository.VoicePipeline
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 保留此回归以保护麦克风所有权、快速往返页面时的旧回调隔离，以及结束清理链的状态同步。 */
@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryRealtimeVoiceCallControllerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun reopeningWaitsForOldResourcesAndIgnoresOldState() = runTest {
        val closeGate = CompletableDeferred<Unit>()
        val first = FakeSession(closeGate)
        val second = FakeSession()
        val sessions = ArrayDeque<RealtimeVoiceSession>(listOf(first, second))
        val foreground = RecordingForeground()
        val controller = controller(sessions, foreground = foreground)
        controller.start(null, null, "", VoiceConversationStyle.CHATBOT, VoicePipeline.CASCADED)
        advanceUntilIdle()
        assertTrue(controller.state.value.active)
        assertEquals(1, foreground.starts)
        controller.end()
        controller.start(null, null, "", VoiceConversationStyle.CHATBOT, VoicePipeline.CASCADED)
        advanceUntilIdle()
        // 结束清理未完成时不得抢占麦克风，旧会话的迟到状态也不能污染共享状态。
        assertEquals(1, sessions.size)
        first.state.value = RealtimeVoiceState(phase = RealtimeVoicePhase.Failed)
        advanceUntilIdle()
        assertEquals(RealtimeVoicePhase.Idle, controller.state.value.voice.phase)
        closeGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(RealtimeVoicePhase.Closed, first.state.value.phase)
        assertEquals(RealtimeVoicePhase.Listening, controller.state.value.voice.phase)
        assertTrue(controller.state.value.active)
        assertFalse(controller.state.value.ending)
        controller.end()
        advanceUntilIdle()
        assertEquals(RealtimeVoicePhase.Closed, second.state.value.phase)
    }

    @Test
    fun microphoneWaitsForCatalogAndClosingBeforeReadyDoesNotStartIt() = runTest {
        val ready = CompletableDeferred<Unit>()
        val catalog = mockk<ModelCatalogRepository>(relaxed = true)
        coEvery { catalog.awaitReady() } coAnswers { ready.await() }
        val session = FakeSession()
        val controller = controller(ArrayDeque(listOf(session)), catalog)
        controller.start(null, null, "", VoiceConversationStyle.CHATBOT, VoicePipeline.CASCADED)
        advanceUntilIdle()
        assertEquals(RealtimeVoicePhase.Idle, session.state.value.phase)
        controller.end()
        ready.complete(Unit)
        advanceUntilIdle()
        assertEquals(RealtimeVoicePhase.Closed, session.state.value.phase)
    }

    @Test
    fun startupFailureUnlocksSettingsAfterCleanupAndKeepsTheReason() = runTest {
        val closeGate = CompletableDeferred<Unit>()
        val session = FakeSession(closeGate, startupFailure = RealtimeVoiceFailure.Configuration)
        val controller = controller(ArrayDeque(listOf(session)))

        controller.start(null, null, "", VoiceConversationStyle.CHATBOT, VoicePipeline.CASCADED)
        assertTrue(controller.state.value.configurationLocked)
        advanceUntilIdle()
        assertTrue(controller.state.value.ending)
        assertTrue(controller.state.value.configurationLocked)

        closeGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(controller.state.value.configurationLocked)
        assertEquals(RealtimeVoiceFailure.Configuration, controller.state.value.voice.failure)
    }

    @Test
    fun configurationSwitchWaitsForFlushAndCloseAndReopeningResetsToDefaults() = runTest {
        val first = FakeSession(CompletableDeferred())
        val second = FakeSession()
        val third = FakeSession()
        val configurations = mutableListOf<RealtimeVoiceConfiguration>()
        val sessions = ArrayDeque<RealtimeVoiceSession>(listOf(first, second, third))
        val selection = ModelSelection("instance-qwen", "instance-qwen-realtime", "qwen-audio-3.1-realtime-plus")
        val controller = InMemoryRealtimeVoiceCallController(
            recordingRepository(configurations, sessions),
            realtimeCatalog(selection),
            RealtimeCapable("qwen"),
            RecordingForeground(),
            defaultsRepository(VoiceGlobalDefaults(realtimeModel = selection)),
            overridesRepository(),
            lookup(),
        )
        controller.start("current", selection, "当前聊天", VoiceConversationStyle.CHATBOT, VoicePipeline.CASCADED)
        advanceUntilIdle()
        controller.selectConfiguration(VoiceConversationStyle.CHATBOT, VoicePipeline.NATIVE_REALTIME)
        advanceUntilIdle()
        assertEquals(1, configurations.size)
        assertTrue(controller.state.value.switching)
        controller.selectConfiguration(VoiceConversationStyle.CHATBOT, VoicePipeline.NATIVE_REALTIME)
        first.closeGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals("current", configurations.last().conversationId)
        assertEquals(selection, configurations.last().modelSelection)
        assertEquals(VoiceConversationStyle.CHATBOT, configurations.last().style)
        assertEquals(RealtimeVoicePhase.Listening, second.state.value.phase)
        controller.end()
        controller.start("current", selection, "", VoiceConversationStyle.CHATBOT, VoicePipeline.CASCADED)
        advanceUntilIdle()
        assertEquals(VoiceConversationStyle.CHATBOT, configurations.last().style)
        assertEquals(VoicePipeline.CASCADED, configurations.last().pipeline)
        controller.end()
        advanceUntilIdle()
    }

    @Test
    fun lazyStartFallsBackToCascadedChatbotWhenThePreselectionLacksItsPrerequisites() = runTest {
        val configurations = mutableListOf<RealtimeVoiceConfiguration>()
        val sessions = ArrayDeque<RealtimeVoiceSession>(listOf(FakeSession(), FakeSession()))
        val controller = InMemoryRealtimeVoiceCallController(
            recordingRepository(configurations, sessions),
            realtimeCatalog(null),
            RealtimeCapable("qwen"),
            RecordingForeground(),
            // 未配置原生实时默认模型：实时链路不可进入。
            defaultsRepository(VoiceGlobalDefaults(realtimeModel = null)),
            overridesRepository(),
            lookup(),
        )
        VoicePipeline.entries.forEach { pipeline ->
            controller.start("assistant", null, "", VoiceConversationStyle.ASSISTANT, pipeline)
        }
        advanceUntilIdle()
        assertTrue(configurations.isEmpty())
        assertFalse(controller.state.value.active)
        // 原生实时默认模型未配置时预选该链路：同样回落级联 Chatbot。
        controller.start(null, null, "", VoiceConversationStyle.CHATBOT, VoicePipeline.NATIVE_REALTIME)
        advanceUntilIdle()
        assertEquals(VoicePipeline.CASCADED, configurations.last().pipeline)
        controller.end()
        advanceUntilIdle()
    }

    @Test
    fun persistenceFailureBlocksConfigurationSwitchAndKeepsTheOriginalSessionForRetry() = runTest {
        val selection = ModelSelection("instance-qwen", "instance-qwen-realtime", "qwen-audio-3.1-realtime-plus")
        val active = FakeSession(canFinish = false)
        var created = 0
        val controller = InMemoryRealtimeVoiceCallController(
            object : RealtimeVoiceRepository {
                override fun createSession(configuration: RealtimeVoiceConfiguration): RealtimeVoiceSession {
                    created++
                    return active
                }
            },
            realtimeCatalog(selection),
            RealtimeCapable("qwen"),
            RecordingForeground(),
            defaultsRepository(VoiceGlobalDefaults(realtimeModel = selection)),
            overridesRepository(),
            lookup(),
        )
        controller.start("current", ModelSelection("service", "group", "model"), "", VoiceConversationStyle.CHATBOT, VoicePipeline.CASCADED)
        advanceUntilIdle()
        controller.selectConfiguration(VoiceConversationStyle.CHATBOT, VoicePipeline.NATIVE_REALTIME)
        advanceUntilIdle()
        assertEquals(1, created)
        assertFalse(controller.state.value.switching)
        assertEquals(RealtimeVoicePhase.Failed, controller.state.value.voice.phase)
        controller.end()
        advanceUntilIdle()
    }

    @Test
    fun endingKeepsCleanupWindowAndIsIdempotentAcrossBothEndEntries() = runTest {
        val closeGate = CompletableDeferred<Unit>()
        val session = FakeSession(closeGate)
        val controller = controller(ArrayDeque(listOf(session)))
        controller.start(null, null, "", VoiceConversationStyle.CHATBOT, VoicePipeline.CASCADED)
        advanceUntilIdle()
        // 页面挂断与状态栏结束可能同时到达：只允许一条清理链，且 ending 持续到资源释放完毕，
        // 前台服务据此维持优先级，不会在 close() 完成前退出。
        controller.end()
        controller.end()
        assertFalse(controller.state.value.active)
        assertTrue(controller.state.value.ending)
        assertEquals(RealtimeVoicePhase.Listening, session.state.value.phase)
        closeGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(RealtimeVoiceCallState(), controller.state.value)
        assertEquals(RealtimeVoicePhase.Closed, session.state.value.phase)
    }

    @Test
    fun manualSubmissionIsRejectedOutsideAnActiveCall() = runTest {
        val session = FakeSession()
        val controller = controller(ArrayDeque(listOf(session)))
        val input = VoiceManualInput("手动消息")
        assertEquals(VoiceInputResult.Unavailable, controller.submit(input))
        controller.start(null, null, "", VoiceConversationStyle.CHATBOT, VoicePipeline.CASCADED)
        advanceUntilIdle()
        assertEquals(VoiceInputResult.Accepted, controller.submit(input))
        controller.end()
        assertEquals(VoiceInputResult.Unavailable, controller.submit(input))
        advanceUntilIdle()
    }

    @Test
    fun editingPauseDoesNotOverwriteUserMute() = runTest {
        val session = FakeSession()
        val controller = controller(ArrayDeque(listOf(session)))
        controller.start(null, null, "", VoiceConversationStyle.CHATBOT, VoicePipeline.CASCADED)
        advanceUntilIdle()
        controller.toggleMute()
        controller.setInputPaused(true)
        assertTrue(session.state.value.muted)
        assertTrue(session.state.value.inputPaused)
        controller.setInputPaused(false)
        assertTrue(session.state.value.muted)
        assertFalse(session.state.value.inputPaused)
        controller.end()
        advanceUntilIdle()
    }

    private fun controller(
        sessions: ArrayDeque<RealtimeVoiceSession>,
        catalog: ModelCatalogRepository = mockk(relaxed = true),
        foreground: RecordingForeground = RecordingForeground(),
        capabilities: VoiceCapabilityResolver = NoRealtimeSupport,
        defaults: VoiceGlobalDefaults = VoiceGlobalDefaults(),
        memory: Map<Pair<VoicePipeline, ModelSelection>, String> = emptyMap(),
    ) = InMemoryRealtimeVoiceCallController(
        object : RealtimeVoiceRepository {
            override fun createSession(configuration: RealtimeVoiceConfiguration): RealtimeVoiceSession =
                sessions.removeFirst()
        },
        catalog,
        capabilities,
        foreground,
        defaultsRepository(defaults),
        overridesRepository(),
        lookup(memory),
    )

    private fun recordingRepository(
        configurations: MutableList<RealtimeVoiceConfiguration>,
        sessions: ArrayDeque<RealtimeVoiceSession>,
    ): RealtimeVoiceRepository = object : RealtimeVoiceRepository {
        override fun createSession(configuration: RealtimeVoiceConfiguration): RealtimeVoiceSession {
            configurations += configuration
            return sessions.removeFirst()
        }
    }

    private fun defaultsRepository(initial: VoiceGlobalDefaults): VoiceGlobalDefaultsRepository =
        object : VoiceGlobalDefaultsRepository {
            private val flow = MutableStateFlow(initial)
            override val defaults: StateFlow<VoiceGlobalDefaults> = flow
            override suspend fun update(transform: (VoiceGlobalPreference) -> VoiceGlobalPreference) {
            val current = flow.value
            val updated = transform(
                VoiceGlobalPreference(
                    pipeline = current.pipeline,
                    semiCascadedUnderstandingModel = current.semiCascadedUnderstandingModel,
                    semiCascadedSynthesisModel = current.semiCascadedSynthesisModel,
                ),
            )
            flow.value = current.copy(
                pipeline = updated.pipeline,
                semiCascadedUnderstandingModel = updated.semiCascadedUnderstandingModel,
                semiCascadedSynthesisModel = updated.semiCascadedSynthesisModel,
            )
        }
        }

    private fun overridesRepository(): VoiceOverrideRepository = object : VoiceOverrideRepository {
        override val rebuildCount: StateFlow<Int> = MutableStateFlow(0)
    override suspend fun acknowledgeRebuilds() = Unit
        override fun observe(conversationId: String): Flow<ConversationVoiceOverrides> =
            flowOf(ConversationVoiceOverrides(conversationId))
        override fun current(conversationId: String) = ConversationVoiceOverrides(conversationId)
        override suspend fun apply(draft: VoiceConfigDraft): VoiceApplyResult = VoiceApplyResult.Applied
        override suspend fun resetFields(conversationId: String, fields: Set<VoiceConfigField>) = Unit
        override suspend fun clear(conversationId: String) = Unit
    }

    private fun lookup(
        memory: Map<Pair<VoicePipeline, ModelSelection>, String> = emptyMap(),
    ): VoiceConfigLookup = object : VoiceConfigLookup {
        override fun isModelValid(selection: ModelSelection): Boolean = true
        override fun isVoiceValid(pipeline: VoicePipeline, selection: ModelSelection, voiceId: String): Boolean? = null
        override fun rememberedVoice(pipeline: VoicePipeline, selection: ModelSelection): String? =
            memory[pipeline to selection]
    }

    /** 默认替身：没有任何服务接入原生 Realtime，因此该链路不可进入。 */
    private object NoRealtimeSupport : VoiceCapabilityResolver {
        override fun isSupported(serviceId: String, capability: ModelCapability): Boolean = false
        override fun connectionProbe(serviceId: String) =
            throw VoiceCapabilityNotSupported(serviceId, ModelCapability.TTS)
        override fun endpointStrategy(serviceId: String) = EndpointStrategy.ConfiguredBaseUrl
        override fun realtimeSupport(serviceId: String): RealtimeVoiceSupport? = null
    }

    /** 声明某个服务已接入原生 Realtime 的替身。 */
    private class RealtimeCapable(private val serviceId: String) : VoiceCapabilityResolver {
        override fun isSupported(serviceId: String, capability: ModelCapability): Boolean =
            capability != ModelCapability.Realtime || serviceId == this.serviceId
        override fun connectionProbe(serviceId: String) =
            throw VoiceCapabilityNotSupported(serviceId, ModelCapability.TTS)
        override fun endpointStrategy(serviceId: String) = EndpointStrategy.VendorOwned
        override fun realtimeSupport(serviceId: String): RealtimeVoiceSupport? =
            if (serviceId == this.serviceId) object : RealtimeVoiceSupport {
                override val serviceId: String = this@RealtimeCapable.serviceId
                override val builtInVoices = emptyList<TtsVoice>()
                override val customVoiceAllowed: Boolean = true
                override val uplink = RealtimeUplink(16_000, false)
                override fun defaultVoiceFor(modelId: String): String = "longanqian_v3.1"
            } else null
    }

    /** 带有原生 Realtime 模型的语音服务实例。 */
    private fun realtimeInstance(id: String = "instance-qwen", templateId: String = "qwen") = ModelServiceInstance(
        id = id, name = id, templateId = templateId,
        isEnabled = true, isOfficialToolsEnabled = false, sharedApiKey = "sk-$id",
        connections = listOf(
            ServiceConnection(
                id = "$id-realtime",
                capability = ServiceCapability.Realtime,
                protocol = ApiProtocol.OpenaiChatcompletions,
                endpoint = "wss://example.test",
                models = listOf(Model("qwen-audio-3.1-realtime-plus", "qwen-audio-3.1-realtime-plus",
                    capabilities = setOf(ModelCapability.Realtime))),
            ),
        ),
    )

    /** 带 Realtime 服务实例的目录替身；默认选择由语音全局默认假件承载。 */
    private fun realtimeCatalog(selection: ModelSelection?): ModelCatalogRepository {
        val instance = realtimeInstance()
        val catalog = mockk<ModelCatalogRepository>()
        every { catalog.observeInstances() } returns MutableStateFlow(listOf(instance))
        every { catalog.currentInstances() } returns listOf(instance)
        every { catalog.currentAssistantSelection() } returns null
        every { catalog.currentTtsSelection() } returns null
        every { catalog.currentSpeechSelection() } returns null
        every { catalog.resolveConnection(any()) } answers {
            listOf(instance).resolveConnection(firstArg())
        }
        coEvery { catalog.awaitReady() } returns Unit
        return catalog
    }

    @Test
    fun realtimePipelineUsesTheConfiguredDefaultSelectionAndRememberedVoice() = runTest {
        val selection = ModelSelection("instance-qwen", "instance-qwen-realtime", "qwen-audio-3.1-realtime-plus")
        val configurations = mutableListOf<RealtimeVoiceConfiguration>()
        val sessions = ArrayDeque<RealtimeVoiceSession>(listOf(FakeSession(), FakeSession()))
        val controller = InMemoryRealtimeVoiceCallController(
            recordingRepository(configurations, sessions),
            realtimeCatalog(selection),
            RealtimeCapable("qwen"),
            RecordingForeground(),
            defaultsRepository(VoiceGlobalDefaults(realtimeModel = selection)),
            overridesRepository(),
            lookup(mapOf(VoicePipeline.NATIVE_REALTIME to selection to "cloned_voice_id")),
        )
        controller.start(null, null, "", VoiceConversationStyle.CHATBOT, VoicePipeline.CASCADED)
        advanceUntilIdle()
        assertTrue(controller.state.value.realtimeAvailable)
        controller.selectConfiguration(VoiceConversationStyle.CHATBOT, VoicePipeline.NATIVE_REALTIME)
        advanceUntilIdle()
        assertEquals(VoicePipeline.NATIVE_REALTIME, configurations.last().pipeline)
        assertEquals(selection, configurations.last().modelSelection)
        // 音色记忆按完整模型选择生效。
        assertEquals("cloned_voice_id", configurations.last().realtimeVoice)
        controller.end()
        advanceUntilIdle()
    }

    @Test
    fun realtimePipelineIsUnavailableWithoutAConfiguredDefault() = runTest {
        val configurations = mutableListOf<RealtimeVoiceConfiguration>()
        val sessions = ArrayDeque<RealtimeVoiceSession>(listOf(FakeSession(), FakeSession()))
        val controller = InMemoryRealtimeVoiceCallController(
            recordingRepository(configurations, sessions),
            realtimeCatalog(null),
            RealtimeCapable("qwen"),
            RecordingForeground(),
            defaultsRepository(VoiceGlobalDefaults(realtimeModel = null)),
            overridesRepository(),
            lookup(),
        )
        controller.start(null, null, "", VoiceConversationStyle.CHATBOT, VoicePipeline.CASCADED)
        advanceUntilIdle()
        assertFalse(controller.state.value.realtimeAvailable)
        controller.selectConfiguration(VoiceConversationStyle.CHATBOT, VoicePipeline.NATIVE_REALTIME)
        advanceUntilIdle()
        // 未配置 Realtime 默认模型时不进入该链路：不得新建会话。
        assertEquals(1, configurations.size)
        assertEquals(VoicePipeline.CASCADED, configurations.last().pipeline)
        controller.end()
        advanceUntilIdle()
    }

    @Test
    fun semiCascadedAvailabilitySurvivesCallEnd() = runTest {
        val selection = ModelSelection("instance-omni", "conn-omni", "mimo-v2.6-pro")
        val instance = ModelServiceInstance(
            id = "instance-omni", name = "MiMo", templateId = "mimo",
            isEnabled = true, isOfficialToolsEnabled = false, sharedApiKey = "sk-test",
            connections = listOf(
                ServiceConnection(
                    id = "conn-omni",
                    capability = ServiceCapability.Conversation,
                    protocol = ApiProtocol.Anthropic,
                    endpoint = "https://example.test/anthropic",
                    models = listOf(Model("mimo-v2.6-pro", "MiMo", capabilities = setOf(ModelCapability.LLM))),
                ),
            ),
        )
        val catalog = mockk<ModelCatalogRepository>(relaxed = true)
        every { catalog.currentAssistantSelection() } returns selection
        every { catalog.resolveConnection(any()) } answers { listOf(instance).resolveConnection(firstArg()) }
        coEvery { catalog.awaitReady() } returns Unit
        val tts = ModelSelection("instance-a", "conn-a", "tts-a")
        val controller = controller(
            ArrayDeque(listOf(FakeSession())),
            catalog,
            defaults = VoiceGlobalDefaults(
                pipeline = VoicePipeline.SEMI_CASCADED,
                semiCascadedSynthesisModel = tts,
            ),
        )

        controller.start(null, selection, "", VoiceConversationStyle.CHATBOT, VoicePipeline.SEMI_CASCADED)
        advanceUntilIdle()
        assertTrue(controller.state.value.semiCascadedAvailable)
        assertEquals(VoicePipeline.SEMI_CASCADED, controller.state.value.pipeline)

        controller.end()
        advanceUntilIdle()
        // 可用性属于环境能力：挂断后复位不能把半级联链路误标为不可用。
        assertTrue(controller.state.value.semiCascadedAvailable)
    }

    @Test
    fun activeChatbotRejectsSwitchToAssistantEvenWithRealtimeConfigured() = runTest {
        val selection = ModelSelection("instance-qwen", "instance-qwen-realtime", "qwen-audio-3.1-realtime-plus")
        val configurations = mutableListOf<RealtimeVoiceConfiguration>()
        val sessions = ArrayDeque<RealtimeVoiceSession>(listOf(FakeSession(), FakeSession()))
        val controller = InMemoryRealtimeVoiceCallController(
            recordingRepository(configurations, sessions),
            realtimeCatalog(selection),
            RealtimeCapable("qwen"),
            RecordingForeground(),
            defaultsRepository(VoiceGlobalDefaults(realtimeModel = selection)),
            overridesRepository(),
            lookup(mapOf(VoicePipeline.NATIVE_REALTIME to selection to "cloned_voice_id")),
        )
        controller.start(null, null, "", VoiceConversationStyle.CHATBOT, VoicePipeline.CASCADED)
        advanceUntilIdle()
        assertTrue(controller.state.value.realtimeAvailable)
        controller.selectConfiguration(VoiceConversationStyle.ASSISTANT, VoicePipeline.NATIVE_REALTIME)
        advanceUntilIdle()
        assertEquals(1, configurations.size)
        assertEquals(VoiceConversationStyle.CHATBOT, configurations.single().style)
        assertEquals(VoicePipeline.CASCADED, configurations.single().pipeline)
        controller.end()
        advanceUntilIdle()
    }

    private class RecordingForeground : RealtimeVoiceCallForeground {
        var starts = 0
        override fun start() { starts++ }
    }

    private class FakeSession(
        val closeGate: CompletableDeferred<Unit>? = null,
        private val canFinish: Boolean = true,
        private val startupFailure: RealtimeVoiceFailure? = null,
    ) : RealtimeVoiceSession {
        override val state = MutableStateFlow(RealtimeVoiceState())
        override suspend fun start() {
            state.value = state.value.copy(
                phase = if (startupFailure == null) RealtimeVoicePhase.Listening else RealtimeVoicePhase.Failed,
                failure = startupFailure,
            )
        }
        override fun setInputPaused(paused: Boolean) { state.value = state.value.copy(inputPaused = paused) }
        override suspend fun submit(input: VoiceManualInput) = VoiceInputResult.Accepted
        override fun setMuted(muted: Boolean) { state.value = state.value.copy(muted = muted) }
        override suspend fun finishPendingTurn(): Boolean {
            if (!canFinish) state.value = state.value.copy(
                phase = RealtimeVoicePhase.Failed,
                failure = RealtimeVoiceFailure.Persistence,
            )
            return canFinish
        }
        override suspend fun retry() { start() }
        override suspend fun close() {
            closeGate?.await()
            state.value = RealtimeVoiceState(phase = RealtimeVoicePhase.Closed)
        }
    }
}
