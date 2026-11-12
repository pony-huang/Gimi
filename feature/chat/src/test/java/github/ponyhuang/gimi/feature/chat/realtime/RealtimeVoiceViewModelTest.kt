package github.ponyhuang.gimi.feature.chat.realtime

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionSnapshot
import github.ponyhuang.gimi.domain.conversation.testing.FakeAgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.usecase.ObserveConversationsUseCase
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelSettings
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.UpdateDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallController
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallState
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceMode
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceModeRepository
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptPreset
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptRepository
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 模式抽屉与会话的回归线：
 * 1. Realtime 可用性只看默认模型配置，不跟通话状态快照走（待启动/挂断后也必须可用）；
 * 2. 语音开启后不允许更换任何模式，只有待启动态能预选并在「开始通话」时生效；
 * 3. 轻聊预设的选中态经抽屉暴露并写穿到仓库，通话中被 mutate 闸门拒绝；
 * 4. 预选模式写穿到模式仓库，下次进入语音页恢复，通话中的被拒预选不落盘；
 * 5. 新建会话仅待启动态可用：助手模式创建并换绑待启动参数，通话中与轻聊/实时模式一律拒绝。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeVoiceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun configuredRealtimeModelIsAvailableBeforeCallStarts() = runTest {
        // 控制器快照保持默认（realtimeAvailable = false），模拟懒启动待启动/挂断后复位。
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem() // stateIn 的初始空状态
            val state = awaitItem()
            assertTrue(state.realtimeAvailable)
            assertEquals("Qwen Realtime", state.realtimeModelName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun staleRealtimeSelectionKeepsRealtimeModesUnavailable() = runTest {
        val viewModel = viewModel(
            settings = configuredRealtimeSettings().copy(
                realtimeSelection = ModelSelection("qwen", "realtime", "removed-model"),
            ),
        )

        viewModel.uiState.test {
            awaitItem()
            assertFalse(awaitItem().realtimeAvailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun realtimeModeCanBePreselectedAndStartedBeforeCallStarts() = runTest {
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) {
            every { state } returns MutableStateFlow(RealtimeVoiceCallState())
        }
        val viewModel = viewModel(controller = controller)

        viewModel.uiState.test {
            awaitItem()
            assertTrue(awaitItem().realtimeAvailable)
            viewModel.selectMode(RealtimeVoiceMode.RealtimeAssistant)
            assertEquals(RealtimeVoiceMode.RealtimeAssistant, awaitItem().mode)
            cancelAndIgnoreRemainingEvents()
        }

        val selection = ModelSelection("qwen", "chat", "chat-model")
        viewModel.prepare("conversation", selection, "当前聊天")
        viewModel.startCall()
        verify {
            controller.start("conversation", selection, "当前聊天", RealtimeVoiceMode.RealtimeAssistant)
        }
    }

    @Test
    fun modeCannotBeChangedWhileCallIsActive() = runTest {
        val callState = MutableStateFlow(
            RealtimeVoiceCallState(active = true, mode = RealtimeVoiceMode.Chat, realtimeAvailable = true),
        )
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) {
            every { state } returns callState
        }
        val viewModel = viewModel(controller = controller)

        viewModel.uiState.test {
            awaitItem()
            assertEquals(RealtimeVoiceMode.Chat, awaitItem().mode)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.selectMode(RealtimeVoiceMode.Realtime)
        verify(exactly = 0) { controller.selectMode(any()) }
        // 挂断回到待启动态后仍是通话前的预选模式，证明通话中的选择被彻底忽略。
        callState.value = RealtimeVoiceCallState()
        viewModel.uiState.test {
            var state = awaitItem()
            if (state.mode != RealtimeVoiceMode.Simple) state = awaitItem()
            assertEquals(RealtimeVoiceMode.Simple, state.mode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun promptPresetsAreExposedThroughSettingsState() = runTest {
        val presets = FakePresetRepository()
        val viewModel = viewModel(promptPresets = presets)

        viewModel.uiState.test {
            awaitItem() // stateIn 的初始空状态
            val settings = awaitItem().settings
            assertEquals(listOf("builtin.roast", "builtin.companion"), settings.builtInPromptPresets.map { it.id })
            assertEquals(listOf("p1"), settings.userPromptPresets.map { it.id })
            assertNull(settings.selectedPromptPresetId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun promptPresetSelectionWritesThroughRepository() = runTest {
        val presets = FakePresetRepository()
        val viewModel = viewModel(promptPresets = presets)

        viewModel.uiState.test {
            awaitItem() // stateIn 的初始空状态
            awaitItem()
            viewModel.selectPromptPreset("builtin.roast")
            // 写入走 mutate 协程：等到状态合流出新选中项，仓库此时必然已落值。
            while (awaitItem().settings.selectedPromptPresetId != "builtin.roast") {
                // 继续等待
            }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("builtin.roast", presets.selectedPresetId.value)
    }

    @Test
    fun promptPresetSelectionIsBlockedWhileCallIsActive() = runTest {
        val callState = MutableStateFlow(RealtimeVoiceCallState(active = true))
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) {
            every { state } returns callState
        }
        val presets = FakePresetRepository()
        val viewModel = viewModel(controller = controller, promptPresets = presets)

        viewModel.settingsWriteBlockedRevision.test {
            assertEquals(0L, awaitItem())
            viewModel.selectPromptPreset("builtin.roast")
            assertEquals(1L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // 通话中被拒的写入不得穿透仓库。
        assertNull(presets.selectedPresetId.value)
    }

    @Test
    fun modeSelectionPersistsToModeRepositoryAndRestoresOnNextViewModel() = runTest {
        val modeRepository = FakeModeRepository()
        val viewModel = viewModel(modeRepository = modeRepository)

        // 预选即写穿到模式仓库，供下次进入语音页恢复。
        viewModel.selectMode(RealtimeVoiceMode.Realtime)
        assertEquals(RealtimeVoiceMode.Realtime, modeRepository.lastMode.value)

        // 新的 ViewModel（对应重新进入语音页）以上次使用的模式作为预选初值。
        val restored = viewModel(modeRepository = modeRepository)
        restored.uiState.test {
            awaitItem() // stateIn 的初始空状态
            assertEquals(RealtimeVoiceMode.Realtime, awaitItem().mode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun modeSelectionBlockedByActiveCallDoesNotOverwriteModeRepository() = runTest {
        val callState = MutableStateFlow(RealtimeVoiceCallState(active = true))
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) {
            every { state } returns callState
        }
        val modeRepository = FakeModeRepository()
        val viewModel = viewModel(controller = controller, modeRepository = modeRepository)

        viewModel.selectMode(RealtimeVoiceMode.RealtimeAssistant)

        assertEquals(RealtimeVoiceMode.Simple, modeRepository.lastMode.value)
    }

    @Test
    fun newSessionInReadyStateRebindsTheFreshlyCreatedAssistantConversation() = runTest {
        val selection = ModelSelection("qwen", "chat", "chat-model")
        val newSelection = ModelSelection("qwen", "chat", "new-model")
        val sessionResolver = mockk<ConversationSessionResolver> {
            coEvery { createAndActivate() } returns ConversationSessionSnapshot(
                "new-conversation", newSelection, ConversationToolConfiguration(),
            )
        }
        val observeConversations = mockk<ObserveConversationsUseCase>()
        every { observeConversations() } returns MutableStateFlow(
            listOf(Conversation(id = "new-conversation", title = "新对话")),
        )
        val controller = mockk<RealtimeVoiceCallController>(relaxed = true) {
            every { state } returns MutableStateFlow(RealtimeVoiceCallState())
        }
        val viewModel = viewModel(
            controller = controller,
            sessionResolver = sessionResolver,
            observeConversations = observeConversations,
        )
        viewModel.selectMode(RealtimeVoiceMode.Chat)
        viewModel.prepare("old-conversation", selection, "当前聊天")
        viewModel.newSession()

        viewModel.uiState.test {
            awaitItem() // stateIn 的初始空状态
            // 换绑完成：待启动态展示的会话标题来自新建会话。
            while (awaitItem().conversationTitle != "新对话") {
                // 继续等待
            }
            cancelAndIgnoreRemainingEvents()
        }

        // 「开始通话」即绑定新会话：待启动参数的 id、模型、标题已被整体换绑。
        viewModel.startCall()
        verify { controller.start("new-conversation", newSelection, "新对话", RealtimeVoiceMode.Chat) }
    }

    @Test
    fun newSessionIsBlockedWhileCallIsActive() = runTest {
        val callState = MutableStateFlow(RealtimeVoiceCallState(active = true, mode = RealtimeVoiceMode.Chat))
        val sessionResolver = mockk<ConversationSessionResolver>(relaxed = true)
        val viewModel = viewModel(
            controller = mockk(relaxed = true) { every { state } returns callState },
            sessionResolver = sessionResolver,
        )
        viewModel.prepare("old-conversation", ModelSelection("qwen", "chat", "chat-model"), "当前聊天")

        viewModel.newSession()

        // 通话中不支持新建：重建会话会打断采集/播放，必须先结束通话（UI 置灰，这里是兜底）。
        coVerify(exactly = 0) { sessionResolver.createAndActivate() }
    }

    @Test
    fun newSessionIsNoOpForModesWithoutPersistence() = runTest {
        val sessionResolver = mockk<ConversationSessionResolver>(relaxed = true)
        val viewModel = viewModel(sessionResolver = sessionResolver)
        viewModel.prepare("old-conversation", ModelSelection("qwen", "chat", "chat-model"), "当前聊天")

        // 默认预选轻聊：轻聊/实时/实时助手的内存会话随每次起呼新建，无持久化会话可换。
        viewModel.newSession()

        coVerify(exactly = 0) { sessionResolver.createAndActivate() }
    }

    private fun viewModel(
        settings: DefaultModelSettings = configuredRealtimeSettings(),
        controller: RealtimeVoiceCallController = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(RealtimeVoiceCallState())
        },
        sessionResolver: ConversationSessionResolver = mockk(relaxed = true),
        observeConversations: ObserveConversationsUseCase = mockk(relaxed = true),
        promptPresets: SimpleChatPromptRepository = FakePresetRepository(),
        modeRepository: RealtimeVoiceModeRepository = FakeModeRepository(),
    ): RealtimeVoiceViewModel {
        val voiceCapabilities = mockk<VoiceCapabilityResolver>(relaxed = true) {
            every { isSupported(any(), ModelCapability.Realtime) } returns true
        }
        val observeSettings = mockk<ObserveDefaultModelSettingsUseCase>()
        every { observeSettings() } returns MutableStateFlow(settings)
        return RealtimeVoiceViewModel(
            controller = controller,
            sessionResolver = sessionResolver,
            observeConversations = observeConversations,
            updateSettings = mockk<UpdateDefaultModelSettingsUseCase>(relaxed = true),
            runWhenAgentIdle = RunWhenAgentIdleUseCase(FakeAgentRuntimeGate()),
            ttsVoiceRepository = mockk<TtsVoiceRepository>(relaxed = true),
            promptPresets = promptPresets,
            modeRepository = modeRepository,
            voiceCapabilities = voiceCapabilities,
            observeSettings = observeSettings,
        )
    }

    /** 已配置 Realtime 默认模型的典型配置；其余默认模型留空以避开音色列表加载。 */
    private fun configuredRealtimeSettings(): DefaultModelSettings = DefaultModelSettings(
        services = listOf(
            LLMModelSetting(
                id = "qwen",
                name = "Qwen",
                isEnabled = true,
                apiKey = "key",
                apiBaseUrl = "wss://example.com",
                apiProtocol = ApiProtocol.OpenaiChatcompletions,
                anthropicBaseUrl = "",
                groups = listOf(
                    ModelGroup(
                        id = "realtime",
                        name = "Realtime",
                        models = listOf(
                            Model(
                                id = "qwen-realtime",
                                name = "Qwen Realtime",
                                capabilities = setOf(ModelCapability.Realtime),
                            ),
                        ),
                    ),
                    ModelGroup(id = "chat", name = "Chat", models = listOf(Model("chat-model", "Chat"))),
                ),
            ),
        ),
        assistantSelection = null,
        fastSelection = null,
        speechSelection = null,
        ttsSelection = null,
        ttsVoiceId = "",
        realtimeSelection = ModelSelection("qwen", "realtime", "qwen-realtime"),
    )
}

/** 内存版语音模式仓库；写穿与回落语义与真实仓库一致。 */
private class FakeModeRepository : RealtimeVoiceModeRepository {
    override val lastMode = MutableStateFlow(RealtimeVoiceMode.Simple)

    override fun setLastMode(mode: RealtimeVoiceMode) {
        lastMode.value = mode
    }
}

/** 内存版轻聊预设仓库；选中归一化规则与真实仓库一致——无效 id 一律落空。 */
private class FakePresetRepository : SimpleChatPromptRepository {
    override val presets = MutableStateFlow(
        listOf(SimpleChatPromptPreset(id = "p1", name = "简洁速答", content = "直接给结论")),
    )

    override val builtInPresets: List<SimpleChatPromptPreset> = listOf(
        SimpleChatPromptPreset(id = "builtin.roast", name = "贴吧嘴臭老哥", content = "嘴臭人设"),
        SimpleChatPromptPreset(id = "builtin.companion", name = "情感陪伴师", content = "陪伴人设"),
    )

    private val mutableSelectedId = MutableStateFlow<String?>(null)

    override val selectedPresetId: StateFlow<String?> = mutableSelectedId

    override fun upsertPreset(preset: SimpleChatPromptPreset) {
        val existing = presets.value.indexOfFirst { it.id == preset.id }
        presets.value = if (existing >= 0) {
            presets.value.toMutableList().also { it[existing] = preset }
        } else {
            presets.value + preset
        }
    }

    override fun deletePreset(presetId: String) {
        if (mutableSelectedId.value == presetId) selectPreset(null)
        presets.value = presets.value.filterNot { it.id == presetId }
    }

    override fun selectPreset(presetId: String?) {
        val target = presetId?.takeIf { id ->
            builtInPresets.any { it.id == id } || presets.value.any { it.id == id }
        }
        mutableSelectedId.value = target
    }
}
