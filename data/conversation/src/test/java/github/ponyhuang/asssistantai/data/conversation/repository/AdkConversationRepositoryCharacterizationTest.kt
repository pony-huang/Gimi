package github.ponyhuang.asssistantai.data.conversation.repository

import android.content.Context
import app.cash.turbine.test
import com.google.adk.kt.sessions.SessionService
import github.ponyhuang.asssistantai.data.conversation.local.ConversationMetadataDao
import github.ponyhuang.asssistantai.data.conversation.local.ConversationMetadataEntity
import github.ponyhuang.asssistantai.data.conversation.local.ConversationToolConfigurationCodec
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.conversation.model.ToolAccessMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AdkConversationRepositoryCharacterizationTest {

    private val sessionService = mockk<SessionService>()
    private val metadataDao = mockk<ConversationMetadataDao>(relaxed = true)
    private val context = mockk<Context> {
        every { getString(any()) } returns "新对话"
    }

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

    @Test
    fun refresh_propagatesCancellation() = runTest {
        coEvery {
            sessionService.listSessions(appName = any(), userId = any())
        } throws CancellationException("cancelled")

        try {
            repository().refresh()
            throw AssertionError("CancellationException was swallowed")
        } catch (_: CancellationException) {
            // Expected.
        }
    }

    @Test
    fun conversationToolConfiguration_readsPersistedSnapshot() = runTest {
        val expected = ConversationToolConfiguration(
            enabledLocalToolIds = setOf("clock"),
            enabledMcpServerIds = setOf("mcp-1"),
            toolAccessMode = ToolAccessMode.ON_DEMAND,
        )
        coEvery { metadataDao.get("session-1") } returns ConversationMetadataEntity(
            sessionId = "session-1",
            toolConfigurationJson = ConversationToolConfigurationCodec.encode(expected),
        )

        assertEquals(expected, repository().conversationToolConfiguration("session-1"))
    }

    @Test
    fun setConversationToolConfiguration_persistsSnapshotWithoutReplacingOtherMetadata() = runTest {
        val configuration = ConversationToolConfiguration(
            enabledLocalToolIds = setOf("clock", "location"),
        )

        repository().setConversationToolConfiguration("session-1", configuration)

        coVerify {
            metadataDao.setToolConfiguration(
                "session-1",
                match { payload ->
                    ConversationToolConfigurationCodec.decode(payload) == configuration
                },
            )
        }
    }

    private fun repository() = AdkConversationRepository(
        appName = "test-app",
        userId = "test-user",
        sessionService = sessionService,
        metadataDao = metadataDao,
        context = context,
    )
}
