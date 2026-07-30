package github.ponyhuang.gimi.voice

import android.content.Context
import github.ponyhuang.gimi.domain.assistant.model.AssistantConfigIssue
import github.ponyhuang.gimi.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionPhase
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionState
import github.ponyhuang.gimi.domain.assistant.model.AssistantTurn
import github.ponyhuang.gimi.domain.assistant.repository.AssistantConfirmationHandler
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSessionCoordinator
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAgentTaskExecutorTest {

    private val context: Context = mockk {
        every { getString(any()) } returns "错误"
        every { getString(any(), *anyVararg()) } returns "错误"
    }
    private val coordinatorState = MutableStateFlow(AssistantSessionState())
    private val coordinator = FakeCoordinator(coordinatorState)
    private val executor = VoiceAgentTaskExecutor(context, coordinator)

    @Test
    fun `execute submits through coordinator with bluetooth source and maps result`() = runTest {
        coordinator.onSubmit = {
            coordinatorState.value = AssistantSessionState(
                sessionId = "voice-1",
                phase = AssistantSessionPhase.FOLLOW_UP_IDLE,
                turn = AssistantTurn(userText = "指令", responseText = "回答"),
            )
        }

        val result = executor.execute("指令") { true }

        assertEquals("voice-1", result.sessionId)
        assertEquals("回答", result.responseText)
        assertEquals(AssistantInvocationSource.BLUETOOTH_WAKE, coordinator.submissions.single().first)
    }

    @Test
    fun `voice confirmation handler is forwarded to coordinator handler`() = runTest {
        coordinator.onSubmit = {
            coordinatorState.value = AssistantSessionState(
                sessionId = "voice-1",
                phase = AssistantSessionPhase.FOLLOW_UP_IDLE,
                turn = AssistantTurn(responseText = "完成"),
            )
        }

        executor.execute("指令") { request ->
            assertEquals("call-1", request.callId)
            assertEquals("camera_open", request.toolName)
            false
        }

        val handler = coordinator.submissions.single().second
        assertTrue(handler != null)
    }

    @Test(expected = IllegalStateException::class)
    fun `coordinator error surfaces as exception`() = runTest {
        coordinator.onSubmit = {
            coordinatorState.value = AssistantSessionState(
                phase = AssistantSessionPhase.ERROR,
                errorMessage = "网络失败",
            )
        }

        executor.execute("指令") { true }
    }

    private class FakeCoordinator(
        override val state: MutableStateFlow<AssistantSessionState>,
    ) : AssistantSessionCoordinator {
        val submissions = mutableListOf<Pair<AssistantInvocationSource, AssistantConfirmationHandler?>>()
        var onSubmit: () -> Unit = {}
        override val voiceSessionId = MutableStateFlow<String?>(null)

        override suspend fun configurationIssue(): AssistantConfigIssue? = null

        override fun noteInvocation(source: AssistantInvocationSource) = Unit

        override suspend fun submit(
            text: String,
            source: AssistantInvocationSource,
            confirmationHandler: AssistantConfirmationHandler?,
        ) {
            submissions += source to confirmationHandler
            onSubmit()
        }

        override fun stop() = Unit

        override fun respondToConfirmation(
            confirmationCallId: String,
            confirmed: Boolean,
        ): Boolean = true

        override fun hideOverlay() = Unit
    }
}
