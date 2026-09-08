package github.ponyhuang.gimi.data.voicewake

import android.content.Context
import github.ponyhuang.gimi.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionState
import github.ponyhuang.gimi.domain.assistant.repository.AssistantConfirmationHandler
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSubmissionResult
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
        coordinator.submitResult = AssistantSubmissionResult.Completed("voice-1", "回答")

        val result = executor.execute("指令") { true }

        assertEquals("voice-1", result.sessionId)
        assertEquals("回答", result.responseText)
        assertEquals(AssistantInvocationSource.BLUETOOTH_WAKE, coordinator.submissions.single().first)
    }

    @Test
    fun `voice confirmation handler is forwarded to coordinator handler`() = runTest {
        coordinator.submitResult = AssistantSubmissionResult.Completed("voice-1", "完成")

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
        coordinator.submitResult = AssistantSubmissionResult.Failed("网络失败")

        executor.execute("指令") { true }
    }

    @Test
    fun `busy current conversation surfaces localized error`() = runTest {
        coordinator.submitResult = AssistantSubmissionResult.Busy("voice-1")

        val error = runCatching { executor.execute("指令") { true } }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("错误", error?.message)
    }

    @Test
    fun `stopped coordinator task surfaces a typed cancellation`() = runTest {
        coordinator.submitResult = AssistantSubmissionResult.Stopped

        val error = runCatching { executor.execute("指令") { true } }.exceptionOrNull()

        assertTrue(error is VoiceAgentTaskStoppedException)
    }

    private class FakeCoordinator(
        override val state: MutableStateFlow<AssistantSessionState>,
    ) : AssistantSessionCoordinator {
        val submissions = mutableListOf<Pair<AssistantInvocationSource, AssistantConfirmationHandler?>>()
        var submitResult: AssistantSubmissionResult = AssistantSubmissionResult.Completed("voice-1", "")

        override fun noteInvocation(source: AssistantInvocationSource) = Unit

        override suspend fun submit(
            text: String,
            source: AssistantInvocationSource,
            confirmationHandler: AssistantConfirmationHandler?,
        ): AssistantSubmissionResult {
            submissions += source to confirmationHandler
            return submitResult
        }

        override fun stop() = Unit

        override fun respondToConfirmation(
            confirmationCallId: String,
            confirmed: Boolean,
        ): Boolean = true

        override fun updatePresentation(event: github.ponyhuang.gimi.domain.assistant.model.AssistantPresentationEvent) = Unit

        override fun hidePresentation() = Unit
    }
}
