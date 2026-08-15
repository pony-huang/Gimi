package github.ponyhuang.gimi.data.assistant

import github.ponyhuang.gimi.domain.assistant.model.AssistantConfigIssue
import github.ponyhuang.gimi.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.gimi.domain.assistant.repository.VoiceSessionStore
import github.ponyhuang.gimi.domain.conversation.model.ChatFunctionCall
import github.ponyhuang.gimi.domain.conversation.model.ChatRunEvent
import github.ponyhuang.gimi.domain.conversation.model.ChatRunPart
import github.ponyhuang.gimi.domain.conversation.model.ToolConfirmationRequest
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ToolApprovalRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeState
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.speech.repository.SpeechRecognitionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAssistantSessionCoordinatorTest {

    private val conversations: ConversationRepository = mockk(relaxed = true)
    private val chatAgent: ChatAgentRepository = mockk()
    private val modelCatalog: ModelCatalogRepository = mockk()
    private val speechRecognition: SpeechRecognitionRepository = mockk()
    private val gate = RecordingAgentRuntimeGate()
    private val store = FakeVoiceSessionStore()
    private val toolApproval = FakeToolApprovalRepository()
    private lateinit var coordinator: DefaultAssistantSessionCoordinator

    private val selection = ModelSelection("svc", "grp", "chat-model")

    @Before
    fun setUp() {
        coEvery { modelCatalog.awaitReady() } returns Unit
        every { modelCatalog.currentAssistantSelection() } returns selection
        every { modelCatalog.currentServices() } returns listOf(service())
        every { speechRecognition.availability } returns flowOf(true)
        coEvery { conversations.loadMessages(any()) } returns null
        coEvery { conversations.createConversation(any(), any()) } returns "voice-session-1"
        coordinator = DefaultAssistantSessionCoordinator(
            conversations = conversations,
            chatAgent = chatAgent,
            modelCatalog = modelCatalog,
            speechRecognition = speechRecognition,
            runtimeGate = gate,
            sessionStore = store,
            toolApproval = toolApproval,
        )
    }

    @Test
    fun `submit folds partial events into the current turn answer`() = runTest {
        coordinator.taskDispatcher = StandardTestDispatcher(testScheduler)
        coEvery { chatAgent.send(any(), any(), any(), any()) } returns flowOf(
            textEvent("你", partial = true),
            textEvent("好", partial = true),
            textEvent("你好。", partial = false),
        )

        coordinator.submit("打招呼", AssistantInvocationSource.BLUETOOTH_WAKE)
        advanceUntilIdle()

        val state = coordinator.state.value
        assertEquals(AssistantSessionPhase.FOLLOW_UP_IDLE, state.phase)
        assertEquals("voice-session-1", state.sessionId)
        assertEquals("打招呼", state.turn?.userText)
        assertEquals("你好。", state.turn?.responseText)
        assertEquals(false, state.taskActive)
        assertEquals("voice-session-1", store.voiceSessionId.value)
    }

    @Test
    fun `submit reuses the persisted voice session`() = runTest {
        coordinator.taskDispatcher = StandardTestDispatcher(testScheduler)
        store.setVoiceSessionId("existing-session")
        coEvery { conversations.loadMessages("existing-session") } returns emptyList()
        coEvery { chatAgent.send(any(), any(), any(), any()) } returns flowOf(textEvent("好"))

        coordinator.submit("继续", AssistantInvocationSource.BLUETOOTH_WAKE)
        advanceUntilIdle()

        assertEquals("existing-session", coordinator.state.value.sessionId)
        coVerify(exactly = 0) { conversations.createConversation(any(), any()) }
    }

    @Test
    fun `submissions to the shared voice session are serialized`() = runTest {
        coordinator.taskDispatcher = StandardTestDispatcher(testScheduler)
        val firstGate = CompletableDeferred<Unit>()
        var sendCount = 0
        coEvery { chatAgent.send(any(), any(), any(), any()) } answers {
            sendCount++
            if (sendCount == 1) {
                flow {
                    emit(textEvent("一"))
                    firstGate.await()
                }
            } else {
                flowOf(textEvent("二"))
            }
        }

        val first = async { coordinator.submit("第一", AssistantInvocationSource.BLUETOOTH_WAKE) }
        advanceUntilIdle()
        val second = async { coordinator.submit("第二", AssistantInvocationSource.BLUETOOTH_WAKE) }
        advanceUntilIdle()

        assertEquals(1, sendCount)
        firstGate.complete(Unit)
        first.await()
        second.await()
        advanceUntilIdle()
        assertEquals(2, sendCount)
        assertEquals("二", coordinator.state.value.turn?.responseText)
    }

    @Test
    fun `tool calls switch phase and confirmation approval continues the task`() = runTest {
        coordinator.taskDispatcher = StandardTestDispatcher(testScheduler)
        coEvery { chatAgent.send(any(), any(), any(), any()) } returns flowOf(
            toolCallEvent("call-1", "brightness_set"),
            confirmationEvent("confirm-1", "brightness_set"),
        )
        coEvery {
            chatAgent.respondToToolConfirmation(any(), any(), any())
        } returns flowOf(textEvent("已调亮。"))

        val submission = async { coordinator.submit("调亮屏幕", AssistantInvocationSource.BLUETOOTH_WAKE) }
        runCurrent()

        val awaiting = coordinator.state.value
        assertEquals(AssistantSessionPhase.AWAITING_CONFIRMATION, awaiting.phase)
        assertEquals("brightness_set", awaiting.pendingConfirmation?.toolName)
        assertEquals(listOf("brightness_set"), awaiting.turn?.toolNames)

        coordinator.respondToConfirmation("confirm-1", true)
        submission.await()
        advanceUntilIdle()

        coVerify { chatAgent.respondToToolConfirmation("voice-session-1", "confirm-1", true) }
        assertEquals("已调亮。", coordinator.state.value.turn?.responseText)
        assertEquals(AssistantSessionPhase.FOLLOW_UP_IDLE, coordinator.state.value.phase)
    }

    @Test
    fun `stale confirmation id cannot approve the current request`() = runTest {
        coordinator.taskDispatcher = StandardTestDispatcher(testScheduler)
        coEvery { chatAgent.send(any(), any(), any(), any()) } returns flowOf(
            confirmationEvent("confirm-current", "brightness_set"),
        )
        coEvery {
            chatAgent.respondToToolConfirmation(any(), any(), any())
        } returns flowOf(textEvent("已调亮。"))

        val submission = async { coordinator.submit("调亮屏幕", AssistantInvocationSource.BLUETOOTH_WAKE) }
        runCurrent()

        assertFalse(coordinator.respondToConfirmation("confirm-stale", true))
        assertEquals(
            "confirm-current",
            coordinator.state.value.pendingConfirmation?.confirmationCallId,
        )
        assertTrue(coordinator.respondToConfirmation("confirm-current", true))
        submission.await()
        advanceUntilIdle()

        coVerify {
            chatAgent.respondToToolConfirmation("voice-session-1", "confirm-current", true)
        }
    }

    @Test
    fun `confirmation is auto rejected after fifteen seconds`() = runTest {
        coordinator.taskDispatcher = StandardTestDispatcher(testScheduler)
        coEvery { chatAgent.send(any(), any(), any(), any()) } returns flowOf(
            confirmationEvent("confirm-1", "camera_open"),
        )
        coEvery {
            chatAgent.respondToToolConfirmation(any(), any(), any())
        } returns flowOf(textEvent("已取消。"))

        val submission = async { coordinator.submit("打开相机", AssistantInvocationSource.BLUETOOTH_WAKE) }
        runCurrent()
        assertEquals(AssistantSessionPhase.AWAITING_CONFIRMATION, coordinator.state.value.phase)

        advanceTimeBy(15_100)
        submission.await()
        advanceUntilIdle()

        coVerify { chatAgent.respondToToolConfirmation("voice-session-1", "confirm-1", false) }
    }

    @Test
    fun `stop cancels the running task but hideOverlay keeps it`() = runTest {
        coordinator.taskDispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        coEvery { chatAgent.send(any(), any(), any(), any()) } returns flow {
            emit(textEvent("处理中"))
            gate.await()
        }

        val submission = async { coordinator.submit("长任务", AssistantInvocationSource.BLUETOOTH_WAKE) }
        advanceUntilIdle()
        assertTrue(coordinator.state.value.taskActive)

        coordinator.hideOverlay()
        advanceUntilIdle()
        assertTrue(coordinator.state.value.taskActive)
        assertEquals(false, coordinator.state.value.overlayVisible)

        coordinator.stop()
        submission.await()
        advanceUntilIdle()
        assertEquals(false, coordinator.state.value.taskActive)
    }

    @Test
    fun `missing default model reports configuration issue`() = runTest {
        coordinator.taskDispatcher = StandardTestDispatcher(testScheduler)
        every { modelCatalog.currentAssistantSelection() } returns null
        every { modelCatalog.currentServices() } returns emptyList()
        coEvery { chatAgent.send(any(), any(), any(), any()) } returns flowOf()

        assertEquals(AssistantConfigIssue.MISSING_AGENT_MODEL, coordinator.configurationIssue())

        coordinator.submit("你好", AssistantInvocationSource.BLUETOOTH_WAKE)
        advanceUntilIdle()
        assertEquals(AssistantSessionPhase.MISSING_CONFIG, coordinator.state.value.phase)
        coVerify(exactly = 0) { chatAgent.send(any(), any(), any(), any()) }
    }

    @Test
    fun `missing speech recognition reports configuration issue`() = runTest {
        every { speechRecognition.availability } returns flowOf(false)

        assertEquals(AssistantConfigIssue.MISSING_STT, coordinator.configurationIssue())
    }

    @Test
    fun `agent failure surfaces error state`() = runTest {
        coordinator.taskDispatcher = StandardTestDispatcher(testScheduler)
        coEvery { chatAgent.send(any(), any(), any(), any()) } returns flow {
            throw IllegalStateException("network down")
        }

        coordinator.submit("你好", AssistantInvocationSource.BLUETOOTH_WAKE)
        advanceUntilIdle()

        val state = coordinator.state.value
        assertEquals(AssistantSessionPhase.ERROR, state.phase)
        assertEquals("network down", state.errorMessage)
        assertEquals(false, state.taskActive)
    }

    @Test
    fun `quick tile source maps to system assistant lease`() = runTest {
        coordinator.taskDispatcher = StandardTestDispatcher(testScheduler)
        coEvery { chatAgent.send(any(), any(), any(), any()) } returns flowOf(textEvent("好"))

        coordinator.submit("你好", AssistantInvocationSource.BLUETOOTH_WAKE)
        advanceUntilIdle()

        assertEquals(
            AgentTaskSource.BLUETOOTH_VOICE to "voice-session-1",
            gate.acquisitions.single(),
        )
    }

    @Test
    fun `bluetooth source maps to bluetooth voice lease`() = runTest {
        coordinator.taskDispatcher = StandardTestDispatcher(testScheduler)
        coEvery { chatAgent.send(any(), any(), any(), any()) } returns flowOf(textEvent("好"))

        coordinator.submit("你好", AssistantInvocationSource.BLUETOOTH_WAKE)
        advanceUntilIdle()

        assertEquals(
            AgentTaskSource.BLUETOOTH_VOICE to "voice-session-1",
            gate.acquisitions.single(),
        )
    }

    @Test
    fun `full access auto approves confirmation without waiting`() = runTest {
        coordinator.taskDispatcher = StandardTestDispatcher(testScheduler)
        toolApproval.setFullAccess(true)
        coEvery { chatAgent.send(any(), any(), any(), any()) } returns flowOf(
            confirmationEvent("confirm-1", "brightness_set"),
        )
        coEvery {
            chatAgent.respondToToolConfirmation(any(), any(), any())
        } returns flowOf(textEvent("已调亮。"))

        coordinator.submit("调亮屏幕", AssistantInvocationSource.BLUETOOTH_WAKE)
        advanceUntilIdle()

        coVerify { chatAgent.respondToToolConfirmation("voice-session-1", "confirm-1", true) }
        assertEquals(AssistantSessionPhase.FOLLOW_UP_IDLE, coordinator.state.value.phase)
        assertEquals("已调亮。", coordinator.state.value.turn?.responseText)
    }

    @Test
    fun `always allowed tool auto approves confirmation without waiting`() = runTest {
        coordinator.taskDispatcher = StandardTestDispatcher(testScheduler)
        toolApproval.setAlwaysAllowed("brightness_set")
        coEvery { chatAgent.send(any(), any(), any(), any()) } returns flowOf(
            confirmationEvent("confirm-1", "brightness_set"),
        )
        coEvery {
            chatAgent.respondToToolConfirmation(any(), any(), any())
        } returns flowOf(textEvent("已调亮。"))

        coordinator.submit("调亮屏幕", AssistantInvocationSource.BLUETOOTH_WAKE)
        advanceUntilIdle()

        coVerify { chatAgent.respondToToolConfirmation("voice-session-1", "confirm-1", true) }
        assertEquals("已调亮。", coordinator.state.value.turn?.responseText)
    }

    private fun service(): LLMModelSetting = LLMModelSetting(
        id = "svc",
        name = "Service",
        isEnabled = true,
        apiKey = "key",
        apiBaseUrl = "https://example.com",
        apiProtocol = ApiProtocol.Standard,
        anthropicBaseUrl = "",
        groups = listOf(
            ModelGroup(
                id = "grp",
                name = "Group",
                models = listOf(Model("chat-model", "Chat")),
            ),
        ),
    )

    private fun textEvent(
        text: String,
        partial: Boolean = false,
    ): ChatRunEvent = ChatRunEvent(
        id = "event-${text.hashCode()}-$partial",
        invocationId = "invocation",
        author = "model",
        parts = listOf(ChatRunPart(text = text)),
        functionCalls = emptyList(),
        functionResponses = emptyList(),
        partial = partial,
        turnComplete = !partial,
        errorCode = null,
        errorMessage = null,
        timestamp = 0L,
    )

    private fun toolCallEvent(callId: String, toolName: String): ChatRunEvent = ChatRunEvent(
        id = "tool-$callId",
        invocationId = "invocation",
        author = "model",
        parts = emptyList(),
        functionCalls = listOf(ChatFunctionCall(callId, toolName, emptyMap())),
        functionResponses = emptyList(),
        partial = false,
        turnComplete = false,
        errorCode = null,
        errorMessage = null,
        timestamp = 0L,
    )

    private fun confirmationEvent(callId: String, toolName: String): ChatRunEvent = ChatRunEvent(
        id = "confirmation-$callId",
        invocationId = "invocation",
        author = "model",
        parts = emptyList(),
        functionCalls = listOf(
            ChatFunctionCall(
                id = callId,
                name = "adk_request_confirmation",
                args = emptyMap(),
                confirmationRequest = ToolConfirmationRequest(toolName, emptyMap()),
            ),
        ),
        functionResponses = emptyList(),
        partial = false,
        turnComplete = false,
        errorCode = null,
        errorMessage = null,
        timestamp = 0L,
    )
}

private class FakeToolApprovalRepository : ToolApprovalRepository {
    private val _alwaysAllowedToolNames = MutableStateFlow<Set<String>>(emptySet())
    private val _fullAccess = MutableStateFlow(false)
    override val alwaysAllowedToolNames = _alwaysAllowedToolNames
    override val fullAccess = _fullAccess

    override fun setAlwaysAllowed(toolName: String) {
        _alwaysAllowedToolNames.value = _alwaysAllowedToolNames.value + toolName
    }

    override fun removeAlwaysAllowed(toolName: String) {
        _alwaysAllowedToolNames.value = _alwaysAllowedToolNames.value - toolName
    }

    override fun setFullAccess(enabled: Boolean) {
        _fullAccess.value = enabled
    }

    override fun isAutoApproved(toolName: String): Boolean =
        _fullAccess.value || toolName in _alwaysAllowedToolNames.value
}

private class FakeVoiceSessionStore : VoiceSessionStore {
    private val _voiceSessionId = MutableStateFlow<String?>(null)
    override val voiceSessionId = _voiceSessionId

    override fun setVoiceSessionId(value: String?) {
        _voiceSessionId.value = value
    }
}

private class RecordingAgentRuntimeGate : AgentRuntimeGate {
    val acquisitions = mutableListOf<Pair<AgentTaskSource, String?>>()
    override val state = MutableStateFlow<AgentRuntimeState>(AgentRuntimeState.Idle)

    override suspend fun acquire(
        source: AgentTaskSource,
        sessionId: String?,
        phase: github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase,
    ): AgentRunLease {
        acquisitions += source to sessionId
        return object : AgentRunLease {
            override fun updatePhase(phase: github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase) = Unit
            override fun release() = Unit
        }
    }

    override suspend fun <T> runMutation(block: suspend () -> T): AgentMutationResult<T> =
        AgentMutationResult.Applied(block())
}
