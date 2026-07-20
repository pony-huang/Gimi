package github.ponyhuang.asssistantai.data.conversation.repository

import app.cash.turbine.test
import com.google.adk.kt.sessions.SessionService
import github.ponyhuang.asssistantai.data.conversation.local.ConversationMetadataDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AdkConversationRepositoryCharacterizationTest {

    private val sessionService = mockk<SessionService>()
    private val metadataDao = mockk<ConversationMetadataDao>(relaxed = true)

    @Test
    fun contentUpdate_ignoresBlankId_andPublishesCompletedSessionId() = runTest {
        val repository = repository()

        repository.conversationContentUpdates.test {
            repository.notifyConversationContentChanged("")
            expectNoEvents()

            repository.notifyConversationContentChanged("session-1")
            assertEquals("session-1", awaitItem())
        }
    }

    @Test
    fun createConversation_returnsBlank_whenSessionStorageFails() = runTest {
        coEvery { sessionService.createSession(any()) } throws IllegalStateException("storage unavailable")

        assertEquals("", repository().createConversation())
    }

    private fun repository() = AdkConversationRepository(
        appName = "test-app",
        userId = "test-user",
        sessionService = sessionService,
        metadataDao = metadataDao,
    )
}
