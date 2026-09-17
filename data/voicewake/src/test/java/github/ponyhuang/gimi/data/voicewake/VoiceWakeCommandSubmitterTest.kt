package github.ponyhuang.gimi.data.voicewake

import github.ponyhuang.gimi.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.gimi.domain.assistant.model.AssistantPresentationEvent
import github.ponyhuang.gimi.domain.assistant.model.AssistantSessionState
import github.ponyhuang.gimi.domain.assistant.repository.AssistantConfirmationHandler
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSubmissionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceWakeCommandSubmitterTest {
    @Test
    fun submitsTextToSharedCurrentSessionWithoutAudioOrPrivateConfirmationHandler() = runTest {
        val coordinator = FakeCoordinator()

        VoiceWakeCommandSubmitter(coordinator).submit("打开地图")

        assertEquals("打开地图", coordinator.text)
        assertEquals(AssistantInvocationSource.BLUETOOTH_WAKE, coordinator.source)
        assertNull(coordinator.confirmationHandler)
    }

    private class FakeCoordinator : AssistantSessionCoordinator {
        override val state = MutableStateFlow(AssistantSessionState())
        var text: String? = null
        var source: AssistantInvocationSource? = null
        var confirmationHandler: AssistantConfirmationHandler? = null

        override fun noteInvocation(source: AssistantInvocationSource) = Unit

        override fun updatePresentation(event: AssistantPresentationEvent) = Unit

        override suspend fun submit(
            text: String,
            source: AssistantInvocationSource,
            confirmationHandler: AssistantConfirmationHandler?,
        ): AssistantSubmissionResult {
            this.text = text
            this.source = source
            this.confirmationHandler = confirmationHandler
            return AssistantSubmissionResult.Completed("session", "")
        }

        override fun stop() = Unit

        override fun respondToConfirmation(
            confirmationCallId: String,
            confirmed: Boolean,
        ): Boolean = false

        override fun hidePresentation() = Unit
    }
}
