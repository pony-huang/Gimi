package github.ponyhuang.gimi.data.voice.runtime

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.resolveConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.RealtimeVoiceOverride
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallForeground
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallState
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceConfiguration
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceMode
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoicePhase
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceRepository
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSession
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceState
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSupport
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityNotSupported
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
        controller.start(null, null, "", RealtimeVoiceMode.Simple)
        advanceUntilIdle()
        assertTrue(controller.state.value.active)
        assertEquals(1, foreground.starts)
        controller.end()
        controller.start(null, null, "", RealtimeVoiceMode.Simple)
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
        controller.start(null, null, "", RealtimeVoiceMode.Simple)
        advanceUntilIdle()
        assertEquals(RealtimeVoicePhase.Idle, session.state.value.phase)
        controller.end()
        ready.complete(Unit)
        advanceUntilIdle()
        assertEquals(RealtimeVoicePhase.Closed, session.state.value.phase)
    }

    @Test
    fun modeSwitchWaitsForFlushAndCloseAndReopeningResetsToSimple() = runTest {
        val first = FakeSession(CompletableDeferred())
        val second = FakeSession()
        val third = FakeSession()
        val configurations = mutableListOf<RealtimeVoiceConfiguration>()
        val sessions = ArrayDeque<RealtimeVoiceSession>(listOf(first, second, third))
        val controller = InMemoryRealtimeVoiceCallController(object : RealtimeVoiceRepository {
            override fun createSession(configuration: RealtimeVoiceConfiguration): RealtimeVoiceSession {
                configurations += configuration
                return sessions.removeFirst()
            }
        }, mockk(relaxed = true), NoRealtimeSupport, RecordingForeground())
        val selection = ModelSelection("service", "group", "model")
        controller.start("current", selection, "当前聊天", RealtimeVoiceMode.Simple)
        advanceUntilIdle()
        controller.selectMode(RealtimeVoiceMode.Chat)
        advanceUntilIdle()
        assertEquals(1, configurations.size)
        assertTrue(controller.state.value.switching)
        controller.selectMode(RealtimeVoiceMode.Chat)
        first.closeGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals("current", configurations.last().conversationId)
        assertEquals(selection, configurations.last().modelSelection)
        assertEquals(RealtimeVoicePhase.Listening, second.state.value.phase)
        controller.end()
        controller.start("current", selection, "", RealtimeVoiceMode.Simple)
        advanceUntilIdle()
        assertEquals(RealtimeVoiceMode.Simple, configurations.last().mode)
        controller.end()
        advanceUntilIdle()
    }

    @Test
    fun lazyStartFallsBackToSimpleWhenThePreselectedModeLacksItsPrerequisites() = runTest {
        val configurations = mutableListOf<RealtimeVoiceConfiguration>()
        val sessions = ArrayDeque<RealtimeVoiceSession>(listOf(FakeSession(), FakeSession()))
        val controller = InMemoryRealtimeVoiceCallController(object : RealtimeVoiceRepository {
            override fun createSession(configuration: RealtimeVoiceConfiguration): RealtimeVoiceSession {
                configurations += configuration
                return sessions.removeFirst()
            }
        }, realtimeCatalog(null), RealtimeCapable("qwen"), RecordingForeground())
        // 未绑定会话时预选助手模式：回落轻聊，避免带着非法配置起会话。
        controller.start(null, null, "", RealtimeVoiceMode.Chat)
        advanceUntilIdle()
        assertEquals(RealtimeVoiceMode.Simple, configurations.last().mode)
        controller.end()
        advanceUntilIdle()
        // Realtime 默认模型未配置时预选实时模式：同样回落轻聊。
        controller.start(null, null, "", RealtimeVoiceMode.Realtime)
        advanceUntilIdle()
        assertEquals(RealtimeVoiceMode.Simple, configurations.last().mode)
        controller.end()
        advanceUntilIdle()
    }

    @Test
    fun persistenceFailureBlocksModeSwitchAndKeepsTheOriginalSessionForRetry() = runTest {
        val active = FakeSession(canFinish = false)
        var created = 0
        val controller = InMemoryRealtimeVoiceCallController(object : RealtimeVoiceRepository {
            override fun createSession(configuration: RealtimeVoiceConfiguration): RealtimeVoiceSession {
                created++
                return active
            }
        }, mockk(relaxed = true), NoRealtimeSupport, RecordingForeground())
        controller.start("current", ModelSelection("service", "group", "model"), "", RealtimeVoiceMode.Simple)
        advanceUntilIdle()
        controller.selectMode(RealtimeVoiceMode.Chat)
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
        controller.start(null, null, "", RealtimeVoiceMode.Simple)
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

    private fun controller(
        sessions: ArrayDeque<RealtimeVoiceSession>,
        catalog: ModelCatalogRepository = mockk(relaxed = true),
        foreground: RecordingForeground = RecordingForeground(),
        capabilities: VoiceCapabilityResolver = NoRealtimeSupport,
    ) = InMemoryRealtimeVoiceCallController(object : RealtimeVoiceRepository {
        override fun createSession(configuration: RealtimeVoiceConfiguration): RealtimeVoiceSession = sessions.removeFirst()
    }, catalog, capabilities, foreground)

    /** 默认替身：没有任何服务接入原生 Realtime，因此 Realtime 模式不可进入。 */
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
                override val builtInVoices = emptyList<github.ponyhuang.gimi.domain.voice.model.TtsVoice>()
                override val customVoiceAllowed: Boolean = true
                override val uplink = github.ponyhuang.gimi.domain.voice.repository.RealtimeUplink(16_000, false)
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

    /** 带 Realtime 默认选择与音色覆盖的目录替身。 */
    private fun realtimeCatalog(
        selection: ModelSelection?,
        override: RealtimeVoiceOverride? = null,
    ): ModelCatalogRepository {
        val instance = realtimeInstance()
        val catalog = mockk<ModelCatalogRepository>()
        every { catalog.observeInstances() } returns MutableStateFlow(listOf(instance))
        every { catalog.observeRealtimeSelection() } returns MutableStateFlow(selection)
        every { catalog.observeRealtimeVoice() } returns MutableStateFlow(override)
        every { catalog.currentRealtimeSelection() } returns selection
        every { catalog.currentRealtimeVoice() } returns override
        every { catalog.currentInstances() } returns listOf(instance)
        every { catalog.currentTtsSelection() } returns null
        every { catalog.currentSpeechSelection() } returns null
        every { catalog.resolveConnection(any()) } answers {
            listOf(instance).resolveConnection(firstArg())
        }
        coEvery { catalog.awaitReady() } returns Unit
        return catalog
    }

    @Test
    fun realtimeModeUsesTheConfiguredDefaultSelectionAndVoice() = runTest {
        val selection = ModelSelection("instance-qwen", "instance-qwen-realtime", "qwen-audio-3.1-realtime-plus")
        val voice = RealtimeVoiceOverride(selection, "cloned_voice_id")
        val configurations = mutableListOf<RealtimeVoiceConfiguration>()
        val sessions = ArrayDeque<RealtimeVoiceSession>(listOf(FakeSession(), FakeSession()))
        val controller = InMemoryRealtimeVoiceCallController(object : RealtimeVoiceRepository {
            override fun createSession(configuration: RealtimeVoiceConfiguration): RealtimeVoiceSession {
                configurations += configuration
                return sessions.removeFirst()
            }
        }, realtimeCatalog(selection, voice), RealtimeCapable("qwen"), RecordingForeground())
        controller.start(null, null, "", RealtimeVoiceMode.Simple)
        advanceUntilIdle()
        assertTrue(controller.state.value.realtimeAvailable)
        controller.selectMode(RealtimeVoiceMode.Realtime)
        advanceUntilIdle()
        assertEquals(RealtimeVoiceMode.Realtime, configurations.last().mode)
        assertEquals(selection, configurations.last().modelSelection)
        assertEquals("cloned_voice_id", configurations.last().realtimeVoice)
        controller.end()
        advanceUntilIdle()
    }

    @Test
    fun realtimeModeIsUnavailableWithoutAConfiguredDefault() = runTest {
        val configurations = mutableListOf<RealtimeVoiceConfiguration>()
        val sessions = ArrayDeque<RealtimeVoiceSession>(listOf(FakeSession(), FakeSession()))
        val controller = InMemoryRealtimeVoiceCallController(object : RealtimeVoiceRepository {
            override fun createSession(configuration: RealtimeVoiceConfiguration): RealtimeVoiceSession {
                configurations += configuration
                return sessions.removeFirst()
            }
        }, realtimeCatalog(null), RealtimeCapable("qwen"), RecordingForeground())
        controller.start(null, null, "", RealtimeVoiceMode.Simple)
        advanceUntilIdle()
        assertFalse(controller.state.value.realtimeAvailable)
        controller.selectMode(RealtimeVoiceMode.Realtime)
        advanceUntilIdle()
        // 未配置 Realtime 默认模型时不进入该模式：不得新建会话。
        assertEquals(1, configurations.size)
        assertEquals(RealtimeVoiceMode.Simple, configurations.last().mode)
        controller.end()
        advanceUntilIdle()
    }

    @Test
    fun realtimeAssistantModeUsesTheConfiguredDefaultSelectionAndVoice() = runTest {
        val selection = ModelSelection("instance-qwen", "instance-qwen-realtime", "qwen-audio-3.1-realtime-plus")
        val voice = RealtimeVoiceOverride(selection, "cloned_voice_id")
        val configurations = mutableListOf<RealtimeVoiceConfiguration>()
        val sessions = ArrayDeque<RealtimeVoiceSession>(listOf(FakeSession(), FakeSession()))
        val controller = InMemoryRealtimeVoiceCallController(object : RealtimeVoiceRepository {
            override fun createSession(configuration: RealtimeVoiceConfiguration): RealtimeVoiceSession {
                configurations += configuration
                return sessions.removeFirst()
            }
        }, realtimeCatalog(selection, voice), RealtimeCapable("qwen"), RecordingForeground())
        controller.start(null, null, "", RealtimeVoiceMode.Simple)
        advanceUntilIdle()
        assertTrue(controller.state.value.realtimeAvailable)
        controller.selectMode(RealtimeVoiceMode.RealtimeAssistant)
        advanceUntilIdle()
        // 实时助手当前与实时轻聊同一引擎：默认 Realtime 模型与音色覆盖必须一致生效。
        assertEquals(RealtimeVoiceMode.RealtimeAssistant, configurations.last().mode)
        assertEquals(selection, configurations.last().modelSelection)
        assertEquals("cloned_voice_id", configurations.last().realtimeVoice)
        controller.end()
        advanceUntilIdle()
    }

    @Test
    fun realtimeAssistantModeIsUnavailableWithoutAConfiguredDefault() = runTest {
        val configurations = mutableListOf<RealtimeVoiceConfiguration>()
        val sessions = ArrayDeque<RealtimeVoiceSession>(listOf(FakeSession(), FakeSession()))
        val controller = InMemoryRealtimeVoiceCallController(object : RealtimeVoiceRepository {
            override fun createSession(configuration: RealtimeVoiceConfiguration): RealtimeVoiceSession {
                configurations += configuration
                return sessions.removeFirst()
            }
        }, realtimeCatalog(null), RealtimeCapable("qwen"), RecordingForeground())
        controller.start(null, null, "", RealtimeVoiceMode.Simple)
        advanceUntilIdle()
        assertFalse(controller.state.value.realtimeAvailable)
        controller.selectMode(RealtimeVoiceMode.RealtimeAssistant)
        advanceUntilIdle()
        // 实时助手同样要求已配置 Realtime 默认模型：不得新建会话。
        assertEquals(1, configurations.size)
        assertEquals(RealtimeVoiceMode.Simple, configurations.last().mode)
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
    ) : RealtimeVoiceSession {
        override val state = MutableStateFlow(RealtimeVoiceState())
        override suspend fun start() { state.value = state.value.copy(phase = RealtimeVoicePhase.Listening) }
        override fun selectAudioDevice(deviceId: Int?) {}
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
