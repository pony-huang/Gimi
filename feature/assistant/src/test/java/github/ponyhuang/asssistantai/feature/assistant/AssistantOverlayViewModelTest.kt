package github.ponyhuang.asssistantai.feature.assistant

import app.cash.turbine.test
import github.ponyhuang.asssistantai.core.testing.MainDispatcherRule
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantConfigIssue
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantSessionState
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantTurn
import github.ponyhuang.asssistantai.domain.assistant.model.PendingAssistantConfirmation
import github.ponyhuang.asssistantai.domain.assistant.repository.AssistantConfirmationHandler
import github.ponyhuang.asssistantai.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.asssistantai.domain.speech.model.SpeechPlaybackState
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechPlaybackRepository
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechRecognitionRepository
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechSynthesisRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantOverlayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val coordinatorState = MutableStateFlow(AssistantSessionState())
    private val coordinator = FakeCoordinator(coordinatorState)
    private val speechRecognition: SpeechRecognitionRepository = mockk()
    private val playbackState = MutableStateFlow(SpeechPlaybackState())
    private val playback: SpeechPlaybackRepository = mockk(relaxed = true) {
        every { state } returns playbackState
        every { errors } returns MutableSharedFlow()
    }
    private val synthesis: SpeechSynthesisRepository = mockk {
        every { isAvailable() } returns false
    }

    private fun viewModel(): AssistantOverlayViewModel = AssistantOverlayViewModel(
        coordinator = coordinator,
        speechRecognition = speechRecognition,
        speechPlayback = playback,
        speechSynthesis = synthesis,
    ).apply {
        recordingDispatcher = mainDispatcherRule.dispatcher
    }

    @Test
    fun `invocation with active task only restores state without recording`() = runTest {
        coordinatorState.value = AssistantSessionState(
            sessionId = "s1",
            phase = AssistantSessionPhase.GENERATING,
            turn = AssistantTurn(userText = "问题"),
            taskActive = true,
        )
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect() }

        vm.onInvoked(AssistantInvocationSource.TILE, microphoneGranted = true)
        runCurrent()

        val state = vm.uiState.value
        assertEquals(AssistantSessionPhase.GENERATING, state.phase)
        assertEquals("问题", state.userText)
        assertTrue(coordinator.invocations.contains(AssistantInvocationSource.TILE))
        assertEquals(false, vm.uiState.value.inputEnabled)
    }

    @Test
    fun `keyboard input submits draft and resets idle timer`() = runTest {
        coordinatorState.value = AssistantSessionState(
            sessionId = "s1",
            phase = AssistantSessionPhase.FOLLOW_UP_IDLE,
        )
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect() }
        vm.onInvoked(AssistantInvocationSource.TILE, microphoneGranted = true)
        runCurrent()

        vm.onAction(AssistantOverlayAction.DraftChanged("今天天气"))
        vm.onAction(AssistantOverlayAction.SubmitDraft)
        runCurrent()

        assertEquals("今天天气", coordinator.submissions.single().first)
        assertEquals("", vm.uiState.value.draftText)
    }

    @Test
    fun `idle for thirty seconds after settle closes the overlay`() = runTest {
        coordinatorState.value = AssistantSessionState(
            sessionId = "s1",
            phase = AssistantSessionPhase.GENERATING,
            taskActive = true,
        )
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect() }
        vm.onInvoked(AssistantInvocationSource.TILE, microphoneGranted = true)
        runCurrent()

        vm.events.test {
            coordinatorState.value = AssistantSessionState(
                sessionId = "s1",
                phase = AssistantSessionPhase.FOLLOW_UP_IDLE,
                turn = AssistantTurn(userText = "问题", responseText = "回答"),
                taskActive = false,
            )
            runCurrent()
            advanceTimeBy(IDLE_CLOSE_TIMEOUT_MS + 500)
            runCurrent()
            assertEquals(AssistantOverlayEvent.CloseOverlay, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // TTS 不可用 → 非阻塞提示而非播报
        assertTrue(vm.uiState.value.ttsNotice)
    }

    @Test
    fun `confirmation countdown derives from deadline`() = runTest {
        coordinatorState.value = AssistantSessionState(
            sessionId = "s1",
            phase = AssistantSessionPhase.AWAITING_CONFIRMATION,
            taskActive = true,
            pendingConfirmation = PendingAssistantConfirmation(
                confirmationCallId = "c1",
                toolName = "camera_open",
                deadlineEpochMs = System.currentTimeMillis() + 15_000,
            ),
        )
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect() }
        vm.onInvoked(AssistantInvocationSource.TILE, microphoneGranted = true)
        runCurrent()

        val state = vm.uiState.value
        assertEquals(AssistantSessionPhase.AWAITING_CONFIRMATION, state.phase)
        assertEquals("camera_open", state.pendingConfirmation?.toolName)
        assertTrue(state.confirmationRemainingSeconds in 13..15)

        vm.onAction(AssistantOverlayAction.ApproveConfirmation)
        assertEquals(listOf(true), coordinator.confirmationResponses)
    }

    @Test
    fun `missing configuration is surfaced from coordinator check`() = runTest {
        coordinator.configIssue = AssistantConfigIssue.MISSING_AGENT_MODEL
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect() }

        vm.onInvoked(AssistantInvocationSource.TILE, microphoneGranted = true)
        advanceUntilIdle()

        assertEquals(AssistantSessionPhase.MISSING_CONFIG, vm.uiState.value.phase)
        assertEquals(AssistantConfigIssue.MISSING_AGENT_MODEL, vm.uiState.value.configIssue)
    }

    @Test
    fun `recorder startup failure leaves preparing state and enables retry`() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect() }

        vm.onInvoked(AssistantInvocationSource.TILE, microphoneGranted = true)
        advanceUntilIdle()

        assertEquals(AssistantSessionPhase.FOLLOW_UP_IDLE, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.canRetryListening)
    }

    @Test
    fun `configuration check failure leaves preparing state and enables retry`() = runTest {
        coordinator.configFailure = IllegalStateException("catalog unavailable")
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect() }

        vm.onInvoked(AssistantInvocationSource.TILE, microphoneGranted = true)
        advanceUntilIdle()

        assertEquals(AssistantSessionPhase.FOLLOW_UP_IDLE, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.canRetryListening)
    }

    @Test
    fun `transcription failure is surfaced as an error`() = runTest {
        coEvery { speechRecognition.transcribe(any()) } throws
            IllegalStateException("speech service unavailable")
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect() }
        val transcribe = AssistantOverlayViewModel::class.java.getDeclaredMethod(
            "transcribeAndSubmit",
            ByteArray::class.java,
        ).apply { isAccessible = true }

        transcribe.invoke(vm, byteArrayOf(1, 2))
        advanceUntilIdle()

        assertEquals(AssistantSessionPhase.ERROR, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.canRetryListening)
    }

    @Test
    fun `stop task cancels coordinator task and playback`() = runTest {
        coordinatorState.value = AssistantSessionState(
            sessionId = "s1",
            phase = AssistantSessionPhase.GENERATING,
            taskActive = true,
        )
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect() }
        vm.onInvoked(AssistantInvocationSource.TILE, microphoneGranted = true)
        runCurrent()

        vm.onAction(AssistantOverlayAction.StopTask)

        assertTrue(coordinator.stopCalled)
        verify { playback.stop() }
    }

    @Test
    fun `close hides overlay without cancelling the task`() = runTest {
        coordinatorState.value = AssistantSessionState(
            sessionId = "s1",
            phase = AssistantSessionPhase.GENERATING,
            taskActive = true,
        )
        val vm = viewModel()
        backgroundScope.launch { vm.uiState.collect() }
        vm.onInvoked(AssistantInvocationSource.TILE, microphoneGranted = true)
        runCurrent()

        vm.events.test {
            vm.onAction(AssistantOverlayAction.CloseOverlay)
            assertEquals(AssistantOverlayEvent.CloseOverlay, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(coordinator.hideCalled)
        assertEquals(false, coordinator.stopCalled)
    }

    private class FakeCoordinator(
        override val state: MutableStateFlow<AssistantSessionState>,
    ) : AssistantSessionCoordinator {
        val submissions = mutableListOf<Pair<String, AssistantInvocationSource>>()
        val confirmationResponses = mutableListOf<Boolean>()
        val invocations = mutableListOf<AssistantInvocationSource>()
        var stopCalled = false
        var hideCalled = false
        var configIssue: AssistantConfigIssue? = null
        var configFailure: Throwable? = null
        override val voiceSessionId = MutableStateFlow<String?>(null)

        override suspend fun configurationIssue(): AssistantConfigIssue? {
            configFailure?.let { throw it }
            return configIssue
        }

        override fun noteInvocation(source: AssistantInvocationSource) {
            invocations += source
        }

        override suspend fun submit(
            text: String,
            source: AssistantInvocationSource,
            confirmationHandler: AssistantConfirmationHandler?,
        ) {
            submissions += text to source
        }

        override fun stop() {
            stopCalled = true
        }

        override fun respondToConfirmation(confirmed: Boolean) {
            confirmationResponses += confirmed
        }

        override fun hideOverlay() {
            hideCalled = true
        }
    }
}
